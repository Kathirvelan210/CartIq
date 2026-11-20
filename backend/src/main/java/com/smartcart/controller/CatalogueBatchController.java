package com.smartcart.controller;

import com.smartcart.catalogimport.CatalogueBatchService;
import com.smartcart.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/catalogue-imports/batches")
public class CatalogueBatchController {
    private final CatalogueBatchService service;

    public CatalogueBatchController(CatalogueBatchService service) {
        this.service=service;
    }

    public record Snapshot(@NotBlank @Pattern(regexp="[a-f0-9]{64}") String sha256,
                           @Min(1) @Max(100000) int rows, @Size(max=1000) String sourceUrl) {}
    public record Chunk(@Min(0) int offset,
                        @NotNull @Size(min=1,max=20) List<@NotNull @Size(max=262144) String> records) {}

    @PostMapping
    public Map<String,Object> start(@Valid @RequestBody Snapshot snapshot,@AuthenticationPrincipal Jwt jwt) {
        return service.start(snapshot.sha256(),snapshot.rows(),jwt.getSubject(),snapshot.sourceUrl());
    }
    @GetMapping
    public List<Map<String,Object>> list(@RequestParam(defaultValue="0") int offset) {
        if(offset<0)throw ApiException.bad("Offset must be nonnegative.");
        return service.list(offset);
    }
    @GetMapping("/{id}")
    public Map<String,Object> get(@PathVariable String id) { return service.get(id); }
    @GetMapping("/{id}/issues")
    public List<Map<String,Object>> issues(@PathVariable String id,@RequestParam(defaultValue="0") int offset) {
        if(offset<0)throw ApiException.bad("Offset must be nonnegative.");
        return service.issues(id,offset);
    }
    @PutMapping("/{id}/rows")
    public Map<String,Object> append(@PathVariable String id,@Valid @RequestBody Chunk chunk,@AuthenticationPrincipal Jwt jwt) {
        if(chunk.records().stream().mapToLong(String::length).sum()>1048576)throw ApiException.bad("Chunk exceeds the import size limit.");
        return service.append(id,chunk.offset(),chunk.records(),jwt.getSubject());
    }
}
