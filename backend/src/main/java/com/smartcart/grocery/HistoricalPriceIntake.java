package com.smartcart.grocery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import com.smartcart.service.CatalogService;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import static com.smartcart.repository.Db.*;

@Service
@Profile("!mysql-local")
public class HistoricalPriceIntake {
    public record Authorization(@NotBlank @Size(max=150) String sourceIdentity,@NotBlank @Size(max=300) String permissionDocument,
        @NotBlank @Pattern(regexp="[a-f0-9]{64}") String permissionSha256,@NotBlank @Size(max=600) String scope,
        @NotBlank @Size(max=600) String revocationConditions,@AssertTrue boolean collectionAllowed,@AssertTrue boolean retentionAllowed,
        boolean analyticsAllowed,boolean trainingAllowed,boolean serveAllowed,boolean displayAllowed,boolean rawEvidenceAllowed,
        boolean historicalAsOfAllowed) {}
    public record Decision(int row,String disposition,String reason) {}
    public record Report(String batchId,String sha256,boolean committed,List<Decision> rows) {}
    private record Candidate(PriceObservations.Input input, Instant sourceAvailable, String sourceEvidence) {}

    private final Db db;
    private final PriceObservations observations;
    private final CatalogService catalogue;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final TransactionTemplate transactions;

    public HistoricalPriceIntake(Db db,PriceObservations observations,CatalogService catalogue,ObjectMapper mapper,
        Validator validator,PlatformTransactionManager manager) {
        this.db=db;this.observations=observations;this.catalogue=catalogue;this.mapper=mapper;this.validator=validator;
        transactions=new TransactionTemplate(manager);
    }

    @Transactional
    public void authorize(String reviewer,String grantId,Authorization input) {
        if (catalogue.sample()) throw unavailable("Historical intake is unavailable in a sample catalogue.");
        var grant=db.one("SELECT * FROM price_retention_grants WHERE id=? FOR UPDATE",grantId);
        active(grant);
        boolean display=bool(grant,"history_allowed");
        if (input.displayAllowed()!=display || (display||bool(grant,"dataset_allowed"))&&!input.analyticsAllowed()
            || input.trainingAllowed()&&(!input.analyticsAllowed()||!bool(grant,"dataset_allowed"))
            || input.serveAllowed()&&(!input.trainingAllowed()||!display)) throw ApiException.bad("Check the requested uses against the base retention grant.");
        var forecast=db.maybe("SELECT * FROM price_forecast_permissions WHERE grant_id=?",grantId);
        if (forecast.isPresent()&&forecast.get().get("revoked_at")==null &&
            (!input.trainingAllowed() || bool(forecast.get(),"serve_allowed")&&!input.serveAllowed())) {
            throw ApiException.bad("Reconcile the existing forecast approval before registering conflicting use rights.");
        }
        db.insert("price_source_authorizations",fields("grant_id",grantId,"source_identity",input.sourceIdentity(),"document_reference",input.permissionDocument(),
            "document_sha256",input.permissionSha256(),"approval_scope",input.scope(),"revocation_conditions",input.revocationConditions(),
            "analytics_allowed",input.analyticsAllowed(),"training_allowed",input.trainingAllowed(),"serve_allowed",input.serveAllowed(),
            "display_allowed",input.displayAllowed(),"raw_evidence_allowed",input.rawEvidenceAllowed(),"historical_as_of_allowed",input.historicalAsOfAllowed(),
            "approved_by",reviewer,"approved_at",Instant.now()));
    }

    @Transactional
    public void revoke(String grantId) {
        db.one("SELECT grant_id FROM price_source_authorizations WHERE grant_id=? FOR UPDATE",grantId);
        db.run("UPDATE price_source_authorizations SET revoked_at=? WHERE grant_id=? AND revoked_at IS NULL",Instant.now(),grantId);
        observations.revoke(grantId);
    }

    public List<Map<String,Object>> registry() {
        return db.rows("""
            SELECT a.*,g.provider,g.listing_id,g.location_id,g.first_observation_at,g.last_observation_at,g.retain_until,
              g.revoked_at AS retention_revoked_at,p.platform_id AS retailer,p.variant_id,
              CASE WHEN a.revoked_at IS NOT NULL OR g.revoked_at IS NOT NULL THEN 'REVOKED'
                   WHEN g.retain_until<=? THEN 'EXPIRED' ELSE 'ACTIVE' END AS permission_status
            FROM price_source_authorizations a JOIN price_retention_grants g ON g.id=a.grant_id
            JOIN platform_products p ON p.id=g.listing_id ORDER BY a.approved_at DESC,a.grant_id LIMIT 200
            """,Instant.now());
    }

