package com.smartcart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.OpenFoodFactsAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class OpenFoodFactsAdapterSafetyTest {
 static class Adapter extends OpenFoodFactsAdapter {
  String body;int status=200,calls;Exception failure;HttpRequest request;
  Adapter(String body){super(new ObjectMapper(),true,"CartIQ-test");this.body=body;}
  @Override protected HttpResponse<InputStream> send(HttpRequest request) throws Exception {this.request=request;calls++;if(failure!=null)throw failure;HttpResponse<InputStream> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));return response;}
 }
 @Test void successAndRequestPolicy() throws Exception {var a=new Adapter("{\"product\":{\"code\":\"3017620020117\",\"product_name\":\"Test\"}}");assertEquals("Test",a.fetch("3017620020117").path("product_name").asText());assertEquals("world.openfoodfacts.org",a.request.uri().getHost());assertEquals(20,a.request.timeout().orElseThrow().toSeconds());assertEquals("CartIQ-test",a.request.headers().firstValue("User-Agent").orElseThrow());assertEquals(1,a.calls);}
 @ParameterizedTest @ValueSource(ints={404,429,500,503}) void httpFailuresHaveNoUnboundedRetry(int status){var a=new Adapter("{}");a.status=status;assertThrows(IllegalStateException.class,()->a.fetch("3017620020117"));assertEquals(1,a.calls);}
 @Test void timeoutPropagates(){var a=new Adapter("{}");a.failure=new HttpTimeoutException("timeout");assertThrows(HttpTimeoutException.class,()->a.fetch("3017620020117"));assertEquals(1,a.calls);}
 @ParameterizedTest @ValueSource(strings={"broken","{}","{\"product\":{}}","{\"product\":{\"code\":\"3017620020111\"}}","{\"product\":{\"code\":\"4006381333931\"}}"}) void invalidResponsesRejected(String body){var a=new Adapter(body);assertThrows(Exception.class,()->a.fetch("3017620020117"));}
 @Test void invalidRequestNeverCallsTransport(){var a=new Adapter("{}");assertThrows(RuntimeException.class,()->a.fetch("bad"));assertEquals(0,a.calls);}
 @Test void incompleteButIdentifiedProductIsPassedToStaging() throws Exception {var a=new Adapter("{\"product\":{\"code\":\"3017620020117\"}}");assertFalse(a.fetch("3017620020117").has("product_name"));}
 @Test void oversizeResponseRejected(){var a=new Adapter(" ".repeat(2000001));assertThrows(IllegalStateException.class,()->a.fetch("3017620020117"));}
}
