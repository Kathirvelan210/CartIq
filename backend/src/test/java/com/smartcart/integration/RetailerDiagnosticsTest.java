package com.smartcart.integration;

import com.smartcart.exception.ApiException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static com.smartcart.integration.QuickCommerceProvider.Outcome.*;
import static com.smartcart.integration.RetailerDiagnostics.Billing.*;
import static com.smartcart.integration.RetailerDiagnostics.Category.*;
import static org.junit.jupiter.api.Assertions.*;

class RetailerDiagnosticsTest {
    @Test void swiggyFailureCategoriesDoNotGuessRootCauseOrBilling(){
        var cases=List.of(
            new QuickCommerceProvider.LookupFailure(HttpStatus.BAD_GATEWAY,"fixture",PROVIDER_ERROR,true,"uuid",200,PLATFORM_FAILED_UNSPECIFIED),
            new QuickCommerceProvider.LookupFailure(HttpStatus.SERVICE_UNAVAILABLE,"fixture",PROVIDER_ERROR,true,null,401),
            new QuickCommerceProvider.LookupFailure(HttpStatus.NOT_FOUND,"fixture",UNAVAILABLE,true,null,404),
            new QuickCommerceProvider.LookupFailure(HttpStatus.BAD_GATEWAY,"fixture",PROVIDER_ERROR,false,null,502),
            new QuickCommerceProvider.LookupFailure(HttpStatus.BAD_GATEWAY,"fixture",PROVIDER_ERROR,false,null,500),
            new QuickCommerceProvider.LookupFailure(HttpStatus.BAD_GATEWAY,"fixture",PROVIDER_ERROR,false,null,200,ADAPTER_RESPONSE));
        var expected=List.of(PLATFORM_FAILED_UNSPECIFIED,AUTH_OR_ACCOUNT,UNAVAILABLE_AREA_OR_ITEM,RETAILER_UPSTREAM,PROVIDER_FAILURE,ADAPTER_RESPONSE);
        for(int i=0;i<cases.size();i++){
            var event=RetailerDiagnostics.outcome("cartiq","instamart","search",null,cases.get(i),true);
            assertEquals(expected.get(i),event.category());
            assertEquals(i<3?NO_CHARGE_SETTLED:UNCERTAIN,event.billing());
            assertEquals(i==0,event.failedPlatform());
        }
        var blocked=RetailerDiagnostics.outcome("cartiq","instamart","search",null,ApiException.bad("fixture secret"),false);
        assertEquals(LOCAL_GUARD,blocked.category());
        assertEquals(NOT_DISPATCHED,blocked.billing());
        assertFalse(blocked.toString().contains("fixture secret"));
    }
    @Test void lastSuccessUsesVerifiedTransportCompletionAndEventsStayBounded(){
        var diagnostics=new RetailerDiagnostics();
        var result=new QuickCommerceProvider.Result("zepto",91,200,null,List.of(),Instant.now(),"fixture");
        diagnostics.add(RetailerDiagnostics.outcome("cartiq","zepto","search",result,null,true));
        for(int i=0;i<130;i++)diagnostics.add(RetailerDiagnostics.outcome("cartiq","instamart","item",null,ApiException.bad("hidden"),false));
        assertNotNull(diagnostics.lastSuccess().get("zepto"));
        assertNull(diagnostics.lastSuccess().get("instamart"));
        assertEquals(120,diagnostics.events("instamart").size());
        assertTrue(diagnostics.events("zepto").isEmpty());
    }
}
