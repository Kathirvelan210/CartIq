package com.smartcart.recipe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.exception.ApiException;
import com.smartcart.service.RecipeService;
import jakarta.validation.Validator;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

@Service
public class RecipeLibrary {
    public record Curated(String id,String source,RecipeCard recipe) {}
    public record Entry(String id,String kind,String source,RecipeCard recipe) {}
    private record Draft(String owner,Entry entry,Instant expiresAt) {}
    private final Map<String,Entry> curated;
    private final Map<String,Draft> drafts=new LinkedHashMap<>();
    private final Map<String,Instant> cooldown=new HashMap<>();
    private final RecipeService pantry;
    private final Validator validator;
    private final RestClient client;
    private final boolean enabled;
    private final String secret;

    public RecipeLibrary(ObjectMapper json,Validator validator,RecipeService pantry,
        @Value("${smartcart.ai-url}") String url,@Value("${smartcart.ai-secret}") String secret,
        @Value("${CARTIQ_RECIPE_GENERATION_ENABLED:false}") boolean enabled) throws IOException {
        this.pantry=pantry; this.validator=validator; this.secret=secret; this.enabled=enabled;
        var transport=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        // Allow the private service to finish a 75-second provider read and return its classified outcome.
        transport.setReadTimeout(Duration.ofSeconds(100));
        client=RestClient.builder().baseUrl(url).requestFactory(transport).requestInterceptor((request,body,execution)->{
            String id=org.slf4j.MDC.get("requestId");
            if(id!=null) request.getHeaders().set("X-Request-Id",id);
            return execution.execute(request,body);
        }).build();
        Map<String,Entry> entries=new LinkedHashMap<>();
        try (var resource=getClass().getResourceAsStream("/recipes/curated.json")) {
            for (Curated row:json.readValue(resource,new TypeReference<List<Curated>>() {})) {
                validate(row.recipe());
                entries.put(row.id(),new Entry(row.id(),"CURATED",row.source(),row.recipe()));
            }
        }
        curated=Collections.unmodifiableMap(entries);
    }

    public List<Entry> browse(String q,String cuisine,RecipeCard.Meal meal,RecipeCard.Diet diet,RecipeCard.Difficulty difficulty,Integer minutes) {
        String terms=ProductMatching.normalized(q),region=ProductMatching.normalized(cuisine);
        return curated.values().stream().filter(entry->{
            var r=entry.recipe();
            String search=ProductMatching.normalized(r.name()+" "+r.description()+" "+r.cuisine()+" "+
                String.join(" ",r.ingredients().stream().map(RecipeCard.Part::name).toList()));
            return Arrays.stream(terms.split(" ")).allMatch(search::contains) && ProductMatching.normalized(r.cuisine()).contains(region)
                && (meal==null||r.meal()==meal) && (diet==null||r.diet()==diet) && (difficulty==null||r.difficulty()==difficulty)
                && (minutes==null||r.prepMinutes()+r.cookMinutes()<=minutes);
        }).toList();
    }

    synchronized Entry find(String owner,String id) {
        if (curated.containsKey(id)) return curated.get(id);
        Draft draft=drafts.get(id);
        if (draft==null || !draft.owner().equals(owner) || !draft.expiresAt().isAfter(Instant.now())) throw ApiException.missing();
        return draft.entry();
    }

    public record GenerationReceipt(Entry entry,HttpHeaders evidence) {}

