package com.smartcart.integration;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

class ConcurrentComparisonTest {
    @TempDir Path directory;

    @Test void floorAndExistingSingleAttemptPreventEveryLegFromDispatching() throws Exception {
        var path=directory.resolve("limits");
        var ids=List.of("a".repeat(64),"b".repeat(64),"c".repeat(64));
        var budget=new QuickCommerceBudget(path,2);
        Files.writeString(path,"cartiq-qc-budget-v1\n2\nsettled\n");
        assertThrows(com.smartcart.exception.ApiException.class,()->budget.spendComparison(ids,2,start->fail("Unexpected dispatch")));
        Files.writeString(path,"cartiq-qc-budget-v1\n92\nsettled\n"+ids.get(0)+"\n");
        assertThrows(QuickCommerceProvider.DuplicateLookup.class,()->budget.spendComparison(ids,92,start->fail("Duplicate dispatched")));
        assertFalse(budget.inspect().pending());
    }

    @Test void oneRemainingAttemptCannotFundThreeRetailers() throws Exception {
        var path = directory.resolve("one-attempt-left");
        var ledger = new StringBuilder("cartiq-qc-budget-v1\n85\nsettled\n");
        for (int i = 0; i < 19; i++) ledger.append("%064x%n".formatted(i));
        Files.writeString(path, ledger);
        var budget = new QuickCommerceBudget(path, 85);
        assertEquals(1, budget.inspect().allowance());
        var ids = List.of("a".repeat(64), "b".repeat(64), "c".repeat(64));
        assertThrows(com.smartcart.exception.ApiException.class,
            () -> budget.spendComparison(ids, 85, start -> fail("Unexpected dispatch")));
        assertEquals(ledger.toString(), Files.readString(path));
    }

    @Test void extensionPreservesHistoryAndIsExhaustedAcrossRestarts() throws Exception {
        var path = directory.resolve("extended");
        var ledger = new StringBuilder("cartiq-qc-budget-v1\n85\nsettled\n");
        for (int i = 0; i < 19; i++) ledger.append("%064x%n".formatted(i));
        Files.writeString(path, ledger);
        var policy = path.resolveSibling("extended.attempt-limit");
        Files.writeString(policy, "39");
        var budget = new QuickCommerceBudget(path, 85);
        assertEquals(20, budget.inspect().allowance());
        assertThrows(QuickCommerceProvider.DuplicateLookup.class,
            () -> budget.spend("%064x".formatted(0), remaining -> fail("Duplicate dispatched")));
        for (int i = 19; i < 39; i++) budget.spend("%064x".formatted(i), remaining -> remaining);
        var reopened = new QuickCommerceBudget(path, 65);
        assertEquals(39, reopened.inspect().attempts().size());
        assertEquals(0, reopened.inspect().allowance());
        assertThrows(com.smartcart.exception.ApiException.class,
            () -> reopened.spend("%064x".formatted(39), remaining -> fail("Budget exceeded")));
        Files.writeString(policy, "invalid");
        assertThrows(com.smartcart.exception.ApiException.class, reopened::inspect);
    }

    @Test void interruptedBatchCannotBeClearedByRestartOrMistakenForOneReservation() throws Exception {
        var path=directory.resolve("crash");
        Files.writeString(path,"cartiq-qc-budget-v1\n3\nsettled\n");
        var ids=List.of("a".repeat(64),"b".repeat(64),"c".repeat(64));
        var budget=new QuickCommerceBudget(path,3);
        assertThrows(IllegalStateException.class,()->budget.spendComparison(ids,3,start->{throw new IllegalStateException("fixture");}));
        assertEquals("cartiq-qc-batch-pending-v1",Files.readAllLines(path).get(0));
        assertEquals("batch\t"+String.join("\t",ids),Files.readAllLines(path).get(3));
        var restored=new QuickCommerceBudget(path,0).inspect();
        assertTrue(restored.pending()); assertEquals(0,restored.allowance()); assertEquals(3,restored.attempts().size());
    }

