package com.smartcart.controller;

import com.smartcart.exception.ApiException;
import com.smartcart.integration.QuickCommerceProvider;
import com.smartcart.service.ShoppingPositions;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShopperComparisonControllerTest {
    private final ShoppingPositions positions=new ShoppingPositions();
    private final QuickCommerceProvider provider=mock(QuickCommerceProvider.class);
    private final ShopperComparisonController controller=new ShopperComparisonController(positions,provider);
    private final ShopperComparisonController.Selection selection=new ShopperComparisonController.Selection("Milk",10.0,76.0,"641008",null);
    private static Jwt user(String owner){return Jwt.withTokenValue("fixture").header("alg","none").subject(owner).build();}
    private void select(String owner,double lat){positions.select(owner,new ShoppingPositions.Fix(lat,76.0,null,System.currentTimeMillis(),"manual","641008"));}
    private void quote(String blocker){when(provider.quoteComparison("milk",10,76,"641008",QuickCommerceProvider.RETAILERS)).thenReturn(
        new QuickCommerceProvider.ComparisonQuote(90,10,List.of(),blocker,Instant.now()));}

    @Test void approvalIsBoundToOwnerPositionAndConsumedOnce(){
        try {
            var owner=user("one");select("one",10);quote(null);
            var preview=controller.prepare(owner,selection).getBody();
            assertNotNull(preview);
            var yes=new ShopperComparisonController.Confirmation(preview.id(),true);
            assertThrows(ApiException.class,()->controller.progress(user("two"),preview.id()));
            assertThrows(ApiException.class,()->controller.confirm(user("two"),yes));
            assertThrows(ApiException.class,()->controller.confirm(owner,new ShopperComparisonController.Confirmation(preview.id(),false)));
            select("one",11);
            assertThrows(ApiException.class,()->controller.confirm(owner,yes));
            select("one",10);
            controller.confirm(owner,yes);
            assertThrows(ApiException.class,()->controller.confirm(owner,yes));
            verify(provider,timeout(2000).times(1)).compareConfirmed(eq("milk"),eq(10d),eq(76d),eq("641008"),eq(QuickCommerceProvider.RETAILERS),eq(preview.quote()),any());
        } finally {controller.close();}
    }

    @Test void confirmationUsesOnlyTheRetailersInTheApprovedPreview(){
        try {
            var owner=user("one");select("one",10);
            var pair=List.of("blinkit","instamart");
            var input=new ShopperComparisonController.Selection("Milk",10d,76d,"641008",pair);
            when(provider.quoteComparison("milk",10,76,"641008",pair)).thenReturn(
                new QuickCommerceProvider.ComparisonQuote(90,2,List.of(),null,Instant.now()));
            var preview=controller.prepare(owner,input).getBody();
            assertEquals(pair,preview.selection().retailers());
            assertEquals(2,preview.maximumCredits());
            controller.confirm(owner,new ShopperComparisonController.Confirmation(preview.id(),true));
            verify(provider,timeout(2000).times(1)).compareConfirmed(eq("milk"),eq(10d),eq(76d),eq("641008"),eq(pair),eq(preview.quote()),any());
        } finally {controller.close();}
    }

    @Test void cartTakesOnlyTheSelectedReturnedSkuAndUsesItsServerPrice() {
        try {
            var owner=user("one"); select("one",10);quote(null);
            var item=new QuickCommerceProvider.Product("sku42","Test pack","Test","1 L","INR",6200L,null,true,2,
                null,null,null,null,null,null,null,null,null,null,null);
            var result=new QuickCommerceProvider.Result("zepto",87,200,new QuickCommerceProvider.Location(10,76,"641008","641008"),
                List.of(item),Instant.now(),"12345678-1111-4444-8888-123456789012",60);
            doAnswer(call->{java.util.function.Consumer<QuickCommerceProvider.ComparisonRow> publish=call.getArgument(6);
                publish.accept(new QuickCommerceProvider.ComparisonRow("zepto",result,QuickCommerceProvider.Outcome.SUCCESS,null,null,200));
                return new QuickCommerceProvider.ComparisonBilling(true,87);
            }).when(provider).compareConfirmed(anyString(),anyDouble(),anyDouble(),anyString(),anyList(),any(),any());
            var id=controller.prepare(owner,selection).getBody().id();
            controller.confirm(owner,new ShopperComparisonController.Confirmation(id,true));
            org.awaitility.Awaitility.await().until(()->controller.progress(owner,id).getBody().done());
            var place=new ShopperComparisonController.Place(10d,76d,"641008");
            var pick=new ShopperComparisonController.Pick("zepto","sku42");
            var subset=new ShopperComparisonController.Selection(" MILK ",10d,76d,"641008",List.of("zepto"));
            assertEquals(id,controller.cached(owner,subset).getBody().get(0).jobId());
            assertThrows(ApiException.class,()->controller.prepare(owner,subset));
            assertTrue(controller.cached(user("two"),subset).getBody().isEmpty());
            assertTrue(controller.cached(owner,new ShopperComparisonController.Selection("milk",11d,76d,"641008",null)).getBody().isEmpty());
            assertTrue(controller.cached(owner,new ShopperComparisonController.Selection("milk",10d,76d,"641008",List.of("blinkit"))).getBody().isEmpty());
            assertThrows(ApiException.class,()->controller.add(owner,id,new ShopperComparisonController.Pick("blinkit","sku42")));
            assertThrows(ApiException.class,()->controller.add(owner,id,new ShopperComparisonController.Pick("zepto","missing")));
            var line=controller.add(owner,id,pick).getBody().get(0);assertEquals(6200L,line.priceMinor());assertEquals("zepto",line.retailer());
            select("two",10);assertTrue(controller.basket(user("two"),place).getBody().isEmpty());
            assertThrows(ApiException.class,()->controller.add(user("two"),id,pick));
            assertEquals(2,controller.quantity(owner,new ShopperComparisonController.Change(place,line.id(),2)).getBody().get(0).quantity());
            assertThrows(ApiException.class,()->controller.add(owner,id,pick));
            select("one",11);assertThrows(ApiException.class,()->controller.add(owner,id,pick));
            select("one",10);
            assertTrue(controller.quantity(owner,new ShopperComparisonController.Change(place,line.id(),0)).getBody().isEmpty());
        } finally {controller.close();}
    }

    @Test void singleReceiptReopensOnlyForOriginalAccountAndPlaceWithoutProviderSearch() {
        var single = new ShopperSearchController(positions,provider);
        try {
            var owner = user("one");
            select("one",10);
            var selection = new ShopperSearchController.Selection("milk","zepto",10d,76d,"641008");
            var result = new QuickCommerceProvider.Result("zepto",84,200,new QuickCommerceProvider.Location(10,76,"641008","641008"),
                List.of(),Instant.now(),"12345678-1111-4444-8888-123456789012",60);
            when(provider.search("zepto","milk",10,76,"641008")).thenReturn(result);
            var id = single.prepare(owner,selection).getBody().id();
            var first = single.confirm(owner,new ShopperSearchController.Confirmation(id,true)).getBody();
            assertEquals(first,single.receipt(owner,id).getBody());
            assertThrows(ApiException.class,()->single.receipt(user("two"),id));
            select("one",11);
            assertThrows(ApiException.class,()->single.receipt(owner,id));
            verify(provider,times(1)).search(anyString(),anyString(),anyDouble(),anyDouble(),anyString());
        } finally {single.close();controller.close();}
    }

    @Test void blockedPreviewAndUnsavedLocationCannotDispatch(){
        try {
            var owner=user("one");quote("Duplicate");
            assertThrows(ApiException.class,()->controller.prepare(owner,selection));
            select("one",10);var preview=controller.prepare(owner,selection).getBody();
            assertNull(preview.id()); assertEquals(3,preview.maximumCredits());
            verify(provider,never()).compareConfirmed(anyString(),anyDouble(),anyDouble(),anyString(),anyList(),any(),any());
        } finally {controller.close();}
    }
}
