package com.smartcart.config;

import org.flywaydb.core.Flyway;

/** Operator-run migration entry point. The application runtime only validates. */
public final class DatabaseMigration {
    private DatabaseMigration() {}

    public static void main(String[] args) {
        if (args.length != 1 || !("validate".equals(args[0]) || "migrate".equals(args[0]))) {
            throw new IllegalArgumentException("Choose exactly one migration operation: validate or migrate.");
        }
        String url=required("DB_URL");
        if(!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException("DB_URL must be a MySQL JDBC URL.");
        var flyway=Flyway.configure().dataSource(url,required("DB_USERNAME"),required("DB_PASSWORD"))
            .cleanDisabled(true).baselineOnMigrate(false).load();
        if("migrate".equals(args[0]))flyway.migrate();
        flyway.validate();
        if(flyway.info().pending().length!=0)throw new IllegalStateException("Database has unapplied CartIQ migrations.");
    }

    private static String required(String name){
        String value=System.getenv(name);
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required.");
        return value;
    }
}