    @ParameterizedTest
    @ValueSource(strings={"repeat","single","pair","charged","platform-failed","malformed","balance-disagrees","body-only","missing-billing"})
    void reservesOnceAndSettlesIndependentlyOfCompletionOrder(String mode) throws Exception {
        var ledger=directory.resolve("isolated-ledger");
        Files.writeString(ledger,"cartiq-qc-budget-v1\n92\nsettled\n");
        var budget=new QuickCommerceBudget(ledger,92);
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var pool=Executors.newCachedThreadPool();
        server.setExecutor(pool);
        var selected=switch(mode){case "repeat","single"->List.of("zepto");case "pair"->List.of("blinkit","instamart");default->QuickCommerceProvider.RETAILERS;};
        var arrivals=new CountDownLatch(selected.size());
        var paid=new AtomicInteger();
        var simultaneous=new AtomicInteger();
        var receipts=new ConcurrentLinkedQueue<QuickCommerceProvider.ComparisonRow>();
        int debits=mode.equals("platform-failed")?2:selected.size();
        server.createContext("/v1/",exchange->{
            var path=exchange.getRequestURI().getPath();
            String body;
            if(path.endsWith("platforms")) body="{\"search\":[\"BlinkIt\",\"Zepto\",\"Swiggy\"]}";
            else if(path.endsWith("credits"))body="{\"summary\":{\"total_available\":"+
                (paid.get()==0?92:92-debits+(mode.equals("balance-disagrees")?1:0))+"}}";
            else if(path.endsWith("search")){
                String query=exchange.getRequestURI().getQuery();
                String platform=List.of("BlinkIt","Zepto","Swiggy").stream().filter(p->query.contains("platform="+p)).findFirst().orElseThrow();
                assertTrue(query.contains("pincode=641008"));
                assertTrue(selected.contains(switch(platform){case "BlinkIt"->"blinkit";case "Zepto"->"zepto";default->"instamart";}));
                var reserved=budget.inspect();
                assertTrue(reserved.pending()); assertEquals(92-selected.size(),reserved.balance()); assertEquals(selected.size()+(mode.equals("repeat")?1:0),reserved.attempts().size());
                paid.incrementAndGet(); arrivals.countDown();
                try { if(arrivals.await(4,TimeUnit.SECONDS)) simultaneous.incrementAndGet(); }
                catch(InterruptedException e){throw new RuntimeException(e);}
                int snapshot=platform.equals("BlinkIt")?91:92-debits;
                if(!mode.equals("body-only")&&!mode.equals("missing-billing"))exchange.getResponseHeaders().set("X-Credits-Remaining",String.valueOf(snapshot));
                boolean failure=mode.equals("platform-failed")&&platform.equals("Swiggy");
                body=mode.equals("malformed")&&platform.equals("Swiggy")?"{":
                    "{\"status\":\"success\",\"request_id\":\""+UUID.randomUUID()+"\",\"credits_remaining\":"+snapshot+
                    ",\"data\":{\"platform\":\""+platform+"\",\"lat\":10.942972,\"lon\":76.953417,\"query\":\"milk\","+
                    "\"failed_platforms\":"+(failure?"[\"Swiggy\"]":"[]")+",\"products\":"+
                    (failure?"[]":"[{\"id\":42,\"brand\":\"Fixture\",\"name\":\"Fixture Milk\",\"quantity\":\"1 L\",\"offer_price\":50,\"available\":true}]")+"}}";
                if(mode.equals("missing-billing"))body=body.replace("\"credits_remaining\":"+snapshot+",","");
            } else throw new AssertionError("Unexpected endpoint");
            var bytes=body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            try(var out=exchange.getResponseBody()){out.write(bytes);}
        });
        server.start();
        var provider=new QuickCommerceProvider(RestClient.create("http://127.0.0.1:"+server.getAddress().getPort()),"local-fixture",true,budget);
        try {
            for (var invalid:List.of(List.<String>of(),List.of("zepto","zepto"),List.of("unknown"))) {
                assertThrows(com.smartcart.exception.ApiException.class,()->provider.quoteComparison("milk",10.942972,76.953417,"641008",invalid));
            }
            if(mode.equals("repeat")){
                var previous=provider.quoteComparison("milk",10.942972,76.953417,"641008",selected);
                var key=previous.legs().get(0).reservationKey();
                Files.writeString(ledger,"cartiq-qc-budget-v2\n92\nsettled\n"+key+"\n");
                assertNotNull(provider.quoteComparison("milk",10.942972,76.953417,"641008",selected).blocker());
                Files.writeString(ledger,"evidence\t"+key+"\t"+UUID.randomUUID()+"\tprovider-no-charge\n",StandardOpenOption.APPEND);
                assertThrows(com.smartcart.exception.ApiException.class,()->provider.compareConfirmed("milk",10.942972,76.953417,"641008",selected,previous,receipts::add));
            }
            var quote=provider.quoteComparison("milk",10.942972,76.953417,"641008",selected);
            assertEquals(selected,quote.legs().stream().map(QuickCommerceProvider.ComparisonLeg::retailer).toList());
            assertNull(quote.blocker()); assertEquals(0,paid.get());
            var billing=provider.compareConfirmed("milk",10.942972,76.953417,"641008",selected,quote,receipts::add);
            assertEquals(selected.size(),paid.get()); assertEquals(selected.size(),simultaneous.get()); assertEquals(selected.size(),receipts.size());
            assertEquals(mode.equals("repeat")||mode.equals("single")||mode.equals("pair")||mode.equals("charged")||mode.equals("platform-failed")||mode.equals("body-only"),billing.settled());
            assertEquals(!billing.settled(),budget.inspect().pending());
            assertEquals(mode.equals("platform-failed")||mode.equals("malformed")?2:selected.size(),
                receipts.stream().filter(row->row.result()!=null).count());
            assertEquals(billing.settled()?92-debits:89,budget.inspect().balance());
            assertThrows(com.smartcart.exception.ApiException.class,()->provider.compareConfirmed("milk",10.942972,76.953417,"641008",selected,quote,receipts::add));
            assertEquals(selected.size(),paid.get());
        } finally { provider.closeTransport(); server.stop(0);pool.shutdownNow(); }
    }
}
