package com.smartcart.controller;
import com.smartcart.dto.Models.*;
import com.smartcart.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api")
public class CartController {
    private final CartService carts;private final DecisionService decisions;private final BasketOptimizer optimizer;private final WatchService watches;
    public CartController(CartService carts,DecisionService decisions,BasketOptimizer optimizer,WatchService watches){this.carts=carts;this.decisions=decisions;this.optimizer=optimizer;this.watches=watches;}
    @GetMapping("/me/cart") public List<CartItem> list(@AuthenticationPrincipal Jwt jwt){return carts.list(jwt.getSubject());}
    @PutMapping("/me/cart") @org.springframework.transaction.annotation.Transactional public CartItem save(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CartInput input){CartItem item=carts.save(jwt.getSubject(),input);if(input.preferences().monitorPrice()&&input.preferences().targetPriceMinor()!=null)watches.save(jwt.getSubject(),new WatchInput(input.productId(),input.locationId(),input.preferences().targetPriceMinor()));return item;}
    @DeleteMapping("/me/cart/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@AuthenticationPrincipal Jwt jwt,@PathVariable String id){carts.remove(jwt.getSubject(),id);}
    @DeleteMapping("/me/cart") @ResponseStatus(HttpStatus.NO_CONTENT) public void clear(@AuthenticationPrincipal Jwt jwt,@RequestParam String locationId){carts.clear(jwt.getSubject(),locationId);}
    @GetMapping("/me/decisions") public List<Decision> decisions(@AuthenticationPrincipal Jwt jwt,@RequestParam String locationId){return carts.list(jwt.getSubject()).stream().filter(i->i.locationId().equals(locationId)).map(decisions::decide).toList();}
    @PostMapping("/cart/optimize") public Optimization optimize(@Valid @RequestBody BasketRequest r){return optimizer.optimize(r);}
}
