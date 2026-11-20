package com.smartcart.service;
import com.smartcart.integration.EmailSender;
import com.smartcart.repository.*;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.security.SecureRandom;
import java.util.Locale;
import static com.smartcart.repository.Db.*;
@Service
public class EmailChangeService {
    private final Db db;private final UserRepository users;private final EmailSender sender;
    public EmailChangeService(Db db,UserRepository users,EmailSender sender){this.db=db;this.users=users;this.sender=sender;}
    @Transactional public void request(String user,String email){email=email.trim().toLowerCase(Locale.ROOT);if(users.findByEmail(email).isPresent())throw new ApiException(HttpStatus.CONFLICT,"This email cannot be used.");var previous=db.maybe("SELECT created_at FROM email_change_requests WHERE user_id=?",user);if(previous.isPresent()&&time(previous.get(),"created_at").isAfter(Instant.now().minusSeconds(60)))throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"Please wait a minute before requesting another code.");String code=String.valueOf(10000000+new SecureRandom().nextInt(90000000));db.run("DELETE FROM email_change_requests WHERE user_id=?",user);db.insert("email_change_requests",fields("user_id",user,"new_email",email,"code_hash",AuthService.hash(code),"expires_at",Instant.now().plusSeconds(600)));sender.verification(email,code);}
    @Transactional(noRollbackFor=ApiException.class) public void confirm(String user,String code){var pending=db.maybe("SELECT * FROM email_change_requests WHERE user_id=? FOR UPDATE",user).orElseThrow(()->ApiException.bad("Request a new email verification code first."));if(num(pending,"attempts")>=5||!time(pending,"expires_at").isAfter(Instant.now()))throw ApiException.bad("The verification code expired or too many attempts were made. Request a new code.");if(!java.security.MessageDigest.isEqual(text(pending,"code_hash").getBytes(java.nio.charset.StandardCharsets.UTF_8),AuthService.hash(code).getBytes(java.nio.charset.StandardCharsets.UTF_8))){db.run("UPDATE email_change_requests SET attempts=attempts+1,updated_at=? WHERE user_id=?",Instant.now(),user);throw ApiException.bad("Verification code is incorrect.");}String email=text(pending,"new_email");if(users.findByEmail(email).isPresent())throw new ApiException(HttpStatus.CONFLICT,"This email can no longer be used. Request another change.");var account=users.findById(user).orElseThrow(ApiException::missing);account.email=email;account.sessionVersion++;account.updatedAt=Instant.now();users.saveAndFlush(account);db.run("DELETE FROM email_change_requests WHERE user_id=?",user);db.run("DELETE FROM refresh_tokens WHERE user_id=?",user);}
}
