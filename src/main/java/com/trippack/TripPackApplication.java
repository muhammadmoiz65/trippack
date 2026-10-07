package com.trippack;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Entry point of TripPack, a web app that builds a packing list from the
 * destination weather, the trip length and the trip type.
 */
@SpringBootApplication
public class TripPackApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripPackApplication.class, args);
    }

    /** System clock as a bean, so tests can replace "today" with a fixed date. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
