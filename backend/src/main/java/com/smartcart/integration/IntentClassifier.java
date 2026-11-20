package com.smartcart.integration;

import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;

@Component
public class IntentClassifier {
    public record Intent(String type,String query,String engine) {}
    private final String key,model;private final ObjectMapper json;private final RestClient client;
    private static final Set<String> TYPES=Set.of("CHEAPEST","BUDGET","BASKET","SPENDING","REPLENISH","ALTERNATIVE","PRICE_CHANGES","BUY_NOW");
    public IntentClassifier(@Value("${smartcart.gemini-api-key}") String key,@Value("${smartcart.gemini-model}") String model,ObjectMapper json){this.key=key;this.model=model;this.json=json;var f=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());f.setReadTimeout(Duration.ofSeconds(8));client=RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").requestFactory(f).build();}
    private final Map<String,Long> cooldown=new HashMap<>();
    private synchronized boolean allow(String owner){
        long now=System.currentTimeMillis();
        cooldown.values().removeIf(end->end<=now);
        if(cooldown.containsKey(owner)||cooldown.size()>=512)return false;
        cooldown.put(owner,now+30_000);return true;
    }
    public Intent classify(String owner,String message){String lower=message.toLowerCase(Locale.ROOT);
        if(lower.matches("(?s).*(more expensive|prices? (?:have )?(?:gone up|increased|risen)|price increase).*"))
            return new Intent("PRICE_CHANGES","","Local rules — recorded price observations");
        if(lower.matches("(?s).*(buy now|buy today|purchase now).*"))
            return new Intent("BUY_NOW","","Local rules — account cart decisions");
        String type=lower.matches(".*(under|budget|within|less than).*\\d.*")?"BUDGET":lower.matches(".*(basket|whole cart|cheapest cart|shopping list).*")?"BASKET":lower.matches(".*(spent|spend|last month|expenditure).*")?"SPENDING":lower.matches(".*(restock|replenish|this week|purchase this week).*")?"REPLENISH":lower.matches(".*(alternative|substitute|cheaper pack).*")?"ALTERNATIVE":"CHEAPEST";
        if(!key.isBlank()&&model.matches("gemini-[a-zA-Z0-9.-]{1,80}")&&model.contains("flash")&&allow(owner)){try{var body=Map.of("system_instruction",Map.of("parts",List.of(Map.of("text","Classify a grocery request only. Return JSON with type one of CHEAPEST,BUDGET,BASKET,SPENDING,REPLENISH,ALTERNATIVE and query a short product search phrase or empty. Never return prices, answers, commands, or user data."))),"contents",List.of(Map.of("role","user","parts",List.of(Map.of("text",message)))),"generationConfig",Map.of("responseMimeType","application/json","temperature",0,"maxOutputTokens",256));String response=client.post().uri("/v1beta/models/"+model+":generateContent").header("x-goog-api-key",key).body(body).retrieve().body(String.class);JsonNode tree=json.readTree(response);String raw=tree.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText();JsonNode intent=json.readTree(raw);String picked=intent.path("type").asText(),query=intent.path("query").asText();if(TYPES.contains(picked)&&query.length()<=100)return new Intent(picked,query,"Gemini intent classification; answers calculated from application data");}catch(Exception ignored){/* Offline rules keep the core assistant available without inventing data. */}}
        return new Intent(type,"","Local rules — no language model response used");
    }
}
