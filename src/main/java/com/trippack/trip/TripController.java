package com.trippack.trip;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trippack.trip.TripDtos.TripRequest;
import com.trippack.trip.TripDtos.TripResponse;
import com.trippack.trip.TripDtos.WeatherResponse;

import jakarta.validation.Valid;

/** REST API for trips and their weather. */
@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    public List<TripResponse> list() {
        return service.findAll().stream().map(TripResponse::of).toList();
    }

    @GetMapping("/{id}")
    public TripResponse get(@PathVariable Long id) {
        return TripResponse.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@Valid @RequestBody TripRequest request) {
        return TripResponse.of(service.create(request));
    }

    @PutMapping("/{id}")
    public TripResponse update(@PathVariable Long id, @Valid @RequestBody TripRequest request) {
        return TripResponse.of(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    /** Weather for the trip. Loaded from Open-Meteo once, then served from the database. */
    @GetMapping("/{id}/weather")
    public WeatherResponse weather(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean refresh) {
        Trip trip = service.withWeather(id, refresh);
        return new WeatherResponse(trip.getWeatherSource(), trip.getWeather());
    }
}
