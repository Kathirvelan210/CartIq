package com.smartcart.controller;

import com.smartcart.catalogimport.ProductMatching;
import com.smartcart.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/catalogue-matching")
public class CatalogueMatchingController {
    public record Comparison(@NotNull @Valid ProductMatching.Identity left,
                             @NotNull @Valid ProductMatching.Identity right) {}
    public record ReviewPair(@NotBlank @Size(max = 100) String reference,
                             @NotNull @Valid Comparison comparison) {}
    public record ReviewRequest(@NotNull @Size(min = 1, max = 50) List<@NotNull @Valid ReviewPair> pairs) {}
    public record ReviewResult(String reference, ProductMatching.Result evidence) {}

    @PostMapping("/compare")
    public ProductMatching.Result compare(@Valid @RequestBody Comparison input,
            @RequestParam(defaultValue = "baseline") String method) {
        return switch (method) {
            case "baseline" -> ProductMatching.compare(input.left(), input.right());
            case "hybrid" -> ProductMatching.compareHybrid(input.left(), input.right());
            default -> throw ApiException.bad("Choose the baseline or hybrid method.");
        };
    }

    @PostMapping("/review")
    public List<ReviewResult> review(@Valid @RequestBody ReviewRequest input,
            @RequestParam(defaultValue = "baseline") String method) {
        return input.pairs().stream()
            .map(pair -> new ReviewResult(pair.reference(), compare(pair.comparison(), method)))
            .toList();
    }
}
