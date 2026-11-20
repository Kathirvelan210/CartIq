package com.smartcart;

import com.smartcart.dto.Models.Product;
import com.smartcart.grocery.PricePrediction;
import com.smartcart.service.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UnifiedTimingTest {
    @Test void timingUsesOnlyAnApprovedForecastForTheSameRecordedOffer() {
        var catalogue=mock(CatalogService.class);
        var offers=mock(CurrentOffers.class);
        var cart=mock(CartService.class);
        var predictions=mock(PricePrediction.class);
        var assistant=new UnifiedShoppingAssistant(null,null,catalogue,offers,null,null,cart,predictions);
        var product=new Product("rice500","rice","Test rice","Test","500 g","Grains","","#fff","bag",null,
            new BigDecimal("500"),"g",List.of());
        var observed=Instant.parse("2026-11-15T10:00:00Z");
        var quote=new CurrentOffers.Quote("blinkit",10000,20000,"kg",2L,observed,"Synthetic fixture");
        var scope=new PricePrediction.Scope(product.id(),"blinkit","area");
        when(catalogue.products("area","test rice",null,5,0)).thenReturn(List.of(product));
        when(offers.forPack(product,"area","owner")).thenReturn(List.of(quote));
        var input=new UnifiedShoppingAssistant.Message("Should I buy test rice now or wait?",null,null,null);

        assertEquals("RULE_BASED",assistant.answer("owner","area",input).method());
        when(predictions.approvedTiming("owner",scope)).thenReturn(Optional.of(testForecast(product,observed.minusSeconds(10))));
        assertNull(assistant.answer("owner","area",input).timing());
        when(predictions.approvedTiming("owner",scope)).thenReturn(Optional.of(testForecast(product,observed)));
        var reply=assistant.answer("owner","area",input);
        assertEquals("MODEL_ASSISTED",reply.method());
        assertEquals("rice500",reply.timing().variantId());
        assertEquals("CHECK_TIMING",reply.timing().advice().action());
        assertEquals(9800L,reply.timing().highMinor());
    }

    private static PricePrediction.Prediction testForecast(Product p,Instant observed) {
        return new PricePrediction.Prediction("FORECAST",p.canonicalProductId(),p.id(),p.quantity(),"blinkit","area",
            new PricePrediction.Reading(observed,10000L,"AVAILABLE"),null,"FRESH",1,LocalDate.parse("2026-11-16"),
            9600L,9400L,9800L,0.83,"synthetic-test",observed,"Synthetic fixture",
            new PricePrediction.Advice("CHECK_TIMING","Synthetic advice for contract testing only."));
    }
}
