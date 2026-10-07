package com.trippack.packing;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.trippack.trip.TripType;
import com.trippack.weather.WeatherDay;
import com.trippack.weather.WeatherSource;

/**
 * Turns trip length, trip type and weather into a packing list.
 * This is the core logic of TripPack. It has no database or web code, so it is
 * fully covered by plain unit tests (see PackingRuleEngineTest).
 */
@Component
public class PackingRuleEngine {

    static final double COLD_BELOW = 5;     // °C minimum: coat, gloves, hat
    static final double COOL_BELOW = 12;    // °C minimum: jumper
    static final double HOT_ABOVE = 25;     // °C maximum: sun protection
    static final double WINDY_ABOVE = 40;   // km/h: windbreaker
    static final int MAX_UNDERWEAR = 8;     // after that, plan one laundry day

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);

    /** One suggested line of the packing list. */
    public record Suggestion(String name, Category category, int quantity, String reason) { }

    /**
     * Builds the packing list.
     *
     * @param nights  number of nights, at least 0 (a day trip)
     * @param type    kind of trip
     * @param source  where the weather comes from
     * @param weather daily weather, may be empty if it was not available
     */
    public List<Suggestion> generate(int nights, TripType type, WeatherSource source, List<WeatherDay> weather) {
        List<Suggestion> list = new ArrayList<>();
        addBasics(list);
        addClothes(list, nights);
        addWeatherItems(list, source, weather);
        addTripTypeItems(list, type, nights);
        return list;
    }

    private void addBasics(List<Suggestion> list) {
        list.add(new Suggestion("Passport or ID card", Category.DOCUMENTS, 1, "Always needed"));
        list.add(new Suggestion("Bank card and some cash", Category.DOCUMENTS, 1, "Always needed"));
        list.add(new Suggestion("Toothbrush and toothpaste", Category.TOILETRIES, 1, "Always needed"));
        list.add(new Suggestion("Phone charger", Category.TECH, 1, "Always needed"));
    }

    private void addClothes(List<Suggestion> list, int nights) {
        int days = nights + 1;
        String stay = nights == 1 ? "1 night" : nights + " nights";
        int underwear = Math.min(days, MAX_UNDERWEAR);
        String laundry = days > MAX_UNDERWEAR ? ", plan one laundry day" : "";
        list.add(new Suggestion("Underwear", Category.CLOTHES, underwear, stay + laundry));
        list.add(new Suggestion("Socks", Category.CLOTHES, underwear, stay + laundry));
        list.add(new Suggestion("T-shirts", Category.CLOTHES, clamp((int) Math.ceil(nights * 0.8), 1, 7), stay));
        list.add(new Suggestion("Trousers", Category.CLOTHES, clamp((int) Math.ceil(nights / 3.0), 1, 3), stay));
        if (nights > 0) {
            list.add(new Suggestion("Sleepwear", Category.CLOTHES, 1, "Overnight stay"));
        }
    }

    private void addWeatherItems(List<Suggestion> list, WeatherSource source, List<WeatherDay> weather) {
        if (source == WeatherSource.UNAVAILABLE || weather.isEmpty()) {
            list.add(new Suggestion("Check the weather before you leave", Category.OTHER, 1,
                    "Weather data was not available"));
            return;
        }
        String basis = source == WeatherSource.TYPICAL ? " (typical weather)" : "";

        List<WeatherDay> rainy = weather.stream().filter(WeatherDay::rainLikely).toList();
        if (!rainy.isEmpty()) {
            String reason = "Rain likely on " + days(rainy) + basis;
            list.add(new Suggestion("Umbrella", Category.WEATHER, 1, reason));
            list.add(new Suggestion("Rain jacket", Category.WEATHER, 1, reason));
        }

        double coldest = weather.stream().mapToDouble(WeatherDay::minTemp).min().orElse(COOL_BELOW);
        double hottest = weather.stream().mapToDouble(WeatherDay::maxTemp).max().orElse(HOT_ABOVE);
        if (coldest < COLD_BELOW) {
            String reason = "Down to " + Math.round(coldest) + " °C" + basis;
            list.add(new Suggestion("Warm coat", Category.WEATHER, 1, reason));
            list.add(new Suggestion("Gloves", Category.WEATHER, 1, reason));
            list.add(new Suggestion("Hat", Category.WEATHER, 1, reason));
        } else if (coldest < COOL_BELOW) {
            list.add(new Suggestion("Jumper", Category.WEATHER, 1, "Cool evenings, down to " + Math.round(coldest) + " °C" + basis));
        }
        if (hottest > HOT_ABOVE) {
            String reason = "Up to " + Math.round(hottest) + " °C" + basis;
            list.add(new Suggestion("Sunscreen", Category.WEATHER, 1, reason));
            list.add(new Suggestion("Sunglasses", Category.WEATHER, 1, reason));
            list.add(new Suggestion("Shorts", Category.CLOTHES, 2, reason));
        }
        if (weather.stream().anyMatch(d -> d.snowCm() > 0)) {
            list.add(new Suggestion("Waterproof boots", Category.WEATHER, 1, "Snow expected" + basis));
        }
        double windiest = weather.stream().mapToDouble(WeatherDay::maxWindKmh).max().orElse(0);
        if (windiest > WINDY_ABOVE) {
            list.add(new Suggestion("Windbreaker", Category.WEATHER, 1, "Wind up to " + Math.round(windiest) + " km/h" + basis));
        }
    }

    private void addTripTypeItems(List<Suggestion> list, TripType type, int nights) {
        switch (type) {
            case BEACH -> {
                list.add(new Suggestion("Swimwear", Category.ACTIVITY, 2, "Beach trip"));
                list.add(new Suggestion("Beach towel", Category.ACTIVITY, 1, "Beach trip"));
                list.add(new Suggestion("Flip-flops", Category.ACTIVITY, 1, "Beach trip"));
            }
            case HIKING -> {
                list.add(new Suggestion("Hiking boots", Category.ACTIVITY, 1, "Hiking trip"));
                list.add(new Suggestion("First aid kit", Category.ACTIVITY, 1, "Hiking trip"));
                list.add(new Suggestion("Water bottle", Category.ACTIVITY, 1, "Hiking trip"));
            }
            case BUSINESS -> {
                list.add(new Suggestion("Formal outfit", Category.CLOTHES, clamp((int) Math.ceil(nights / 2.0), 1, 4), "Business trip"));
                list.add(new Suggestion("Laptop and charger", Category.TECH, 1, "Business trip"));
            }
            case CITY -> list.add(new Suggestion("Comfortable walking shoes", Category.CLOTHES, 1, "City trip"));
        }
    }

    /** "Tue 14 Oct, Wed 15 Oct" for up to three days, then "and 2 more days". */
    private static String days(List<WeatherDay> days) {
        List<String> names = days.stream().limit(3).map(d -> d.date().format(DAY)).toList();
        String text = String.join(", ", names);
        int more = days.size() - names.size();
        return more > 0 ? text + " and " + more + " more " + (more == 1 ? "day" : "days") : text;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
