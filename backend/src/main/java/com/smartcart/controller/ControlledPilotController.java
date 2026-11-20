package com.smartcart.controller;

import com.smartcart.service.ControlledPilot;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @Profile("local")
@RequestMapping("/api/admin/retailer-pilot")
public class ControlledPilotController {
    record Enabled(boolean enabled) {}
    record DiscoveryAuthorization(@jakarta.validation.constraints.NotNull @Valid ControlledPilot.Authorization approval,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=150) String query,
        boolean discoveryDisplayApproved) {}
    private final ControlledPilot pilot;
    public ControlledPilotController(ControlledPilot pilot){this.pilot=pilot;}
    @GetMapping public ResponseEntity<?> list(){return result(pilot.overview());}
    @GetMapping("/candidates") public ResponseEntity<?> candidates(@RequestParam String area,@RequestParam String retailer){
        return result(pilot.candidates(area,retailer));
    }
    @GetMapping("/{id}") public ResponseEntity<?> report(@PathVariable UUID id){return result(pilot.report(id.toString()));}
    @PostMapping public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ControlledPilot.Setup input){
        return result(Map.of("id",pilot.create(jwt.getSubject(),input)));
    }
    @PutMapping("/{id}/enabled") public ResponseEntity<?> enable(@PathVariable UUID id,@RequestBody Enabled input){
        pilot.enable(id.toString(),input.enabled());return result(Map.of("enabled",input.enabled()));
    }
    @PostMapping("/{id}/authorize") public ResponseEntity<?> issue(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
         @Valid @RequestBody ControlledPilot.Authorization input){
        return result(Map.of("id",pilot.authorize(jwt.getSubject(),id.toString(),input)));
    }
    @PostMapping("/{id}/authorize-discovery") public ResponseEntity<?> issueDiscovery(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
         @Valid @RequestBody DiscoveryAuthorization input){
        return result(Map.of("id",pilot.authorizeDiscovery(jwt.getSubject(),id.toString(),input.approval(),input.query(),input.discoveryDisplayApproved())));
    }
    private static <T> ResponseEntity<T> result(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
