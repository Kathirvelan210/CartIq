package com.smartcart.controller;

import com.smartcart.dto.Models.*;
import com.smartcart.service.*;
import com.smartcart.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api")
public class AuthController {
    private final AuthService auth;private final DemoService demo;private final boolean sample,secure;
    public AuthController(AuthService auth,DemoService demo,@Value("${smartcart.sample-data}") boolean sample,@Value("${smartcart.secure-cookies}") boolean secure){this.auth=auth;this.demo=demo;this.sample=sample;this.secure=secure;}
    private Session session(AuthService.Issued issued,HttpServletResponse response){response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("cartiq_refresh",issued.refresh()).httpOnly(true).secure(secure).sameSite("Strict").path("/api/auth").maxAge(30*86400).build().toString());return issued.session();}
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED) public Session register(@Valid @RequestBody Register r,HttpServletResponse s){return session(auth.register(r),s);}
    @PostMapping("/auth/login") public Session login(@Valid @RequestBody Login r,HttpServletResponse s){return session(auth.login(r),s);}
    @PostMapping("/auth/demo") public Session demo(HttpServletResponse s){if(!sample)throw ApiException.missing();var issued=auth.create("Demo shopper","demo-"+UUID.randomUUID()+"@sample.invalid",UUID.randomUUID()+"-Sample!",null,true);demo.populateUser(issued.session().user().id());return session(issued,s);}
    @PostMapping("/auth/refresh") public Session refresh(@CookieValue(name="cartiq_refresh",required=false) String cookie,HttpServletResponse s){return session(auth.refresh(cookie),s);}
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT) public void logout(@AuthenticationPrincipal Jwt jwt,@CookieValue(name="cartiq_refresh",required=false) String cookie,@Valid @RequestBody(required=false) DeviceInput device,HttpServletResponse s){auth.logout(jwt==null?null:jwt.getSubject(),cookie,device==null?null:device.token());s.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from("cartiq_refresh","").httpOnly(true).secure(secure).sameSite("Strict").path("/api/auth").maxAge(0).build().toString());}
    @GetMapping("/me") public User me(@AuthenticationPrincipal Jwt jwt){return auth.user(jwt.getSubject());}
    @PatchMapping("/me") public User rename(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Name r){return auth.rename(jwt.getSubject(),r);}
    @PostMapping("/me/password") @ResponseStatus(HttpStatus.NO_CONTENT) public void password(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Password r){auth.password(jwt.getSubject(),r);}
}
