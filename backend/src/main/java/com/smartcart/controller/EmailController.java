package com.smartcart.controller;
import com.smartcart.service.EmailChangeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
@RestController @RequestMapping("/api/me/email-change")
public class EmailController {
    private final EmailChangeService emails;
    public EmailController(EmailChangeService emails){this.emails=emails;}
    public record Request(@NotBlank @Email @Size(max=254) String email) {}
    public record Verify(@NotBlank @Pattern(regexp="[0-9]{8}") String code) {}
    @PostMapping @ResponseStatus(HttpStatus.NO_CONTENT) public void request(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Request input){emails.request(jwt.getSubject(),input.email());}
    @PostMapping("/confirm") @ResponseStatus(HttpStatus.NO_CONTENT) public void confirm(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Verify input){emails.confirm(jwt.getSubject(),input.code());}
}
