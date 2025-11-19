/* package pl.aybolali.plnkztexchangebot.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.service.RedisService;

import java.time.Duration;

@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class RedisTestController {

    private final RedisService redisService;

    @PostMapping("/redis/{key}")
    public String saveToRedis(@PathVariable String key, @RequestBody String value) {
        redisService.setValue(key, value, Duration.ofMinutes(5));
        return "Saved: " + key + " = " + value;
    }

    @GetMapping("/redis/{key}")
    public String getFromRedis(@PathVariable String key) {
        String value = redisService.getValue(key);
        return "Value: " + (value != null ? value : "not found");
    }

    @PostMapping("/redis/counter/{key}")
    public String incrementCounter(@PathVariable String key) {
        Long newValue = redisService.increment(key);
        return "Counter: " + key + " = " + newValue;
    }
}

*/
