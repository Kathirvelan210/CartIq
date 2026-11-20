package com.smartcart;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcart.catalogimport.ImportNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import com.smartcart.exception.ApiException;
import static org.junit.jupiter.api.Assertions.*;
class CatalogueImportNormalizerTest {
 // Body 301762002011 has weighted sum 43: check digit is 7, not 1.
 @ParameterizedTest
 @CsvSource({"96385074,00000096385074","036000291452,00036000291452","4006381333931,04006381333931","3017620020117,03017620020117","10012345000017,10012345000017","00036000291452,00036000291452"})
 void barcodeValidation(String input,String expected){assertEquals(expected,ImportNormalizer.gtin(input));}
 @ParameterizedTest
 @ValueSource(strings={"96385075","036000291453","4006381333932","3017620020111","3017620020112","10012345000018"})
 void rejectsInvalidCheckDigits(String input){var error=assertThrows(ApiException.class,()->ImportNormalizer.gtin(input));assertEquals("Barcode check digit is invalid.",error.getMessage());}
 @ParameterizedTest
 @NullAndEmptySource
 @ValueSource(strings={"abc","1234567","123456789","12345678901","123456789012345"," 96385074","96385074 ","9638-5074","9638507A"})
 void rejectsMalformedBarcodes(String input){assertThrows(ApiException.class,()->ImportNormalizer.gtin(input));}
 @Test void normalization() throws Exception {var p=ImportNormalizer.parse(new ObjectMapper().readTree("{\"product_name\":\"  Rice  Flour \",\"brands\":\"Brand\",\"quantity\":\"1 kg\"}"));assertEquals("Rice Flour",p.name());assertEquals(0,p.amount().compareTo(new java.math.BigDecimal("1000")));assertEquals("g",p.unit());assertNull(p.image());}
 @Test void incompleteAndMultipacksRequireReview() throws Exception {var j=new ObjectMapper();assertThrows(RuntimeException.class,()->ImportNormalizer.parse(j.readTree("{}")));assertThrows(RuntimeException.class,()->ImportNormalizer.parse(j.readTree("{\"product_name\":\"Milk\",\"brands\":\"Brand\",\"quantity\":\"2 x 1 l\"}")));}
}
