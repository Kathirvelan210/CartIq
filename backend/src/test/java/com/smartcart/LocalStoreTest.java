package com.smartcart;

import com.smartcart.config.ReferenceData;
import com.smartcart.config.StarterCatalogue;
import com.smartcart.repository.Db;
import com.smartcart.service.LocalStoreService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

class LocalStoreTest {
    @Test void storesAndListingsRemainScopedToStoreAndPostcode() throws Exception {
        var source=new DriverManagerDataSource("jdbc:h2:mem:localstores;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        var db=new Db(new JdbcTemplate(source));
        new ReferenceData(db,false).run(new DefaultApplicationArguments());
        new StarterCatalogue(db,new ObjectMapper()).run(new DefaultApplicationArguments());
        var stores=new LocalStoreService(db);
        var id=stores.create(new LocalStoreService.Store("Test shop","Test address","560038",12.97,77.64,true,false,null,true));
        stores.saveListing(id,new LocalStoreService.Listing("milk",7500L,8000L,4));
        assertEquals(1,stores.nearby("560038").size());
        assertEquals(0,stores.nearby("560034").size());
        assertEquals(1,stores.nearby(12.97,77.64,1).size());
        assertEquals(0,stores.nearby(13.5,77.64,1).size());
        assertEquals("Test shop",stores.details(id).get("name"));
        assertEquals(4,((Number)stores.listings(id).get(0).get("stock_quantity")).intValue());
        stores.saveListing(id,new LocalStoreService.Listing("milk",7600L,8000L,2));
        assertEquals(1,stores.listings(id).size());
        assertEquals(2,((Number)stores.listings(id).get(0).get("stock_quantity")).intValue());
        assertThrows(RuntimeException.class,()->stores.saveListing(id,new LocalStoreService.Listing("milk",1L,null,-1)));
    }
}
