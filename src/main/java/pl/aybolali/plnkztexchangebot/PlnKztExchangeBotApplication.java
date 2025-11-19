package pl.aybolali.plnkztexchangebot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PlnKztExchangeBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlnKztExchangeBotApplication.class, args);
    }

}
