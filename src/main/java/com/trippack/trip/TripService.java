package com.trippack.trip;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.trippack.packing.PackingItemRepository;
import com.trippack.trip.TripDtos.TripRequest;
import com.trippack.weather.WeatherService;
import com.trippack.weather.WeatherService.WeatherReport;

/** Business logic for trips: date rules, saving, and loading the weather once. */
@Service
public class TripService {

    /** Longest trip we accept. Keeps weather requests small and lists realistic. */
    static final int MAX_NIGHTS = 30;

    private final TripRepository trips;
    private final PackingItemRepository items;
    private final WeatherService weatherService;
    private final Clock clock;

    public TripService(TripRepository trips, PackingItemRepository items, WeatherService weatherService, Clock clock) {
        this.trips = trips;
        this.items = items;
        this.weatherService = weatherService;
        this.clock = clock;
    }

    public List<Trip> findAll() {
        return trips.findAllByOrderByStartDateAsc();
    }

    public Trip get(Long id) {
        return trips.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip " + id + " not found"));
    }

    @Transactional
    public Trip create(TripRequest r) {
        checkDates(r.startDate(), r.endDate());
        return trips.save(new Trip(r.destination(), r.country(), r.latitude(), r.longitude(),
                r.startDate(), r.endDate(), r.type()));
    }

    @Transactional
    public Trip update(Long id, TripRequest r) {
        checkDates(r.startDate(), r.endDate());
        Trip trip = get(id);
        trip.update(r.destination(), r.country(), r.latitude(), r.longitude(), r.startDate(), r.endDate(), r.type());
        return trips.save(trip);
    }

    @Transactional
    public void delete(Long id) {
        Trip trip = get(id);
        items.deleteByTripId(id);
        trips.delete(trip);
    }

    /**
     * Returns the trip with its weather. The weather is loaded from the API only
     * the first time (or when {@code refresh} is true) and then stored with the trip.
     */
    @Transactional
    public Trip withWeather(Long id, boolean refresh) {
        Trip trip = get(id);
        if (!trip.hasWeather() || refresh) {
            WeatherReport report = weatherService.weatherFor(trip.getLatitude(), trip.getLongitude(),
                    trip.getStartDate(), trip.getEndDate());
            trip.setWeather(report.source(), report.days());
            trips.save(trip);
        }
        return trip;
    }

    /** Start not in the past, end not before start, at most MAX_NIGHTS nights. */
    void checkDates(LocalDate start, LocalDate end) {
        if (start.isBefore(LocalDate.now(clock))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The trip cannot start in the past");
        }
        if (end.isBefore(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date must be on or after the start date");
        }
        if (start.plusDays(MAX_NIGHTS).isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A trip can be at most " + MAX_NIGHTS + " nights");
        }
    }
}
