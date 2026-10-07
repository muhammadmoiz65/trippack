package com.trippack.weather;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Talks to the free Open-Meteo APIs. Only the back-end calls these APIs. The
 * responses are converted into {@link Place} and {@link WeatherDay} so the
 * front-end never sees the external format.
 */
@Component
public class WeatherClient {

    /** Rain counts as likely from this forecast probability (percent). */
    static final int RAIN_PROBABILITY_THRESHOLD = 50;
    /** Rain counts as likely from this amount in the archive (mm per day). */
    static final double RAIN_MM_THRESHOLD = 1.0;

    private final RestClient geocoding;
    private final RestClient forecast;
    private final RestClient archive;

    public WeatherClient(@Value("${trippack.weather.geocoding-url}") String geocodingUrl,
                         @Value("${trippack.weather.forecast-url}") String forecastUrl,
                         @Value("${trippack.weather.archive-url}") String archiveUrl) {
        this.geocoding = RestClient.create(geocodingUrl);
        this.forecast = RestClient.create(forecastUrl);
        this.archive = RestClient.create(archiveUrl);
    }

    /** Searches places by name, for the destination field with suggestions. */
    public List<Place> searchPlaces(String query) {
        GeocodingResponse response = geocoding.get()
                .uri(b -> b.path("/search").queryParam("name", query).queryParam("count", 6)
                        .queryParam("language", "en").queryParam("format", "json").build())
                .retrieve().body(GeocodingResponse.class);
        if (response == null || response.results() == null) {
            return List.of();
        }
        return response.results().stream()
                .map(r -> new Place(r.name(), r.admin1(), r.country(), r.latitude(), r.longitude()))
                .toList();
    }

    /** Daily forecast for dates within the next 16 days. */
    public List<WeatherDay> forecast(double lat, double lon, LocalDate from, LocalDate to) {
        DailyResponse response = forecast.get()
                .uri(b -> b.path("/forecast").queryParam("latitude", lat).queryParam("longitude", lon)
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                                + "snowfall_sum,wind_speed_10m_max")
                        .queryParam("timezone", "auto")
                        .queryParam("start_date", from).queryParam("end_date", to).build())
                .retrieve().body(DailyResponse.class);
        return toDays(response, true);
    }

    /** Measured daily weather of past dates, used as "typical weather". */
    public List<WeatherDay> history(double lat, double lon, LocalDate from, LocalDate to) {
        DailyResponse response = archive.get()
                .uri(b -> b.path("/archive").queryParam("latitude", lat).queryParam("longitude", lon)
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum,"
                                + "snowfall_sum,wind_speed_10m_max")
                        .queryParam("timezone", "auto")
                        .queryParam("start_date", from).queryParam("end_date", to).build())
                .retrieve().body(DailyResponse.class);
        return toDays(response, false);
    }

    /** Converts the column-wise Open-Meteo response into one object per day. */
    static List<WeatherDay> toDays(DailyResponse response, boolean isForecast) {
        List<WeatherDay> days = new ArrayList<>();
        if (response == null || response.daily() == null || response.daily().time() == null) {
            return days;
        }
        Daily d = response.daily();
        for (int i = 0; i < d.time().size(); i++) {
            boolean rain = isForecast
                    ? value(d.precipitation_probability_max(), i) >= RAIN_PROBABILITY_THRESHOLD
                    : value(d.precipitation_sum(), i) >= RAIN_MM_THRESHOLD;
            days.add(new WeatherDay(d.time().get(i), value(d.temperature_2m_min(), i),
                    value(d.temperature_2m_max(), i), rain, value(d.snowfall_sum(), i),
                    value(d.wind_speed_10m_max(), i)));
        }
        return days;
    }

    /** Open-Meteo returns null for missing values, which we treat as 0. */
    private static double value(List<Double> list, int i) {
        return list == null || list.get(i) == null ? 0 : list.get(i);
    }

    // Shapes of the Open-Meteo JSON responses (only the fields we use).
    record GeocodingResponse(List<GeocodingResult> results) { }

    record GeocodingResult(String name, String admin1, String country, double latitude, double longitude) { }

    record DailyResponse(Daily daily) { }

    record Daily(List<LocalDate> time, List<Double> temperature_2m_max, List<Double> temperature_2m_min,
                 List<Double> precipitation_probability_max, List<Double> precipitation_sum,
                 List<Double> snowfall_sum, List<Double> wind_speed_10m_max) { }
}
