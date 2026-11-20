package com.smartcart.service;

import com.smartcart.dto.Models.*;
import com.smartcart.entity.UserEntity;
import com.smartcart.repository.*;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class AuthService {
    private final UserRepository users;private final Db db;private final PasswordEncoder passwords;private final JwtEncoder encoder;
    public record Issued(Session session,String refresh) {}
    public AuthService(UserRepository users,Db db,PasswordEncoder passwords,JwtEncoder encoder){this.users=users;this.db=db;this.passwords=passwords;this.encoder=encoder;}
    public User user(String id){return dto(users.findById(id).orElseThrow(ApiException::missing));}
    public static User dto(UserEntity u){return new User(u.id,u.name,u.email,u.role,u.sampleUser);}
    public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    @Transactional public Issued register(Register r){return create(r.name().trim(),r.email().trim().toLowerCase(Locale.ROOT),r.password(),r.phone(),false);}
    @Transactional public Issued create(String name,String email,String password,String phone,boolean sample){
        if(passwordTooLong(password))throw ApiException.bad("Use a password of at most 72 UTF-8 bytes.");
        if(users.findByEmail(email).isPresent())throw new ApiException(HttpStatus.CONFLICT,"This email is already registered.");
        UserEntity u=new UserEntity();u.id=id();u.name=name;u.email=email;u.passwordHash=passwords.encode(password);u.sampleUser=sample;users.saveAndFlush(u);
        db.insert("user_profiles",fields("user_id",u.id,"phone",phone));db.insert("user_preferences",fields("user_id",u.id));return issue(u);
    }
    @Transactional public Issued login(Login r){
        UserEntity u=users.findByEmail(r.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        if(u==null||passwordTooLong(r.password())||!passwords.matches(r.password(),u.passwordHash)){
            throw new ApiException(HttpStatus.UNAUTHORIZED,"Email or password is incorrect.");
        }
        return issue(u);
    }
    private Issued issue(UserEntity u){Instant now=Instant.now();JwtClaimsSet claims=JwtClaimsSet.builder().issuer("smartcart-ai").subject(u.id).issuedAt(now).expiresAt(now.plusSeconds(1800)).id(id()).claim("scope",u.role).claim("version",u.sessionVersion).build();String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);String refresh=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);db.insert("refresh_tokens",fields("token_hash",hash(refresh),"user_id",u.id,"expires_at",now.plus(Duration.ofDays(30))));return new Issued(new Session(token,dto(u)),refresh);}
    @Transactional public Issued refresh(String token){if(token==null)throw new ApiException(HttpStatus.UNAUTHORIZED,"Please sign in.");var row=db.maybe("SELECT * FROM refresh_tokens WHERE token_hash=? FOR UPDATE",hash(token)).orElseThrow(()->new ApiException(HttpStatus.UNAUTHORIZED,"Please sign in."));if(!time(row,"expires_at").isAfter(Instant.now()))throw new ApiException(HttpStatus.UNAUTHORIZED,"Please sign in again.");db.run("DELETE FROM refresh_tokens WHERE token_hash=?",hash(token));return issue(users.findById(text(row,"user_id")).orElseThrow(ApiException::missing));}
    @Transactional public void logout(String userId,String refresh,String deviceToken){String owner=userId;if(owner==null&&refresh!=null)owner=db.maybe("SELECT user_id FROM refresh_tokens WHERE token_hash=?",hash(refresh)).map(r->text(r,"user_id")).orElse(null);if(owner!=null){UserEntity u=users.findById(owner).orElseThrow(ApiException::missing);u.sessionVersion++;u.updatedAt=Instant.now();users.save(u);db.run("DELETE FROM refresh_tokens WHERE user_id=?",owner);if(deviceToken!=null)db.run("DELETE FROM device_tokens WHERE user_id=? AND token_hash=?",owner,hash(deviceToken));}}
    @Transactional public User rename(String id,Name input){UserEntity u=users.findById(id).orElseThrow(ApiException::missing);u.name=input.name().trim();u.updatedAt=Instant.now();return dto(users.save(u));}
    @Transactional public void password(String id,Password r){UserEntity u=users.findById(id).orElseThrow(ApiException::missing);if(passwordTooLong(r.currentPassword())||!passwords.matches(r.currentPassword(),u.passwordHash))throw ApiException.bad("Current password is incorrect.");if(passwordTooLong(r.newPassword()))throw ApiException.bad("Use at most 72 UTF-8 bytes.");u.passwordHash=passwords.encode(r.newPassword());u.sessionVersion++;u.updatedAt=Instant.now();users.save(u);db.run("DELETE FROM refresh_tokens WHERE user_id=?",id);}
    private boolean passwordTooLong(String password){return password.getBytes(StandardCharsets.UTF_8).length>72;}
}
