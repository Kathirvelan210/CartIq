package com.smartcart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.ImportNormalizer;
import com.smartcart.dto.Models.*;
import com.smartcart.repository.Db;
import com.smartcart.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.BadSqlGrammarException;
import java.math.*;
import java.time.*;
import java.util.*;
import static com.smartcart.repository.Db.*;

@Service
public class CatalogService {
    private static final String PRODUCT_COLUMNS="SELECT v.*,p.name,p.brand,p.variant_name,p.description,p.color,p.art,p.image_url,c.name AS category";
    private static final String ACTIVE_PRODUCTS=" FROM product_variants v JOIN products p ON p.id=v.product_id"
        +" JOIN product_categories c ON c.id=p.category_id WHERE p.active=TRUE";
    private static final String SEARCH_TEXT="LOWER(CONCAT(p.name,' ',p.brand,' ',p.description,' ',c.name,' ',v.pack_label))";
    private final Db db;private final boolean sample;
    private final OfferFreshness freshness;
    public CatalogService(Db db,boolean sample){this(db,sample,new OfferFreshness(900));}
    @org.springframework.beans.factory.annotation.Autowired
    public CatalogService(Db db,@Value("${smartcart.sample-data}") boolean sample,OfferFreshness freshness){this.db=db;this.sample=sample;this.freshness=freshness;}
    public OfferFreshness freshness(){return freshness;}
    public boolean sample(){return sample;}
    public List<Location> locations(){return db.rows("SELECT * FROM delivery_locations ORDER BY name").stream().map(r->new Location(text(r,"id"),text(r,"name"),text(r,"city"),text(r,"postcode"))).toList();}
    public void requireLocation(String id){db.one("SELECT id FROM delivery_locations WHERE id=?",id);}
    public List<Category> categories(){return db.rows("SELECT * FROM product_categories ORDER BY name").stream().map(r->new Category(text(r,"id"),text(r,"name"))).toList();}
    public List<Platform> platforms(){return db.rows("SELECT * FROM grocery_platforms ORDER BY id").stream().map(r->new Platform(text(r,"id"),text(r,"name"),number(r,"delivery_minor"),number(r,"handling_minor"),number(r,"minimum_minor"),number(r,"free_delivery_minor"),text(r,"fee_source"),bool(r,"enabled"))).toList();}
    public List<Product> products(String locationId,String query,String category,int limit,int offset){
        return products(locationId,query,category,limit,offset,"","");
    }
    public List<Product> products(String locationId,String query,String category,int limit,int offset,String brand,String pack){
        requireLocation(locationId);
        List<Object> parameters=new ArrayList<>();
        String from=filteredProducts(query,category,brand,pack,parameters);
        parameters.add(Math.max(1,Math.min(limit,100)));
        parameters.add(Math.max(0,offset));
        return db.rows(PRODUCT_COLUMNS+from+" ORDER BY p.name,v.amount_base,v.id LIMIT ? OFFSET ?",parameters.toArray())
            .stream().map(r->product(r,locationId)).toList();
    }
    public ProductPage productPage(String locationId,String query,String category,int page,int pageSize,String sort){
        return productPage(locationId,query,category,page,pageSize,sort,"","");
    }
    public ProductPage productPage(String locationId,String query,String category,int page,int pageSize,String sort,String brand,String pack){
        requireLocation(locationId);
        if(page<0||pageSize<1||pageSize>100||(long)page*pageSize>Integer.MAX_VALUE)throw ApiException.bad("Invalid pagination parameters.");
        String order=switch(sort){case "name_asc"->"p.name ASC,v.amount_base ASC,v.id ASC";case "name_desc"->"p.name DESC,v.amount_base ASC,v.id ASC";default->throw ApiException.bad("Unsupported sort order.");};
        List<Object> parameters=new ArrayList<>();
        String from=filteredProducts(query,category,brand,pack,parameters);
        long total=db.count("SELECT COUNT(*)"+from,parameters.toArray());
        long pages=(total+pageSize-1)/pageSize;
        parameters.add(pageSize);
        parameters.add(page*pageSize);
        List<Product> items=db.rows(PRODUCT_COLUMNS+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",parameters.toArray())
            .stream().map(r->product(r,locationId)).toList();
        return new ProductPage(items,page,pageSize,total,pages,(long)page+1<pages,page>0);
    }

    private String filteredProducts(String query,String category,String brand,String pack,List<Object> parameters){
        String term=query==null?"":query.trim().toLowerCase(Locale.ROOT);
        if(term.length()>100)throw ApiException.bad("Search is limited to 100 characters.");
        StringBuilder from=new StringBuilder(ACTIVE_PRODUCTS);
        if(category!=null&&!category.isEmpty()){
            from.append(" AND (c.id=? OR c.name=?)");
            parameters.add(category);
            parameters.add(category);
        }
        if(brand!=null&&!brand.isBlank()){
            if(brand.length()>100)throw ApiException.bad("The brand filter is limited to 100 characters.");
            from.append(" AND LOWER(p.brand)=?");
            parameters.add(brand.strip().toLowerCase(Locale.ROOT));
        }
        if(pack!=null&&!pack.isBlank()){
            var normalized=ImportNormalizer.pack(pack);
            from.append(" AND v.amount_base=? AND v.base_unit=?");
            parameters.add(normalized.amount());
            parameters.add(normalized.unit());
        }
        if(!term.isEmpty()){
            for(String word:term.split("\\s+")){
                from.append(" AND ").append(SEARCH_TEXT).append(" LIKE ? ESCAPE '!'");
                parameters.add("%"+word.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
            }
        }
        return from.toString();
    }
    public List<Product> variantsFor(String variantId,String locationId){
        Product selected=product(variantId,locationId);
        return variants(selected,locationId);
    }

    public Product productByBarcode(String gtin,String locationId){
        String barcode=ImportNormalizer.gtin(gtin.strip());
        requireLocation(locationId);
        Map<String,Object> row;
        try{
            row=db.maybe(PRODUCT_COLUMNS+ACTIVE_PRODUCTS
                +" AND v.id=(SELECT variant_id FROM variant_barcodes WHERE gtin=?)",barcode)
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"No published product matches this barcode."));
        }catch(BadSqlGrammarException error){
            if(missingTable(error)){
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Barcode lookup is unavailable for this catalogue.");
            }
            throw error;
        }
        return product(row,locationId);
    }

