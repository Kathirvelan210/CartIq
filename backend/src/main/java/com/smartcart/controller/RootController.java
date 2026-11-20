package com.smartcart.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.Map;

@RestController
public class RootController {
    @GetMapping(value = "/", headers = "Accept=text/html")
    public ResponseEntity<Void> rootHtml() {
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create("http://localhost:8092"));
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }

    @GetMapping(value = "/")
    public Map<String, Object> rootJson() {
        return Map.of(
            "name", "CartIQ Backend API",
            "status", "UP",
            "docs", "/api/docs",
            "health", "/actuator/health",
            "frontend", "http://localhost:8092"
        );
    }
}
