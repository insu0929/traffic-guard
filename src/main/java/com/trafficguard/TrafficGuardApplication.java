package com.trafficguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.google.gson.Gson;

@SpringBootApplication
public class TrafficGuardApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrafficGuardApplication.class, args);
    }

    @Bean
    public Gson fulfillmentGson() {
        return new Gson();
    }
}