    public record Metadata(List<String> gtins,List<LabelSource> sources){}
    public record LabelSource(String source,String sourceUrl,Instant collectedAt,String verificationStatus,
        String attribution,String dataLicense,String imageLicense,String ingredients,String nutritionBasis,
        Map<String,BigDecimal> nutritionPer100){}

    public Metadata metadata(String variantId,String locationId){
        requireLocation(locationId);
        String unit=text(db.one("SELECT v.base_unit FROM product_variants v JOIN products p ON p.id=v.product_id WHERE v.id=? AND p.active=TRUE",variantId),"base_unit");
        try{
            var codes=db.rows("SELECT gtin FROM variant_barcodes WHERE variant_id=? ORDER BY gtin",variantId)
                .stream().map(row->text(row,"gtin")).toList();
            var sources=db.rows("SELECT * FROM external_product_sources WHERE variant_id=? ORDER BY source,external_id",variantId)
                .stream().map(row->labelSource(row,unit)).toList();
            return new Metadata(codes,sources);
        }catch(BadSqlGrammarException error){
            // The existing mysql-local profile deliberately stops before the provenance tables.
            if(missingTable(error))return new Metadata(List.of(),List.of());
            throw error;
        }
    }

    private static boolean missingTable(BadSqlGrammarException error){
        // MySQL missing table and H2 table not found in MySQL mode.
        String state=error.getSQLException().getSQLState();
        return "42S02".equals(state)||"42S04".equals(state);
    }

