package com.smartcart.config;
import com.smartcart.repository.Db;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static com.smartcart.repository.Db.*;
@Component @Order(-100)
public class ReferenceData implements ApplicationRunner {
    private final Db db;private final boolean sample;
    public ReferenceData(Db db,@Value("${smartcart.sample-data}") boolean sample){this.db=db;this.sample=sample;}
    @Override @Transactional public void run(ApplicationArguments args){if(sample)return;for(var p:new String[][]{{"blinkit","Blinkit"},{"instamart","Instamart"},{"zepto","Zepto"}})if(db.count("SELECT COUNT(*) FROM grocery_platforms WHERE id=?",p[0])==0)db.insert("grocery_platforms",fields("id",p[0],"name",p[1],"fee_source","UNKNOWN"));for(var c:new String[][]{{"dairy","Dairy & eggs"},{"produce","Fruits & vegetables"},{"pantry","Pantry"},{"snacks","Snacks"},{"beverages","Beverages"},{"personal","Personal care"},{"household","Household"}})if(db.count("SELECT COUNT(*) FROM product_categories WHERE id=?",c[0])==0)db.insert("product_categories",fields("id",c[0],"name",c[1]));}
}
