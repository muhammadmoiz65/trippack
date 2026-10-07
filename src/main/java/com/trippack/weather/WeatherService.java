package com.trippack.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Decides which weather data a trip gets.
 * <ul>
 *   <li>Whole trip within the forecast range: the real forecast.</li>
 *   <li>Trip further away: the same dates one year earlier ("typical weather"),
 *       with the dates moved forward again so they match the trip.</li>
 *   <li>API error: no weather, the list is built without weather rules.</li>
 * </ul>
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherClient client;
    private final Clock clock;
    private final int forecastDays;

    public WeatherService(WeatherClient client, Clock clock,
                          @Value("${trippack.weather.forecast-days}") int forecastDays) {
        this.client = client;
        this.clock = clock;
        this.forecastDays = forecastDays;
    }

    /** Result of a weather lookup. */
    public record WeatherReport(WeatherSource source, List<WeatherDay> days) { }

    /** Chooses forecast or typical weather based on how far away the trip is. */
    public WeatherSource sourceFor(LocalDate end) {
        LocalDate lastForecastDay = LocalDate.now(clock).plusDays(forecastDays - 1L);
        return end.isAfter(lastForecastDay) ? WeatherSource.TYPICAL : WeatherSource.FORECAST;
    }

    /** Loads the weather for a trip. Never throws: errors give an UNAVAILABLE report. */
    public WeatherReport weatherFor(double lat, double lon, LocalDate start, LocalDate end) {
        WeatherSource source = sourceFor(end);
        try {
            if (source == WeatherSource.FORECAST) {
                return new WeatherReport(source, client.forecast(lat, lon, start, end));
            }
            List<WeatherDay> lastYear = client.history(lat, lon, start.minusYears(1), end.minusYears(1));
            List<WeatherDay> shifted = lastYear.stream()
                    .map(d -> new WeatherDay(d.date().plusYears(1), d.minTemp(), d.maxTemp(),
                            d.rainLikely(), d.snowCm(), d.maxWindKmh()))
                    .toList();
            return new WeatherReport(source, shifted);
        } catch (RestClientException e) {
            log.warn("Weather API not reachable: {}", e.getMessage());
            return new WeatherReport(WeatherSource.UNAVAILABLE, List.of());
        }
    }
}