    private static final ObjectMapper LABEL_JSON=new ObjectMapper();
    private LabelSource labelSource(Map<String,Object> row,String unit){
        ImportNormalizer.LabelData label=new ImportNormalizer.LabelData(null,"100g",Map.of());
        if("OPEN_FOOD_FACTS".equals(text(row,"source"))){
            try{label=ImportNormalizer.metadata(LABEL_JSON.readTree(text(row,"payload_json")),unit);}
            catch(com.fasterxml.jackson.core.JsonProcessingException ignored){/* Still return the source attribution. */}
        }
        return new LabelSource(text(row,"source"),text(row,"source_url"),time(row,"collected_at"),
            text(row,"verification_status"),text(row,"attribution"),text(row,"data_license"),
            text(row,"image_license"),label.ingredients(),label.nutritionBasis(),label.nutritionPer100());
    }
    public Product product(String variantId,String locationId){requireLocation(locationId);return product(db.one("SELECT v.*,p.name,p.brand,p.variant_name,p.description,p.color,p.art,p.image_url,c.name AS category FROM product_variants v JOIN products p ON p.id=v.product_id JOIN product_categories c ON c.id=p.category_id WHERE v.id=? AND p.active=TRUE",variantId),locationId);}
    private Product product(Map<String,Object> r,String locationId){String variantId=text(r,"id"),label=text(r,"pack_label"),unit=text(r,"base_unit");BigDecimal amount=decimal(r,"amount_base");List<Offer> offers=db.rows("SELECT l.*,pp.platform_id,pp.source_url FROM location_prices l JOIN platform_products pp ON pp.id=l.platform_product_id JOIN grocery_platforms gp ON gp.id=pp.platform_id WHERE pp.variant_id=? AND l.location_id=? AND gp.enabled=TRUE ORDER BY pp.platform_id",variantId,locationId).stream().map(o->{Long price=number(o,"price_minor"),mrp=number(o,"mrp_minor");return new Offer(text(o,"platform_id"),price==null?null:price/100.0,mrp==null?null:mrp/100.0,text(o,"availability"),time(o,"observed_at"),variantId,label,locationId,text(o,"source"),text(o,"verification"),price,price==null?null:unitPrice(price,amount,unit),unitLabel(unit),"VERIFIED".equals(text(o,"verification"))?text(o,"source_url"):null);}).toList();if(!sample)offers=offers.stream().filter(o->"VERIFIED".equals(o.verificationStatus())).toList();return new Product(variantId,text(r,"product_id"),text(r,"name"),text(r,"brand"),label,text(r,"category"),text(r,"description"),text(r,"color"),text(r,"art"),text(r,"image_url"),amount,unit,offers);}
    public Optional<Offer> best(Product p){return p.offers().stream().filter(this::eligible).min(Comparator.comparingLong(Offer::priceMinor));}
    public boolean eligible(Offer o){return o.priceMinor()!=null&&o.priceMinor()>=0&&"available".equals(o.status())&&freshness.fresh(o.updatedAt(),Instant.now())&&("VERIFIED".equals(o.verificationStatus())||sample&&"SAMPLE".equals(o.verificationStatus()));}
    public List<Product> variants(Product p,String location){
        return packsForProduct(p.canonicalProductId(),p.baseUnit(),location);
    }
    public List<Product> packsForProduct(String productId,String unit,String location){
        requireLocation(location);
        return db.rows("SELECT v.*,p.name,p.brand,p.variant_name,p.description,p.color,p.art,p.image_url,c.name AS category FROM product_variants v JOIN products p ON p.id=v.product_id JOIN product_categories c ON c.id=p.category_id WHERE p.active=TRUE AND p.id=? AND v.base_unit=? ORDER BY v.amount_base,v.id",productId,unit).stream().map(r->product(r,location)).toList();
    }
    public static long unitPrice(long price,BigDecimal amount,String unit){return BigDecimal.valueOf(price).multiply(BigDecimal.valueOf("piece".equals(unit)?1:1000)).divide(amount,0,RoundingMode.HALF_UP).longValueExact();}
    public static String unitLabel(String unit){return switch(unit){case "g"->"kg";case "ml"->"litre";default->"piece";};}
}
