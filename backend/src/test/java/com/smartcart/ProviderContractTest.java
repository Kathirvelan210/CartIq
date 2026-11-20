package com.smartcart;
import com.smartcart.integration.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class ProviderContractTest {
    @Test void similarNamesAreInsufficientWithoutBrandAndExactNormalizedPack(){var observation=new GroceryDataProvider.Observation("blinkit","test","560038",Instant.now(),"India Gate Basmati Rice 5 kg","India Gate","5 kg",45000L,50000L,true,null);assertTrue(ProviderSyncService.identityMatches(observation,"Basmati Rice","India Gate",new BigDecimal("5000"),"g"));assertFalse(ProviderSyncService.identityMatches(observation,"Basmati Rice","Another Brand",new BigDecimal("5000"),"g"));assertFalse(ProviderSyncService.identityMatches(observation,"Basmati Rice","India Gate",new BigDecimal("1000"),"g"));assertFalse(ProviderSyncService.identityMatches(observation,"Brown Rice","India Gate",new BigDecimal("5000"),"g"));}

}
