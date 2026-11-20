package com.smartcart.catalogimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Component
public class OpenFoodFactsAdapter {
 private final ObjectMapper json;private final boolean enabled;private final String agent;
 public OpenFoodFactsAdapter(ObjectMapper json,@Value("${smartcart.catalogue-import.off-enabled:false}") boolean enabled,@Value("${smartcart.catalogue-import.user-agent:}") String agent){this.json=json;this.enabled=enabled;this.agent=agent;}
 public JsonNode fetch(String code) throws Exception {
  ImportNormalizer.gtin(code);
  if(!enabled||agent.isBlank())throw new IllegalStateException("Open Food Facts imports require explicit enablement and an identifying User-Agent after licence review.");
  var request=HttpRequest.newBuilder(URI.create("https://world.openfoodfacts.org/api/v3/product/"+code+".json")).timeout(Duration.ofSeconds(20)).header("User-Agent",agent).GET().build();
  var response=send(request);
  try(var body=response.body()){
   if(response.statusCode()==429||response.statusCode()>=500)throw new TemporaryFailure("Temporary source HTTP "+response.statusCode());
   if(response.statusCode()!=200)throw new IllegalStateException("Open Food Facts HTTP "+response.statusCode()+"; retry later.");
   byte[] bytes=body.readNBytes(2000001);if(bytes.length>2000000)throw new IllegalStateException("Source response exceeds import size limit.");
   JsonNode product=json.readTree(bytes).path("product");
   if(!product.isObject())throw new IllegalStateException("Product not found in source response.");
   if(!ImportNormalizer.gtin(product.path("code").asText()).equals(ImportNormalizer.gtin(code)))throw new IllegalStateException("Source barcode mismatch.");
   return product;
  }
 }
 public static class TemporaryFailure extends IllegalStateException { public TemporaryFailure(String message){super(message);} }
 // Transport seam for deterministic tests; production host and request policy remain fixed.
 protected HttpResponse<java.io.InputStream> send(HttpRequest request) throws Exception {
  return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build().send(request,HttpResponse.BodyHandlers.ofInputStream());
 }
}