    public GenerationReceipt generate(String owner,String prompt,boolean usePantry) {
        if (!enabled || secret.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Recipe generation is not configured.");
        reserve(owner);
        var input=usePantry ? pantry.pantry(owner).stream().map(p->p.name()).filter(n->n.length()<=100).limit(30).toList() : List.<String>of();
        RecipeCard recipe;
        HttpHeaders evidence;
        try {
            var generated=client.post().uri("/recipes/generate").header("X-Service-Token",secret)
                .body(Map.of("prompt",prompt.strip(),"pantry",input)).retrieve().toEntity(RecipeCard.class);
            recipe=generated.getBody();
            validate(recipe);
            evidence=GeminiFailure.safeEvidence(generated.getHeaders());
        } catch (RestClientResponseException failure) {
            throw GeminiFailure.from(failure,"Recipe generation is temporarily unavailable.");
        } catch (ResourceAccessException failure) {
            throw GeminiFailure.transport(failure);
        } catch (RestClientException | ApiException failure) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,"The recipe service could not return a valid draft.");
        }
        var entry=new Entry(UUID.randomUUID().toString(),"GENERATED",null,recipe);
        synchronized(this) {
            var now=Instant.now();
            drafts.values().removeIf(d->!d.expiresAt().isAfter(now));
            if (drafts.size()>=128) drafts.remove(drafts.keySet().iterator().next());
            drafts.put(entry.id(),new Draft(owner,entry,now.plusSeconds(1800)));
        }
        return new GenerationReceipt(entry,evidence);
    }

    public record Route(String intent) {}
    public com.smartcart.service.UnifiedShoppingAssistant.Intent route(String owner,String prompt){
        if(!enabled||secret.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Gemini is unavailable. Choose Ask CartIQ for a local answer.");
        reserve(owner);
        try{
            var result=client.post().uri("/shopping/intent").header("X-Service-Token",secret)
                .body(Map.of("prompt",prompt)).retrieve().body(Route.class);
            if(result==null||result.intent()==null)throw new IllegalArgumentException();
            return com.smartcart.service.UnifiedShoppingAssistant.Intent.valueOf(result.intent());
        }catch(RestClientResponseException failure){
            throw GeminiFailure.from(failure,"Gemini routing is unavailable. Choose Ask CartIQ for a local answer.");
        }catch(ResourceAccessException failure){
            throw GeminiFailure.transport(failure);
        }catch(IllegalArgumentException|RestClientException failure){
            throw new ApiException(HttpStatus.BAD_GATEWAY,"The assistant could not validate the Gemini response.");
        }
    }

    public record WeekChoices(List<String> recipeIds) {}
    public List<Entry> week(String owner,String prompt,boolean usePantry,RecipeCard.Diet diet){
        if(!enabled||secret.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Gemini meal planning is unavailable.");
        var choices=new LinkedHashMap<String,String>();
        curated.forEach((id,entry)->{if(diet==null||entry.recipe().diet()==diet)choices.put(id,entry.recipe().name());});
        if(choices.isEmpty())throw ApiException.bad("No curated recipes match this dietary selection.");
        reserve(owner);
        var names=usePantry?pantry.pantry(owner).stream().map(p->p.name()).filter(n->n.length()<=100).limit(30).toList():List.of();
        try{
            var week=client.post().uri("/recipes/week").header("X-Service-Token",secret)
                .body(Map.of("prompt",prompt,"pantry",names,"choices",choices)).retrieve().body(WeekChoices.class);
            if(week==null||week.recipeIds()==null||week.recipeIds().size()!=7||!choices.keySet().containsAll(week.recipeIds()))
                throw new ApiException(HttpStatus.BAD_GATEWAY,"The suggested meals could not be verified.");
            return week.recipeIds().stream().map(curated::get).toList();
        }catch(RestClientResponseException failure){
            throw GeminiFailure.from(failure,"The meal planner is temporarily unavailable.");
        }catch(ResourceAccessException failure){
            throw GeminiFailure.transport(failure);
        }catch(RestClientException failure){throw new ApiException(HttpStatus.BAD_GATEWAY,"The meal planner could not return a verified week.");}
    }

    private synchronized void reserve(String owner) {
        Instant now=Instant.now();
        cooldown.values().removeIf(end->!end.isAfter(now));
        if (cooldown.containsKey(owner) || cooldown.size()>=128) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Wait before generating another recipe.");
        cooldown.put(owner,now.plusSeconds(30));
    }

    private void validate(RecipeCard recipe) {
        if (recipe==null || !validator.validate(recipe).isEmpty()) throw ApiException.bad("The recipe does not match its required structure.");
        recipe.ingredients().forEach(part->RecipeCard.scale(part,recipe.servings(),recipe.servings()));
    }
}
