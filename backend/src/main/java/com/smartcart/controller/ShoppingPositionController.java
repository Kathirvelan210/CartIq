package com.smartcart.controller;

import com.smartcart.service.LiveOffers;
import com.smartcart.service.ShoppingPositions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/shopping-position")
public class ShoppingPositionController {
    public record Selected(String areaId) {}
    private final ShoppingPositions positions;
    private final ObjectProvider<LiveOffers> pilot;
    public ShoppingPositionController(ShoppingPositions positions,ObjectProvider<LiveOffers> pilot){
        this.positions=positions;this.pilot=pilot;
    }
    @PutMapping public ResponseEntity<Selected> select(@AuthenticationPrincipal Jwt jwt,@RequestBody ShoppingPositions.Fix fix){
        positions.select(jwt.getSubject(),fix);
        LiveOffers live=pilot.getIfAvailable();
        String area=live==null?null:live.areaFor(fix);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Selected(area));
    }
    @DeleteMapping @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt){positions.clear(jwt.getSubject());}
}