    public Report upload(String operator,String grantId,byte[] file,String format,boolean commit,String evidence,boolean reviewed) {
        if (catalogue.sample()) throw unavailable("Historical intake is unavailable in a sample catalogue.");
        var grant=scope(grantId,false);
        if (evidence!=null&&(evidence.isBlank()||evidence.length()>500||!bool(grant,"raw_evidence_allowed"))) {
            throw ApiException.bad("The grant does not permit storing this original-evidence reference.");
        }
        if (commit&&!reviewed) throw ApiException.bad("Confirm the original source evidence and any historical availability timestamp before committing.");
        var input=IntakeFile.parse(mapper,file,format);
        var digest=IntakeFile.sha256(file);
        String batch=commit?transactions.execute(status->begin(operator,grantId,format,input.size(),digest,evidence)):null;
        List<Decision> decisions=new ArrayList<>();
        var keys=new HashMap<String,Candidate>();
        var times=new HashMap<Instant,Candidate>();
        for(int i=0;i<input.size();i++) {
            int number=i+1;
            if (commit) {
                var prior=db.maybe("SELECT disposition,reason_code FROM price_intake_decisions WHERE batch_id=? AND input_index=?",batch,number);
                if(prior.isPresent()) {
                    decisions.add(new Decision(number,text(prior.get(),"disposition"),text(prior.get(),"reason_code")));continue;
                }
            }
            var row=input.get(i);
            Decision decision;
            if (row.path("dataClass").asText().equals("SIMULATED")) decision=new Decision(number,"SIMULATED","SIMULATED_EXCLUDED");
            else {
                Candidate candidate=candidate(grantId,grant,row);
                if (candidate==null) decision=new Decision(number,"QUARANTINED","INVALID_FIELDS_OR_SCOPE");
                else if (candidate.sourceAvailable()!=null&&!bool(grant,"historical_as_of_allowed")) {
                    decision=new Decision(number,"PERMISSION_INELIGIBLE","HISTORICAL_AVAILABILITY_NOT_APPROVED");
                } else if (!commit&&(keys.containsKey(candidate.input().sourceRecordId())||times.containsKey(candidate.input().observedAt()))) {
                    boolean same=candidate.equals(keys.get(candidate.input().sourceRecordId()));
                    decision=new Decision(number,same?"DUPLICATE":"QUARANTINED",same?"REPEATED_KEY_IN_FILE":"CONFLICTING_KEY_IN_FILE");
                } else {
                    decision=check(batch,number,grantId,candidate,commit);
                    if(!commit&&Set.of("VALID","DUPLICATE").contains(decision.disposition())) {
                        keys.put(candidate.input().sourceRecordId(),candidate);times.put(candidate.input().observedAt(),candidate);
                    }
                }
            }
            if (commit) {
                Decision result=decision;
                decision=transactions.execute(status->saveDecision(batch,result));
            }
            decisions.add(decision);
        }
        return new Report(batch,digest,commit,List.copyOf(decisions));
    }

    public List<Map<String,Object>> batches() {
        return db.rows("""
            SELECT b.id,b.grant_id,b.payload_sha256,b.input_format,b.row_count,
              CASE WHEN a.revoked_at IS NULL AND g.revoked_at IS NULL AND g.retain_until>?
                   THEN b.original_evidence_reference ELSE NULL END AS original_evidence_reference,b.submitted_at,
              COUNT(d.input_index) AS decided FROM price_intake_batches b LEFT JOIN price_intake_decisions d ON d.batch_id=b.id
              JOIN price_retention_grants g ON g.id=b.grant_id JOIN price_source_authorizations a ON a.grant_id=b.grant_id
              GROUP BY b.id,b.grant_id,b.payload_sha256,b.input_format,b.row_count,b.original_evidence_reference,b.submitted_at,
                a.revoked_at,g.revoked_at,g.retain_until
              ORDER BY b.submitted_at DESC,b.id LIMIT 100
            """,Instant.now());
    }

    public List<Decision> decisions(String batch) {
        db.one("SELECT id FROM price_intake_batches WHERE id=?",batch);
        return db.rows("SELECT input_index,disposition,reason_code FROM price_intake_decisions WHERE batch_id=? ORDER BY input_index",batch)
            .stream().map(row->new Decision((int)num(row,"input_index"),text(row,"disposition"),text(row,"reason_code"))).toList();
    }

    private Decision check(String batch,int row,String grantId,Candidate candidate,boolean commit) {
        try {
            return transactions.execute(status->{
                if(commit) scope(grantId,true);
                var result=observations.evaluate(candidate.input(),candidate.sourceAvailable(),candidate.sourceEvidence(),commit);
                var decision=new Decision(row,result.created()?(commit?"IMPORTED":"VALID"):"DUPLICATE",result.created()?"VALIDATED":"EXISTING_RECORD");
                return commit?saveDecision(batch,decision):decision;
            });
        } catch (ApiException invalid) {
            return unavailableStatus(invalid)?new Decision(row,"PERMISSION_INELIGIBLE","INACTIVE_GRANT"):
                new Decision(row,"QUARANTINED","OBSERVATION_CONFLICT_OR_INVALID");
        }
    }

