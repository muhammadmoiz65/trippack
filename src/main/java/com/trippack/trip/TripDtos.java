package com.trippack.trip;

import java.time.LocalDate;
import java.util.List;

import com.trippack.weather.WeatherDay;
import com.trippack.weather.WeatherSource;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON shapes of the trip API. Kept separate from the entity on purpose. */
public final class TripDtos {

    private TripDtos() { }

    /** Body of POST and PUT /api/trips. Date rules are checked in TripService. */
    public record TripRequest(
            @NotBlank @Size(max = 100) String destination,
            @Size(max = 100) String country,
            @DecimalMin("-90") @DecimalMax("90") double latitude,
            @DecimalMin("-180") @DecimalMax("180") double longitude,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull TripType type) { }

    public record TripResponse(Long id, String destination, String country, double latitude, double longitude,
                               LocalDate startDate, LocalDate endDate, int nights, TripType type,
                               WeatherSource weatherSource) {
        static TripResponse of(Trip t) {
            return new TripResponse(t.getId(), t.getDestination(), t.getCountry(), t.getLatitude(),
                    t.getLongitude(), t.getStartDate(), t.getEndDate(), t.nights(), t.getType(),
                    t.getWeatherSource());
        }
    }

    public record WeatherResponse(WeatherSource source, List<WeatherDay> days) { }
}
