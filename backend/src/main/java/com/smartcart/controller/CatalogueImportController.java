package com.smartcart.controller;
import com.smartcart.catalogimport.CatalogueImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
@RestController
@RequestMapping("/api/admin/catalogue-imports")
public class CatalogueImportController {
 private final CatalogueImportService service;
 public CatalogueImportController(CatalogueImportService service){this.service=service;}
 public record Request(@NotBlank @Pattern(regexp="[0-9]{8}|[0-9]{12,14}") String barcode){}
 public record Approval(@NotBlank @Size(max=50) String categoryId,@NotBlank @Size(max=100) String variantName,
                        @Size(max=60) String existingProductId){}
 @PostMapping("/open-food-facts") public Map<String,Object> fetch(@Valid @RequestBody Request request,@AuthenticationPrincipal Jwt jwt){return service.fetch(request.barcode(),jwt.getSubject());}
 @GetMapping public List<Map<String,Object>> jobs(){return service.jobs();}
 @GetMapping("/candidates") public List<Map<String,Object>> candidates(){return service.candidates();}
 @GetMapping("/review") public List<Map<String,Object>> review(@RequestParam(required=false) String batchId,@RequestParam(defaultValue="0") int offset,@RequestParam(defaultValue="20") int limit){return service.review(batchId,offset,limit);}
 @PostMapping("/candidates/{id}/approve") public Map<String,Object> approve(@PathVariable String id,@Valid @RequestBody Approval request,@AuthenticationPrincipal Jwt jwt){return service.approve(id,request.categoryId(),request.variantName(),request.existingProductId(),jwt.getSubject());}
 @PostMapping("/candidates/{id}/publish") public Map<String,Object> publish(@PathVariable String id,@AuthenticationPrincipal Jwt jwt){return service.publish(id,jwt.getSubject());}
 @PostMapping("/candidates/{id}/reject") public void reject(@PathVariable String id,@AuthenticationPrincipal Jwt jwt){service.reject(id,jwt.getSubject());}
 public record Correction(@NotBlank @Size(max=150) String name,@NotBlank @Size(max=100) String brand,@NotBlank @Size(max=60) String quantity,@NotBlank @Size(max=1000) String reason){}
 @PostMapping("/{id}/resume") public Map<String,Object> resume(@PathVariable String id){return service.resume(id);}
 @PutMapping("/candidates/{id}") public void correct(@PathVariable String id,@Valid @RequestBody Correction input,@AuthenticationPrincipal Jwt jwt){service.correct(id,input.name(),input.brand(),input.quantity(),input.reason(),jwt.getSubject());}
}
