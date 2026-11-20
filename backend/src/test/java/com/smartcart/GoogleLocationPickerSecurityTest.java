package com.smartcart;

import com.smartcart.controller.ShoppingLocationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "CARTIQ_GEOCODER_PROVIDER=google-demo",
    "CARTIQ_GOOGLE_GEOCODING_API_KEY=fixture-google-key"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
class GoogleLocationPickerSecurityTest {
    @Autowired MockMvc mvc;

    @Test void shopperLookupRequiresSignInAndConsentWithoutGrantingAdminAccess() throws Exception {
        var shopper = jwt().authorities(new SimpleGrantedAuthority("SCOPE_customer"));
        var path = "/api/location-lookup/pin";
        var body = "{\"postcode\":\"000000\"}"; // Deliberately invalid to keep every branch offline.
        mvc.perform(get("/api/location-lookup/options")).andExpect(status().isOk())
            .andExpect(jsonPath("$.googleDemo").value(true));
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(shopper).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(shopper).contentType(MediaType.APPLICATION_JSON).content(body)
            .header(ShoppingLocationController.CONSENT_HEADER, "delivery-geocoding-v1")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/geocoder-demo/pin").with(shopper).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
    }
}
