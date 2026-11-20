package com.smartcart.config;
import com.smartcart.repository.UserRepository;
import com.smartcart.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
@Component @Order(100)
public class AdminBootstrap implements ApplicationRunner {
    private final UserRepository users;private final AuthService auth;private final String email,password;
    public AdminBootstrap(UserRepository users,AuthService auth,@Value("${BOOTSTRAP_ADMIN_EMAIL:}") String email,@Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password){this.users=users;this.auth=auth;this.email=email;this.password=password;}
    @Override public void run(ApplicationArguments args){if(email.isBlank()||password.isBlank()||users.findByEmail(email.toLowerCase(java.util.Locale.ROOT)).isPresent())return;if(password.length()<12)throw new IllegalStateException("Use a bootstrap admin password of at least 12 characters.");var issued=auth.create("Administrator",email.toLowerCase(java.util.Locale.ROOT),password,null,false);var user=users.findById(issued.session().user().id()).orElseThrow();user.role="admin";users.save(user);}
}
