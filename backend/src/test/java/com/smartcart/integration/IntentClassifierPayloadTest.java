package com.smartcart.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IntentClassifierPayloadTest {
    @Test void omitsCandidateCountFromTheSerializedFlashRequest() {
        var mapper = new ObjectMapper();
        var classifier = new IntentClassifier("fixture", "gemini-3.8-flash", mapper);
        var builder = RestClient.builder().baseUrl("https://example.invalid");
        var server = MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(classifier, "client", builder.build());

        server.expect(requestTo("https://example.invalid/v1beta/models/gemini-3.8-flash:generateContent"))
            .andExpect(jsonPath("$.generationConfig.candidateCount").doesNotHaveJsonPath())
            .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(256))
            .andRespond(withSuccess("""
                {"candidates":[{"content":{"parts":[{"text":"{\\"type\\":\\"BASKET\\",\\"query\\":\\"rice\\"}"}]}}]}
                """, MediaType.APPLICATION_JSON));

        var intent = classifier.classify("fixture-shopper", "Plan dinner");
        assertEquals("BASKET", intent.type());
        assertEquals("rice", intent.query());
        server.verify();
    }
}
