package com.trippack.weather;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Destination search for the trip form. The browser never calls Open-Meteo directly. */
@RestController
@RequestMapping("/api/places")
public class PlaceController {

    private final WeatherClient client;

    public PlaceController(WeatherClient client) {
        this.client = client;
    }

    @GetMapping
    public List<Place> search(@RequestParam String q) {
        if (q.trim().length() < 2) {
            return List.of();
        }
        try {
            return client.searchPlaces(q.trim());
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Place search is not available right now");
        }
    }
}
