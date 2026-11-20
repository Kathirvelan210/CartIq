package com.smartcart.integration;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
@Component
public class ResendEmailSender implements EmailSender {
    private final String key,from;private final RestClient client;
    public ResendEmailSender(@Value("${RESEND_API_KEY:}") String key,@Value("${RESEND_FROM:}") String from){this.key=key;this.from=from;var f=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());f.setReadTimeout(Duration.ofSeconds(8));client=RestClient.builder().baseUrl("https://api.resend.com").requestFactory(f).build();}
    @Override public void verification(String destination,String code){if(key.isBlank()||from.isBlank())throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Email verification delivery is not configured yet. Your current email has not changed.");try{client.post().uri("/emails").header("Authorization","Bearer "+key).body(Map.of("from",from,"to",List.of(destination),"subject","Verify your new CartIQ email","text","Your CartIQ verification code is "+code+". It expires in 10 minutes. Enter it in the account screen. If you did not request this change, ignore this email.")).retrieve().toBodilessEntity();}catch(Exception e){throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"The verification email could not be delivered. Your current email has not changed.");}}
}
