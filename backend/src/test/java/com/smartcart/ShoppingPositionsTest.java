package com.smartcart;

import com.smartcart.service.ShoppingPositions;
import com.smartcart.service.ShoppingPositions.Fix;
import com.smartcart.exception.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShoppingPositionsTest {
    final ShoppingPositions positions=new ShoppingPositions();
    final long now=System.currentTimeMillis();
    final Fix gps=new Fix(11d,76d,9d,now,"gps",null);
    @Test void gpsWithoutGeocoderIsOwnerScopedAndSwitchInvalidatesOldPosition(){
        positions.select("a",gps);
        positions.require("a",11d,76d,null);
        assertThrows(ApiException.class,()->positions.require("b",11d,76d,null));
        positions.select("a",new Fix(12d,77d,null,now,"pin","560001"));
        assertThrows(ApiException.class,()->positions.require("a",11d,76d,null));
        positions.require("a",12d,77d,"560001");
        positions.clear("a");
        assertThrows(ApiException.class,()->positions.require("a",12d,77d,"560001"));
    }
    @Test void rejectsStaleFutureInvalidAndIncompletePositions(){
        for(var bad:java.util.List.of(new Fix(91d,1d,5d,now,"gps",null),new Fix(1d,1d,null,now,"gps",null),
            new Fix(1d,1d,5d,now-901000,"gps",null),new Fix(1d,1d,5d,now+60000,"gps",null),
            new Fix(1d,181d,null,now,"pin","641008"),new Fix(1d,1d,null,now,"pin","000001")))
            assertThrows(ApiException.class,()->positions.select("a",bad));
    }
}
