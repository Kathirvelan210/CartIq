package com.smartcart.integration;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
@Service
public class ModelService {
    private final RestClient client;private final ObjectMapper json;private final String secret;private final boolean sample;
    public ModelService(@Value("${smartcart.ai-url}") String url,@Value("${smartcart.ai-secret}") String secret,@Value("${smartcart.sample-data}") boolean sample,ObjectMapper json){this.secret=secret;this.sample=sample;this.json=json;var f=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(500)).build());f.setReadTimeout(Duration.ofSeconds(2));client=RestClient.builder().baseUrl(url).requestFactory(f).build();}
    public JsonNode status(){try{return json.readTree(client.get().uri("/models/status").header("X-Service-Token",secret).retrieve().body(String.class));}catch(Exception e){return json.createObjectNode().put("trained",false).put("status","Model service unavailable");}}
    public Map<String,Double> recipeRanks(List<String> pantry){Map<String,Double> scores=new HashMap<>();if(pantry.isEmpty())return scores;try{JsonNode results=json.readTree(client.post().uri("/recipes/recommend").header("X-Service-Token",secret).body(Map.of("ingredients",pantry,"sample",sample)).retrieve().body(String.class));if(results.isArray())for(var result:results)if(result.path("score").isNumber())scores.put(result.path("recipeId").asText(),result.path("score").asDouble());}catch(Exception ignored){}return scores;}
    public JsonNode match(String query){if(query==null||query.isBlank()||query.length()>150)throw com.smartcart.exception.ApiException.bad("Enter an ingredient of 1–150 characters.");try{return json.readTree(client.post().uri("/ingredients/match").header("X-Service-Token",secret).body(Map.of("query",query,"sample",sample)).retrieve().body(String.class));}catch(Exception e){return json.createArrayNode();}}
}
