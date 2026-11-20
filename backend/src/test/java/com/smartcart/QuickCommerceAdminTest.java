package com.smartcart;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:quickcommerceadmin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "QUICKCOMMERCE_API_KEY=", "QUICKCOMMERCE_VERIFIED_START_BALANCE=0"
})
@ActiveProfiles({"local", "test"})
@AutoConfigureMockMvc
class QuickCommerceAdminTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.core.env.Environment environment;

    @Test void requiresAdminAndFailsClosedWithoutConfiguration() throws Exception {
        org.junit.jupiter.api.Assertions.assertNull(environment.getProperty("QUICKCOMMERCE_ENABLED"));
        var request = get("/api/admin/providers/quickcommerce/search")
            .param("retailer", "blinkit").param("q", "milk").param("lat", "12.9").param("lon", "77.66");
        mvc.perform(request).andExpect(status().isUnauthorized());
        mvc.perform(request.with(jwt())).andExpect(status().isForbidden());
        mvc.perform(request.with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isServiceUnavailable());
    }

    @Test void comparisonIsAdminOnlyAndLegacySyncCannotDispatch() throws Exception {
        var request = post("/api/admin/providers/quickcommerce/compare").contentType("application/json")
            .content("{\"q\":\"milk\",\"lat\":12.9,\"lon\":77.66}");
        var admin = jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"));
        mvc.perform(request).andExpect(status().isUnauthorized());
        mvc.perform(request.with(jwt())).andExpect(status().isForbidden());
        mvc.perform(request.with(admin)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.retailers[0].status").value("error"))
            .andExpect(jsonPath("$.retailers[1].status").value("not_attempted"));
        mvc.perform(post("/api/admin/provider/sync").param("locationId", "fixture").with(admin))
            .andExpect(status().isServiceUnavailable());
    }

    @Test void shoppingAccessIsDisabledByDefaultEvenForASignedInUser() throws Exception {
        mvc.perform(post("/api/shopping/retailers/compare").contentType("application/json")
            .content("{\"q\":\"milk\",\"lat\":12.9,\"lon\":77.66}").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test void accountBalanceIsAdminOnlyAndDisabledByDefault() throws Exception {
        var route = get("/api/admin/providers/quickcommerce/credits");
        mvc.perform(route).andExpect(status().isUnauthorized());
        mvc.perform(route.with(jwt())).andExpect(status().isForbidden());
        mvc.perform(route.with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isServiceUnavailable());
    }
}
