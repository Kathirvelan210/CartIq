package com.smartcart;

@org.springframework.boot.test.context.SpringBootTest(classes=LocalStoreHttpAuditTest.Config.class,properties={
    "spring.datasource.url=jdbc:h2:mem:storeaudit;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "smartcart.sample-data=false","smartcart.import-starter-catalogue=true",
    "smartcart.jwt-secret=local-verification-secret-at-least-32-characters"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles({"test","local-store-audit"})
class LocalStoreHttpAuditTest extends LocalStoreAuditContract {
    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.Profile("local-store-audit")
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.boot.autoconfigure.domain.EntityScan("com.smartcart.entity")
    @org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.smartcart.repository")
    @org.springframework.context.annotation.Import({
        com.smartcart.repository.Db.class,com.smartcart.service.LocalStoreService.class,
        com.smartcart.controller.LocalStoreController.class,com.smartcart.security.SecurityConfig.class,
        com.smartcart.controller.OnlineComparisonEvidenceController.class,
        com.smartcart.exception.ApiErrors.class,com.smartcart.config.ReferenceData.class,
        com.smartcart.config.StarterCatalogue.class})
    static class Config {}
}
