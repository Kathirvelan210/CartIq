package com.smartcart.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("prod")
public class ProductionDatabaseCheck {
    @Bean
    @Order(Integer.MIN_VALUE)
    ApplicationRunner verifyDatabase(DataSource dataSource) {
        return ignored -> {
            var flyway=Flyway.configure().dataSource(dataSource).cleanDisabled(true).load();
            flyway.validate();
            if(flyway.info().pending().length!=0)
                throw new IllegalStateException("Database has unapplied CartIQ migrations.");
        };
    }
}
