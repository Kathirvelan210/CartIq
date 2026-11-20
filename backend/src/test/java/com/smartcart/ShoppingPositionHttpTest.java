package com.smartcart;

import com.smartcart.exception.ApiException;
import com.smartcart.service.AuthService;
import com.smartcart.service.ShoppingPositions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"server.address=127.0.0.1", "spring.datasource.url=jdbc:h2:mem:gpsHttp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class ShoppingPositionHttpTest {
    @Autowired AuthService auth;
    @Autowired ShoppingPositions positions;
    @Autowired TestRestTemplate http;

    @Test void onlyTheAuthenticatedOwnerCanSelectOrClearTheirFix() {
        var owner = auth.create("Device tester","device-owner@example.invalid",UUID.randomUUID().toString(),null,false).session();
        var peer = auth.create("Peer tester","device-peer@example.invalid",UUID.randomUUID().toString(),null,false).session();
        var point = new ShoppingPositions.Fix(12d,77d,3500d,System.currentTimeMillis(),"gps",null);
        assertEquals(HttpStatus.UNAUTHORIZED, send(HttpMethod.PUT,null,point));
        assertEquals(HttpStatus.OK, send(HttpMethod.PUT,owner.token(),point));
        positions.require(owner.user().id(),12d,77d,null);
        assertThrows(ApiException.class,()->positions.require(peer.user().id(),12d,77d,null));
        assertEquals(HttpStatus.BAD_REQUEST, send(HttpMethod.PUT,owner.token(),
                new ShoppingPositions.Fix(14d,78d,10d,point.observedAt()-901_000,"gps",null)));
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE,peer.token(),null));
        positions.require(owner.user().id(),12d,77d,null);
        assertEquals(HttpStatus.NO_CONTENT, send(HttpMethod.DELETE,owner.token(),null));
        assertThrows(ApiException.class,()->positions.require(owner.user().id(),12d,77d,null));
        var manual=new ShoppingPositions.Fix(11d,76d,null,System.currentTimeMillis(),"manual",null);
        assertEquals(HttpStatus.OK,send(HttpMethod.PUT,owner.token(),manual));
        positions.require(owner.user().id(),11d,76d,null);
        assertThrows(ApiException.class,()->positions.require(peer.user().id(),11d,76d,null));
        assertEquals(HttpStatus.BAD_REQUEST,send(HttpMethod.PUT,owner.token(),
            new ShoppingPositions.Fix(11d,76d,1d,manual.observedAt(),"manual",null)));
    }

    private HttpStatusCode send(HttpMethod method,String token,ShoppingPositions.Fix fix) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if(token!=null) headers.setBearerAuth(token);
        return http.exchange("/api/me/shopping-position",method,new HttpEntity<>(fix,headers),String.class).getStatusCode();
    }
}
