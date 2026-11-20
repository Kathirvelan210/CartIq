package com.smartcart.service;

import com.smartcart.repository.Db;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import static com.smartcart.repository.Db.*;

@Service
public class DemoService implements ApplicationRunner {
    private final Db db;private final boolean enabled;
    public DemoService(Db db,@Value("${smartcart.sample-data}") boolean enabled){this.db=db;this.enabled=enabled;}
    @Override @Transactional public void run(ApplicationArguments args){if(!enabled||db.count("SELECT COUNT(*) FROM products")>0)return;
        String[][] categories={{"dairy","Dairy & eggs"},{"produce","Fruits & vegetables"},{"snacks","Snacks"},{"pantry","Pantry"},{"beverages","Beverages"},{"personal","Personal care"},{"household","Household"}};
        for(var c:categories)db.insert("product_categories",fields("id",c[0],"name",c[1]));
        String[][] locations={{"blr-indiranagar","Indiranagar","Bengaluru","560038"},{"blr-koramangala","Koramangala","Bengaluru","560034"},{"mum-bandra","Bandra West","Mumbai","400050"}};
        for(var l:locations)db.insert("delivery_locations",fields("id",l[0],"name",l[1],"city",l[2],"postcode",l[3]));
        Object[][] platforms={{"blinkit","Blinkit",2500L,500L,9900L,49900L},{"instamart","Instamart",3000L,500L,9900L,49900L},{"zepto","Zepto",2000L,700L,9900L,39900L}};
        for(var p:platforms){db.insert("grocery_platforms",fields("id",p[0],"name",p[1],"delivery_minor",p[2],"handling_minor",p[3],"minimum_minor",p[4],"free_delivery_minor",p[5],"fee_source","SAMPLE","enabled",true));for(var l:locations)db.insert("platform_locations",fields("platform_id",p[0],"location_id",l[0]));}
        Object[][] rows={
            {"milk","Amul Taaza","Amul","dairy","Toned milk for everyday use.","#78B5DA","carton","1 litre",1000,"ml",6800,7200,7000},
            {"eggs","Farm Fresh Eggs","Fresho","dairy","A pack of six eggs. Store refrigerated.","#D5B994","box","6 pieces",6,"piece",5400,6200,5900},
            {"oats","Rolled Oats","Quaker","pantry","Wholegrain oats for breakfast.","#C1483E","bag","1 kg",1000,"g",17900,19900,18900},
            {"coffee","Classic Coffee","Nescafé","beverages","Instant coffee with a full-bodied flavour.","#70432C","jar","100 g",100,"g",34500,36900,34900},
            {"chips","Magic Masala Chips","Lay’s","snacks","Crispy potato chips with masala seasoning.","#4565AF","bag","90 g",90,"g",4500,5000,4700},
            {"oil","Sunlite Refined Oil","Fortune","pantry","Refined sunflower oil for cooking.","#E6B44C","bottle","1 litre",1000,"ml",13900,15500,14700},
            {"curd","Fresh Curd","Milky Mist","dairy","Creamy curd made from pasteurised milk.","#69A4A4","jar","400 g",400,"g",3800,4200,3900},
            {"almonds","California Almonds","Happilo","pantry","Whole almonds for snacking.","#9C8056","bag","200 g",200,"g",18900,22500,19500},
            {"juice","Real Orange Juice","Real","beverages","Orange fruit beverage. Serve chilled.","#E99A3D","carton","1 litre",1000,"ml",10900,12000,10500},
            {"soap","Gentle Beauty Bar","Dove","personal","A gentle cleansing bar.","#91A1BF","box","100 g",100,"g",5800,6500,6200},
            {"bananas","Yelakki Bananas","Fresh harvest","produce","Sweet small bananas.","#DFC45C","bag","500 g",500,"g",4200,4900,4500},
            {"tomatoes","Fresh Tomatoes","Fresh harvest","produce","Tomatoes for everyday cooking.","#C66B53","bag","500 g",500,"g",2400,2900,2600},
            {"rice","Basmati Rice","India Gate","pantry","Classic basmati rice.","#D4B780","bag","1 kg",1000,"g",9900,10900,10500},
            {"chicken","Fresh Chicken","Fresh harvest","produce","Boneless chicken; cook thoroughly.","#CC9A89","box","500 g",500,"g",17900,18900,17500},
            {"onion","Fresh Onion","Fresh harvest","produce","Onions for everyday meals.","#BE8D9F","bag","1 kg",1000,"g",3900,4500,4100},
            {"paste","Ginger Garlic Paste","Dabur","pantry","Ginger and garlic cooking paste.","#BBAD78","jar","200 g",200,"g",4900,5500,5200},
            {"spices","Biryani Masala","Everest","pantry","Blended spices for biryani.","#BA6E3C","box","100 g",100,"g",7900,8500,8200},
            {"detergent","Laundry Detergent","Surf Excel","household","Detergent powder for household laundry.","#639BC0","bag","1 kg",1000,"g",12000,12500,12200},
            {"shampoo","Daily Care Shampoo","Dove","personal","Daily care shampoo.","#C7BCD8","bottle","340 ml",340,"ml",25000,26500,25500},
            {"bread","Whole Wheat Bread","Britannia","pantry","Whole wheat bread for breakfast.","#BC9467","bag","400 g",400,"g",4500,4800,4600}
        };
        for(var r:rows){String pid=(String)r[0];db.insert("products",fields("id",pid,"category_id",r[3],"name",r[1],"brand",r[2],"variant_name","Standard","description",r[4],"color",r[5],"art",r[6]));variant(pid,pid,(String)r[7],(Integer)r[8],(String)r[9],new int[]{(Integer)r[10],(Integer)r[11],(Integer)r[12]},locations);}
        variant("rice-5kg","rice","5 kg",5000,"g",new int[]{44500,46000,45000},locations);variant("detergent-5kg","detergent","5 kg",5000,"g",new int[]{50000,52000,51000},locations);variant("oil-5l","oil","5 litres",5000,"ml",new int[]{65000,69000,66500},locations);
        recipe("biryani","Chicken Biryani",4,"Rinse and soak rice. Cook chicken with onion, tomato, paste and spices until safely cooked. Layer with partially cooked rice, cover and steam. Check chicken is cooked through before serving.","#D7BA85",new Object[][]{{"rice",400,"g"},{"chicken",500,"g"},{"onion",200,"g"},{"tomatoes",150,"g"},{"paste",30,"g"},{"spices",20,"g"},{"oil",40,"ml"}});
        recipe("overnight-oats","Overnight Oats",2,"Combine oats and milk in a covered container and refrigerate overnight. Add sliced banana and almonds before serving.","#C5D3BA",new Object[][]{{"oats",100,"g"},{"milk",300,"ml"},{"bananas",150,"g"},{"almonds",20,"g"}});
        recipe("egg-toast","Egg & Tomato Toast",2,"Cook eggs thoroughly with tomato and onion in a little oil. Serve with toasted bread.","#DDC6A8",new Object[][]{{"eggs",4,"piece"},{"tomatoes",100,"g"},{"onion",50,"g"},{"bread",160,"g"},{"oil",15,"ml"}});
    }
    private void variant(String id,String product,String label,int amount,String unit,int[] prices,String[][] locations){db.insert("product_variants",fields("id",id,"product_id",product,"pack_label",label,"amount_base",amount,"base_unit",unit));String[] stores={"blinkit","instamart","zepto"};for(int s=0;s<stores.length;s++){String listing=id+"-"+stores[s];db.insert("platform_products",fields("id",listing,"platform_id",stores[s],"variant_id",id,"retailer_sku","sample-"+listing));for(int l=0;l<locations.length;l++){long price=prices[s]+l*200L;Instant now=Instant.now().minusSeconds(s*60L);db.insert("location_prices",fields("id",listing+"-"+l,"platform_product_id",listing,"location_id",locations[l][0],"price_minor",price,"mrp_minor",Math.round(price*1.15),"availability",id.equals("almonds")&&s==0?"out_of_stock":"available","observed_at",now,"source","SAMPLE_FIXTURE","verification","SAMPLE"));for(int day=0;day<60;day++){long historical=Math.max(1,price+Math.round(Math.sin(day*.4+s)*price*.04));db.insert("price_history",fields("id",id(),"platform_product_id",listing,"location_id",locations[l][0],"price_minor",historical,"observed_at",now.minus(Duration.ofDays(day)),"source","SIMULATED_HISTORY","verification","SAMPLE"));}}}}
    private void recipe(String id,String name,int servings,String instructions,String color,Object[][] ingredients){db.insert("recipes",fields("id",id,"name",name,"servings",servings,"instructions",instructions,"source","Curated sample recipe — quantities are estimates; check dietary needs and safe cooking.","sample",true,"color",color));for(var i:ingredients)db.insert("recipe_ingredients",fields("id",id(),"recipe_id",id,"product_id",i[0],"amount_base",i[1],"base_unit",i[2]));}
    @Scheduled(fixedDelay=300000) public void refreshSamplePrices(){if(enabled)db.run("UPDATE location_prices SET observed_at=?,updated_at=? WHERE verification='SAMPLE'",Instant.now(),Instant.now());}
    @Transactional public void populateUser(String user){if(!enabled)return;String location="blr-indiranagar";db.insert("monthly_budgets",fields("id",id(),"user_id",user,"budget_month",YearMonth.now(ZoneId.of("Asia/Kolkata")).toString(),"limit_minor",500000L));
        for(int m=0;m<6;m++)for(int n=0;n<3;n++){YearMonth month=YearMonth.now(ZoneId.of("Asia/Kolkata")).minusMonths(m);int day=m==0?Math.min(LocalDate.now(ZoneId.of("Asia/Kolkata")).getDayOfMonth(),1+n*2):5+n*7;Instant date=month.atDay(day).atTime(8,0).atZone(ZoneId.of("Asia/Kolkata")).toInstant();String history=id();long price=6800L,quantity=2+n,total=price*quantity;db.insert("shopping_history",fields("id",history,"user_id",user,"location_id",location,"platform_id","blinkit","total_minor",total,"savings_minor",quantity*400,"purchased_at",date,"sample",true,"request_key","seed-"+m+"-"+n));db.insert("shopping_history_items",fields("id",id(),"history_id",history,"variant_id","milk","quantity",quantity,"unit_price_minor",price,"category_id","dairy"));db.insert("budget_transactions",fields("id",id(),"user_id",user,"history_id",history,"category_id","dairy","amount_minor",total,"occurred_at",date,"note","Simulated sample purchase"));}
        db.insert("user_pantry",fields("id",id(),"user_id",user,"product_id","rice","amount_base",1000,"base_unit","g"));db.insert("user_pantry",fields("id",id(),"user_id",user,"product_id","oil","amount_base",500,"base_unit","ml"));
        db.insert("replenishment_records",fields("id",id(),"user_id",user,"variant_id","milk","cycle_days",3,"last_purchased_at",Instant.now().minus(Duration.ofDays(4))));
        db.insert("notifications",fields("id",id(),"user_id",user,"title","Welcome to your sample workspace","body","All prices, spending history and forecasts in this workspace are illustrative. Your changes are private to this sample account.","kind","WELCOME","dedupe_key",user+"-welcome","sample",true));
    }
}
