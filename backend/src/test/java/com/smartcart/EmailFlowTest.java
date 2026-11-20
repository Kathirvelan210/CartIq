package com.smartcart;
import com.smartcart.integration.EmailSender;
import com.smartcart.service.*;
import com.smartcart.repository.*;
import com.smartcart.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @ActiveProfiles("test") @Import(EmailFlowTest.Configuration.class) @Transactional
@TestExecutionListeners(listeners={org.springframework.test.context.support.DependencyInjectionTestExecutionListener.class,org.springframework.test.context.transaction.TransactionalTestExecutionListener.class},mergeMode=TestExecutionListeners.MergeMode.REPLACE_DEFAULTS)
class EmailFlowTest {
    static class CapturingSender implements EmailSender {String code;public void verification(String email,String code){this.code=code;}}
    @TestConfiguration static class Configuration {@Bean @Primary CapturingSender fakeMail(){return new CapturingSender();}}
    @Autowired AuthService auth;@Autowired EmailChangeService emails;@Autowired CapturingSender sender;@Autowired UserRepository users;@Autowired Db db;
    String account(){return auth.create("Test shopper",UUID.randomUUID()+"@example.test","A-long-test-password!",null,false).session().user().id();}
    @Test void emailChangesOnlyAfterCorrectCodeAndRevokesSessions(){String user=account();String old=users.findById(user).orElseThrow().email;emails.request(user,"verified@example.test");assertEquals(old,users.findById(user).orElseThrow().email);emails.confirm(user,sender.code);assertEquals("verified@example.test",users.findById(user).orElseThrow().email);assertEquals(1,users.findById(user).orElseThrow().sessionVersion);assertEquals(0,db.count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id=?",user));}
    @Test void codeAttemptsAreLimitedAndCannotBeUsedByAnotherAccount(){String user=account();emails.request(user,"another@example.test");assertThrows(ApiException.class,()->emails.confirm(account(),sender.code));String correct=sender.code;for(int i=0;i<5;i++)assertThrows(ApiException.class,()->emails.confirm(user,"00000000"));assertEquals(5,db.count("SELECT attempts FROM email_change_requests WHERE user_id=?",user));assertThrows(ApiException.class,()->emails.confirm(user,correct));}
}
