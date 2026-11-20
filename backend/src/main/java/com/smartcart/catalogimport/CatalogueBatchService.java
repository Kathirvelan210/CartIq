package com.smartcart.catalogimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.exception.ApiException;
import com.smartcart.repository.Db;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static com.smartcart.repository.Db.*;

@Service
public class CatalogueBatchService {
    public static final String SNAPSHOT_URL =
        "https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/openfoodfacts-products.jsonl.gz";
    private final Db db;
    private final CatalogueImportService imports;
    private final ObjectMapper json;

    public CatalogueBatchService(Db db, CatalogueImportService imports, ObjectMapper json) {
        this.db = db;
        this.imports = imports;
        this.json = json;
    }

    @Transactional
    public Map<String,Object> start(String digest, int rows, String user) {
        return start(digest, rows, user, null);
    }

    @Transactional
    public Map<String,Object> start(String digest, int rows, String user, String sourceUrl) {
        String source = sourceUrl == null ? SNAPSHOT_URL : sourceUrl;
        validateSource(source);
        // Reuse the existing OFF lock to serialize creation and avoid duplicate snapshot jobs.
        db.one("SELECT source FROM catalogue_import_locks WHERE source='OPEN_FOOD_FACTS' FOR UPDATE");
        var batch = db.maybe("SELECT * FROM catalogue_import_batches WHERE snapshot_sha256=?",digest);
        if (batch.isPresent()) {
            if (num(batch.get(),"expected_rows") != rows || !source.equals(text(batch.get(),"snapshot_url")))
                throw ApiException.bad("Snapshot row count or source changed.");
            return batch.get();
        }
        String id = id();
        db.insert("catalogue_import_batches",fields("id",id,"snapshot_sha256",digest,"snapshot_url",source,
            "expected_rows",rows,"requested_by",user));
        return get(id);
    }

    private void validateSource(String source) {
        if (SNAPSHOT_URL.equals(source)) return;
        URI uri;
        try { uri=URI.create(source); }
        catch (IllegalArgumentException error) { throw ApiException.bad("Invalid snapshot source URL."); }
        if (source.length()>1000 || !"https".equals(uri.getScheme()) || uri.getUserInfo()!=null || uri.getPort()!=-1
            || uri.getFragment()!=null || !List.of("in.openfoodfacts.org","world.openfoodfacts.org").contains(uri.getHost()==null?"":uri.getHost())
            || !"/api/v2/search".equals(uri.getPath()) || uri.getQuery()==null) {
            throw ApiException.bad("Use the official Open Food Facts bulk export or search URL.");
        }
    }

    public Map<String,Object> get(String id) {
        return db.one("SELECT * FROM catalogue_import_batches WHERE id=?",id);
    }

    public List<Map<String,Object>> list(int offset) {
        return db.rows("SELECT * FROM catalogue_import_batches ORDER BY created_at DESC,id LIMIT 25 OFFSET ?",offset);
    }

    public List<Map<String,Object>> issues(String id,int offset) {
        get(id);
        return db.rows("SELECT `row_number`,outcome,issue,candidate_id FROM catalogue_batch_rows WHERE batch_id=? AND issue IS NOT NULL ORDER BY `row_number` LIMIT 50 OFFSET ?",id,offset);
    }

    @Transactional
    public Map<String,Object> append(String id,int offset,List<String> records,String user) {
        var batch=db.one("SELECT * FROM catalogue_import_batches WHERE id=? FOR UPDATE",id);
        if (num(batch,"committed_rows") != offset) {
            throw new ApiException(HttpStatus.CONFLICT,"Batch checkpoint changed. Read the current offset before retrying.");
        }
        if ((long)offset+records.size()>num(batch,"expected_rows")) throw ApiException.bad("Chunk exceeds the declared row count.");
        int staged=0, corrections=0, invalid=0;
        Instant now=Instant.now();
        for (String raw : records) {
            offset++;
            String issue=null, candidateId=null;
            JsonNode record=null;
            String code=null;
            try {
                record=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(raw);
                if (record==null||!record.isObject()) throw ApiException.bad("Expected one JSON product object.");
                code=record.path("code").asText();
                ImportNormalizer.gtin(code);
            } catch (JsonProcessingException error) {
                issue="Invalid JSON product record.";
            } catch (ApiException error) {
                issue=error.getMessage();
            }
            String outcome;
            if (issue!=null) {
                invalid++;
                outcome="INVALID";
            } else {
                var candidate=imports.stageSnapshot(code,raw,record,user,now);
                candidateId=candidate.id();
                issue=candidate.issue();
                if (issue==null) { staged++; outcome="PENDING_REVIEW"; }
                else { corrections++; outcome="NEEDS_CORRECTION"; }
            }
            db.insert("catalogue_batch_rows",fields("batch_id",id,"`row_number`",offset,"raw_record",raw,
                "outcome",outcome,"issue",issue,"candidate_id",candidateId));
        }
        db.run("UPDATE catalogue_import_batches SET committed_rows=?,staged_rows=staged_rows+?,correction_rows=correction_rows+?,invalid_rows=invalid_rows+? WHERE id=?",
            offset,staged,corrections,invalid,id);
        return get(id);
    }
}
