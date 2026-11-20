package com.smartcart.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.repository.Db;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import static com.smartcart.repository.Db.fields;

@Component
@Order(0)
@ConditionalOnProperty(name = "smartcart.import-starter-catalogue", havingValue = "true")
public class StarterCatalogue implements ApplicationRunner {
    private final Db db;
    private final ObjectMapper json;

    public StarterCatalogue(Db db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    record Pack(String id, String productId, String name, String brand, String categoryId,
                String description, String color, String art, String quantity, int amountBase,
                String baseUnit, String imageUrl, String retailerUrl, String retailerSku) {}

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments arguments) throws Exception {
        List<Pack> packs;
        try (var stream = new ClassPathResource("catalogue/products.json").getInputStream()) {
            packs = json.readValue(stream, new TypeReference<List<Pack>>() {});
        }
        if (missing("delivery_locations", "blr-indiranagar")) {
            db.insert("delivery_locations", fields("id", "blr-indiranagar", "name", "Indiranagar",
                "city", "Bengaluru", "postcode", "560038"));
        }
        for (var pack : packs) {
            if (missing("products", pack.productId())) {
                db.insert("products", fields("id", pack.productId(), "name", pack.name(),
                    "brand", pack.brand(), "category_id", pack.categoryId(), "description", pack.description(),
                    "color", pack.color(), "art", pack.art(), "variant_name", "Standard", "image_url", pack.imageUrl()));
            }
            if (missing("product_variants", pack.id())) {
                db.insert("product_variants", fields("id", pack.id(), "product_id", pack.productId(),
                    "pack_label", pack.quantity(), "amount_base", pack.amountBase(), "base_unit", pack.baseUnit()));
            }
            if (pack.retailerSku() != null && db.count(
                "SELECT COUNT(*) FROM platform_products WHERE platform_id='blinkit' AND (variant_id=? OR retailer_sku=?)",
                pack.id(), pack.retailerSku()) == 0) {
                db.insert("platform_products", fields("id", pack.id() + "-blinkit", "platform_id", "blinkit",
                    "variant_id", pack.id(), "retailer_sku", pack.retailerSku(), "source_url", pack.retailerUrl()));
            }
        }
    }

    private boolean missing(String table, String id) {
        return db.count("SELECT COUNT(*) FROM " + table + " WHERE id=?", id) == 0;
    }
}

