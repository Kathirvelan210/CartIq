package com.smartcart.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.smartcart.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.*;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import java.net.URI;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean SecretKeySpec signingKey(@Value("${smartcart.jwt-secret}") String configured,@Value("${smartcart.sample-data}") boolean sample){
        byte[] key;
        if(configured.isBlank()){if(!sample)throw new IllegalStateException("Set JWT_SECRET (at least 32 bytes) before disabling sample mode.");key=new byte[48];new SecureRandom().nextBytes(key);}
        else key=configured.getBytes(StandardCharsets.UTF_8);
        if(key.length<32)throw new IllegalStateException("JWT_SECRET needs at least 32 bytes.");
        return new SecretKeySpec(key,"HmacSHA256");
    }
    @Bean JwtEncoder encoder(SecretKeySpec key){return new NimbusJwtEncoder(new ImmutableSecret<>(key));}
    @Bean JwtDecoder decoder(SecretKeySpec key,UserRepository users){
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> version=jwt->{
            String subject=jwt.getSubject();
            if(subject!=null&&!subject.isBlank()&&jwt.getExpiresAt()!=null
                &&users.findById(subject).filter(u->Objects.equals(jwt.getClaim("version"),Long.valueOf(u.sessionVersion))).isPresent()){
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Session expired",null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("smartcart-ai"),version));return decoder;
    }
    @Bean PasswordEncoder passwords(){return new BCryptPasswordEncoder(12);}
    @Bean SecurityFilterChain security(HttpSecurity http,CorsConfigurationSource cors,
        @Value("${QUICKCOMMERCE_SHOPPING_ENABLED:false}") boolean shoppingEnabled,
        @Value("${CARTIQ_GEOCODER_PROVIDER:nominatim}") String geocoderProvider)throws Exception {
        AccessDeniedHandler forbidden=(request,response,error)->{
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"You do not have access to this resource.\"}");
        };
        return http.cors(c->c.configurationSource(cors)).csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a->{
                a.requestMatchers("/api/location-lookup/options").permitAll();
                if ("google-demo".equals(geocoderProvider)) a.requestMatchers("/api/location-lookup/**").authenticated();
                else a.requestMatchers("/api/location-lookup/**").permitAll();
                a.requestMatchers("/","/api/auth/**","/api/products/**","/api/local-stores/**","/api/locations","/api/categories","/api/platforms","/api/meta","/api/cart/optimize","/api/recipes","/api/recipes/*","/api/training/sample","/api/docs/**","/api/swagger-ui/**","/swagger-ui/**","/api/openapi/**","/actuator/health","/actuator/health/liveness","/actuator/health/readiness","/error").permitAll();
                if (shoppingEnabled) a.requestMatchers("/api/shopping/retailers/**").authenticated();
                else a.requestMatchers("/api/shopping/retailers/**").denyAll();
                a.requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin").anyRequest().authenticated();
            })
            .exceptionHandling(e->e.accessDeniedHandler(forbidden))
            .oauth2ResourceServer(o->o.jwt(j->{}).authenticationEntryPoint((r,s,e)->{
                s.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                s.setHeader("WWW-Authenticate","Bearer");
                s.setContentType("application/json");
                s.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Please sign in again.\"}");
            }))
            .headers(h->h.contentTypeOptions(c->{}).frameOptions(f->f.deny())).build();
    }
    @Bean CorsConfigurationSource cors(@Value("${smartcart.allowed-origins}") String origins,Environment environment){
        CorsConfiguration c=new CorsConfiguration();
        List<String> allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        if(environment.acceptsProfiles(Profiles.of("prod"))&&(allowed.isEmpty()||allowed.stream().anyMatch(origin->!isHttpsOrigin(origin))))
            throw new IllegalStateException("ALLOWED_ORIGINS must contain explicit HTTPS origins in production.");
        c.setAllowedOrigins(allowed);
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Content-Type","Authorization","Idempotency-Key",com.smartcart.service.LiveOffers.POSITION_HEADER,
            com.smartcart.controller.ShoppingLocationController.CONSENT_HEADER));
        c.setExposedHeaders(List.of("Retry-After","X-Request-Id"));
        c.setAllowCredentials(true);
        c.validateAllowCredentials();
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",c);
        return source;
    }

    private static boolean isHttpsOrigin(String origin){
        try{
            URI uri=URI.create(origin);
            return "https".equals(uri.getScheme())&&uri.getHost()!=null&&uri.getRawUserInfo()==null
                && uri.getRawPath().isEmpty()&&uri.getRawQuery()==null&&uri.getRawFragment()==null;
        }catch(IllegalArgumentException invalid){return false;}
    }
}
