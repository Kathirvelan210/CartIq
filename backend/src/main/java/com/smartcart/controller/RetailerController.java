package com.smartcart.controller;
import com.smartcart.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class RetailerController {
    @GetMapping("/retailers/{platform}/capabilities") public Map<String,Object> capabilities(@PathVariable String platform){if(!Set.of("blinkit","instamart","zepto").contains(platform))throw ApiException.missing();return Map.of("ordering",false,"paymentMethods",List.of());}
}
