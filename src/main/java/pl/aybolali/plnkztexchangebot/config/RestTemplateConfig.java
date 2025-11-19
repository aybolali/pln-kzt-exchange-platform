package pl.aybolali.plnkztexchangebot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@Slf4j
public class RestTemplateConfig {

    @Value("${app.currency-api.timeout-seconds:5}")
    private int timeoutSeconds;

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        log.info("Creating RestTemplate with timeout: {}s", timeoutSeconds);

        return builder
                .requestFactory(() -> {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
                    factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
                    return factory;
                })
                .build();
    }
}