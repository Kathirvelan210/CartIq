package com.smartcart;

@org.springframework.boot.test.context.SpringBootTest(properties={
    "spring.datasource.url=${CARTIQ_MYSQL_VERIFY_URL:jdbc:mysql://127.0.0.1:3306/cartiq_db?connectionTimeZone=UTC}",
    "spring.datasource.username=${CARTIQ_MYSQL_VERIFY_USERNAME:cartiq_user}","spring.datasource.password=${CARTIQ_DB_PASSWORD}",
    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
    "smartcart.sample-data=false","smartcart.import-starter-catalogue=false",
    "smartcart.jwt-secret=local-verification-secret-at-least-32-characters"})
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="CARTIQ_MYSQL_VERIFY",matches="true")
class MysqlMilestone3Test extends LocalStoreAuditContract {}