    private String begin(String user,String grantId,String format,int rows,String hash,String evidence) {
        scope(grantId,true);
        var prior=db.maybe("SELECT id,input_format,original_evidence_reference FROM price_intake_batches WHERE grant_id=? AND payload_sha256=?",grantId,hash);
        if(prior.isPresent()) {
            if(!Objects.equals(evidence,text(prior.get(),"original_evidence_reference"))||!format.equals(text(prior.get(),"input_format"))) {
                throw ApiException.bad("Replay the batch with the original format and evidence reference.");
            }
            return text(prior.get(),"id");
        }
        String batch=id();
        db.insert("price_intake_batches",fields("id",batch,"grant_id",grantId,"payload_sha256",hash,"input_format",format,"row_count",rows,
            "original_evidence_reference",evidence,"submitted_by",user,"submitted_at",Instant.now()));
        return batch;
    }

    private Decision saveDecision(String batch,Decision decision) {
        db.one("SELECT id FROM price_intake_batches WHERE id=? FOR UPDATE",batch);
        var prior=db.maybe("SELECT disposition,reason_code FROM price_intake_decisions WHERE batch_id=? AND input_index=?",batch,decision.row());
        if(prior.isPresent()) return new Decision(decision.row(),text(prior.get(),"disposition"),text(prior.get(),"reason_code"));
        db.insert("price_intake_decisions",fields("batch_id",batch,"input_index",decision.row(),"disposition",decision.disposition(),
            "reason_code",decision.reason(),"decided_at",Instant.now()));
        return decision;
    }

    private Map<String,Object> scope(String grantId,boolean lock) {
        var policy=db.maybe("SELECT * FROM price_source_authorizations WHERE grant_id=?"+(lock?" FOR UPDATE":""),grantId)
            .orElseThrow(()->unavailable("No verified source authorization is registered for this grant."));
        if(policy.get("revoked_at")!=null) throw unavailable("Source authorization is revoked.");
        var grant=db.one("""
            SELECT g.*,p.variant_id,p.platform_id FROM price_retention_grants g JOIN platform_products p ON p.id=g.listing_id WHERE g.id=?
            """,grantId);
        active(grant);
        var scope=new HashMap<>(grant); scope.put("historical_as_of_allowed",policy.get("historical_as_of_allowed"));
        scope.put("raw_evidence_allowed",policy.get("raw_evidence_allowed"));return scope;
    }
    private void active(Map<String,Object> grant) {
        if(grant.get("revoked_at")!=null||!time(grant,"retain_until").isAfter(Instant.now())) throw unavailable("The retention window has expired or been revoked.");
    }

    private Candidate candidate(String grant,Map<String,Object> scope,JsonNode row) {
        if(!IntakeFile.rowShape(row)||!"GENUINE".equals(field(row,"dataClass"))||row.path("priceMinor").longValue()<=0
            ||!Objects.equals(field(row,"variantId"),text(scope,"variant_id"))||!Objects.equals(field(row,"retailer"),text(scope,"platform_id"))
            ||!Objects.equals(field(row,"locationId"),text(scope,"location_id"))) return null;
        try {
            var at=Instant.parse(field(row,"observedAt"));
            var available=field(row,"sourceAvailableAt");
            var reference=field(row,"sourceAsOfEvidence");
            if(!at.equals(at.truncatedTo(ChronoUnit.SECONDS))||(available==null)!=(reference==null)
               ||reference!=null&&(reference.isBlank()||reference.length()>500))return null;
            var input=new PriceObservations.Input(grant,field(row,"sourceRecordId"),field(row,"sourceEvidence"),at,field(row,"retailerSku"),field(row,"pack"),
                field(row,"currency"),row.get("priceMinor").longValue(),row.hasNonNull("mrpMinor")?row.get("mrpMinor").longValue():null,
                row.hasNonNull("discountPercent")?row.get("discountPercent").decimalValue():null,PriceObservations.Stock.valueOf(field(row,"stock")));
            if(!validator.validate(input).isEmpty())return null;
            return new Candidate(input,available==null?null:Instant.parse(available),reference);
        } catch(IllegalArgumentException | java.time.DateTimeException invalid) {return null;}
    }
    private static String field(JsonNode row,String name){return row.path(name).textValue();}
    private static ApiException unavailable(String reason){return new ApiException(HttpStatus.FORBIDDEN,reason);}
    private static boolean unavailableStatus(ApiException invalid){return invalid.status==HttpStatus.FORBIDDEN;}
}
