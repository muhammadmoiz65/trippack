package com.trippack.trip;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.trippack.weather.WeatherDay;
import com.trippack.weather.WeatherSource;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OrderBy;

/**
 * A planned trip. The weather is stored with the trip once it is loaded, so
 * the external API is not called again every time the trip is opened.
 */
@Entity
public class Trip {

    @Id
    @GeneratedValue
    private Long id;

    private String destination;
    private String country;
    private double latitude;
    private double longitude;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    private TripType type;

    private Instant createdAt = Instant.now();

    @Enumerated(EnumType.STRING)
    private WeatherSource weatherSource;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "trip_weather")
    @OrderBy("date")
    private List<WeatherDay> weather = new ArrayList<>();

    protected Trip() {
        // for JPA
    }

    public Trip(String destination, String country, double latitude, double longitude,
                LocalDate startDate, LocalDate endDate, TripType type) {
        update(destination, country, latitude, longitude, startDate, endDate, type);
    }

    /** Changes the trip. The cached weather is cleared because place or dates may have changed. */
    public final void update(String destination, String country, double latitude, double longitude,
                             LocalDate startDate, LocalDate endDate, TripType type) {
        this.destination = destination;
        this.country = country;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startDate = startDate;
        this.endDate = endDate;
        this.type = type;
        this.weatherSource = null;
        this.weather.clear();
    }

    /** Number of nights, for example 3 for a trip from Monday to Thursday. */
    public int nights() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate);
    }

    public boolean hasWeather() {
        return weatherSource != null;
    }

    public void setWeather(WeatherSource source, List<WeatherDay> days) {
        this.weatherSource = source;
        this.weather.clear();
        this.weather.addAll(days);
    }

    public Long getId() { return id; }
    public String getDestination() { return destination; }
    public String getCountry() { return country; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public TripType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }
    public WeatherSource getWeatherSource() { return weatherSource; }
    public List<WeatherDay> getWeather() { return weather; }
}
