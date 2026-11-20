package com.smartcart.catalogimport;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartcart.exception.ApiException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class ImportNormalizer {
 private ImportNormalizer(){}
 public record Product(String name,String brand,String pack,BigDecimal amount,String unit,String image){}
 public record Pack(BigDecimal amount,String unit){}
 public record LabelData(String ingredients,String nutritionBasis,Map<String,BigDecimal> nutritionPer100){}
 private static final Pattern PACK=Pattern.compile("(?i)^([0-9]+(?:[.,][0-9]+)?)\\s*(kg|g|ml|l|litres?|liters?|pieces?|pcs)$");
 private static final List<String> NUTRIENTS=List.of("energy-kcal","energy-kj","fat","saturated-fat",
  "carbohydrates","sugars","fiber","proteins","salt","sodium");
 private static final Map<String,String> CATEGORY_TAGS=Map.ofEntries(
  Map.entry("en:dairies","dairy"),Map.entry("en:eggs","dairy"),Map.entry("en:fruits","produce"),
  Map.entry("en:vegetables","produce"),Map.entry("en:beverages","beverages"),Map.entry("en:snacks","snacks"),
  Map.entry("en:cereals-and-potatoes","pantry"),Map.entry("en:sauces","pantry"),Map.entry("en:condiments","pantry"),
  Map.entry("en:plant-based-oils","pantry"),Map.entry("en:pastas","pantry"),Map.entry("en:rices","pantry"));
 public static String gtin(String code){
  if(code==null||!code.matches("(?:[0-9]{8}|[0-9]{12,14})"))throw ApiException.bad("Provide a valid GTIN-8, UPC, EAN-13 or GTIN-14.");
  int sum=0;for(int i=code.length()-2,weight=3;i>=0;i--,weight=4-weight)sum+=(code.charAt(i)-'0')*weight;
  if((10-sum%10)%10!=code.charAt(code.length()-1)-'0')throw ApiException.bad("Barcode check digit is invalid.");
  return "0".repeat(14-code.length())+code;
 }
 public static String clean(String s){return s==null?"":s.strip().replaceAll("\\s+"," ");}
 public static Product parse(JsonNode p){
  String name=clean(p.path("product_name").asText()),brand=clean(p.path("brands").asText()),pack=clean(p.path("quantity").asText());
  if(name.isEmpty()||name.length()>150||brand.isEmpty()||brand.length()>100||brand.contains(","))throw ApiException.bad("Missing or ambiguous name/brand; review required.");
  Pack normalized=pack(pack);
  return new Product(name,brand,pack,normalized.amount(),normalized.unit(),image(p));
 }
 public static Pack pack(String value){
  if(value==null||value.length()>60)throw ApiException.bad("Use a supported pack size, such as 500 g, 1 litre or 6 pieces.");
  var m=PACK.matcher(clean(value));
  if(!m.matches())throw ApiException.bad("Use a supported pack size, such as 500 g, 1 litre or 6 pieces.");
  BigDecimal amount=new BigDecimal(m.group(1).replace(',','.'));String unit=m.group(2).toLowerCase(Locale.ROOT);
  if(unit.equals("kg")||unit.startsWith("l"))amount=amount.multiply(BigDecimal.valueOf(1000));
  unit=unit.equals("kg")?"g":unit.startsWith("l")?"ml":unit.startsWith("piece")||unit.equals("pcs")?"piece":unit;
  int precision=unit.equals("piece")?0:3;
  if(amount.signum()<=0||amount.compareTo(new BigDecimal("999999999.999"))>0||amount.stripTrailingZeros().scale()>precision)throw ApiException.bad("Unsupported pack amount.");
  return new Pack(amount,unit);
 }
 public static String image(JsonNode payload){
  String url=payload.path("image_front_url").asText("");
  if(url.length()<=1000&&url.matches("https://(?:images|static)\\.openfoodfacts\\.org/images/products/[a-zA-Z0-9_./-]+\\.(?:jpg|jpeg|png|webp)"))return url;
  String code=payload.path("code").asText();
  if(!code.matches("[0-9]{8}|[0-9]{12,14}"))return null;
  JsonNode images=payload.path("images"), front=images.path("selected").path("front");
  for(String language:List.of("en",payload.path("lang").asText("en"))){
   if(!language.matches("[a-z]{2}"))continue;
   JsonNode selected=front.path(language);
   if(selected.isMissingNode())selected=images.path("front_"+language);
   String revision=selected.path("rev").asText();
   if(!revision.matches("[0-9]{1,10}")||!selected.path("sizes").has("400"))continue;
   String folder=code;
   if(code.length()>8){
    String padded="0".repeat(Math.max(0,13-code.length()))+code;
    folder=padded.substring(0,3)+"/"+padded.substring(3,6)+"/"+padded.substring(6,9)+"/"+padded.substring(9);
   }
   return "https://images.openfoodfacts.org/images/products/"+folder+"/front_"+language+"."+revision+".400.jpg";
  }
  return null;
 }
 public static String categorySuggestion(JsonNode payload){
  var matches=new java.util.HashSet<String>();
  payload.path("categories_tags").forEach(tag->{String match=CATEGORY_TAGS.get(tag.asText());if(match!=null)matches.add(match);});
  return matches.size()==1?matches.iterator().next():null;
 }
 public static LabelData metadata(JsonNode payload,String unit){
  String ingredients=clean(payload.path("ingredients_text_en").asText(payload.path("ingredients_text").asText()));
  if(ingredients.isEmpty()||ingredients.length()>12000)ingredients=null;
  Map<String,BigDecimal> values=new LinkedHashMap<>();
  JsonNode aggregated=payload.path("nutrition").path("aggregated_set");
  String basis=aggregated.path("per").asText();
  boolean useAggregated=basis.equals("100g")||basis.equals("100ml");
  if(!useAggregated)basis="ml".equals(unit)?"100ml":"100g";
  for(String nutrient:NUTRIENTS){
   JsonNode modern=aggregated.path("nutrients").path(nutrient);
   String expected=nutrient.equals("energy-kcal")?"kcal":nutrient.equals("energy-kj")?"kJ":"g";
   if(useAggregated&&(!expected.equalsIgnoreCase(modern.path("unit").asText())||
     !"packaging".equals(modern.path("source").asText())||
     !modern.path("modifier").asText("").isBlank()))continue;
   var element=useAggregated?modern.path("value"):payload.path("nutriments").path(nutrient+"_100g");
   if(!element.isNumber()&&!element.isTextual())continue;
   try{
    var value=new BigDecimal(element.asText());
    int upper=nutrient.startsWith("energy-")?10000:100;
    if(value.signum()>=0&&value.compareTo(BigDecimal.valueOf(upper))<=0&&value.scale()<=20)values.put(nutrient,value);
   }catch(NumberFormatException ignored){/* The raw value remains available for review. */}
  }
  return new LabelData(ingredients,basis,values);
 }
}
