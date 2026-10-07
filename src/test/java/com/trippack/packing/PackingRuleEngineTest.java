package com.trippack.packing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.trippack.packing.PackingRuleEngine.Suggestion;
import com.trippack.trip.TripType;
import com.trippack.weather.WeatherDay;
import com.trippack.weather.WeatherSource;

/** Unit tests for the packing rules. Each test describes one rule with its own test data. */
class PackingRuleEngineTest {

    private final PackingRuleEngine engine = new PackingRuleEngine();

    /** A mild, dry day with no special weather. */
    private static WeatherDay mild(int dayOfMonth) {
        return new WeatherDay(LocalDate.of(2026, 10, dayOfMonth), 14, 20, false, 0, 10);
    }

    private static Optional<Suggestion> find(List<Suggestion> list, String name) {
        return list.stream().filter(s -> s.name().equals(name)).findFirst();
    }

    @Test
    void basicsAreAlwaysOnTheList() {
        List<Suggestion> list = engine.generate(3, TripType.CITY, WeatherSource.FORECAST, List.of(mild(1)));
        assertThat(list).extracting(Suggestion::name)
                .contains("Passport or ID card", "Phone charger", "Toothbrush and toothpaste");
    }

    @Test
    void underwearIsOnePerDayPlusOne() {
        List<Suggestion> list = engine.generate(4, TripType.CITY, WeatherSource.FORECAST, List.of(mild(1)));
        assertThat(find(list, "Underwear")).get().extracting(Suggestion::quantity).isEqualTo(5);
        assertThat(find(list, "Socks")).get().extracting(Suggestion::quantity).isEqualTo(5);
    }

    @Test
    void longTripsAreCappedAndSuggestLaundry() {
        List<Suggestion> list = engine.generate(14, TripType.CITY, WeatherSource.FORECAST, List.of(mild(1)));
        Suggestion underwear = find(list, "Underwear").orElseThrow();
        assertThat(underwear.quantity()).isEqualTo(PackingRuleEngine.MAX_UNDERWEAR);
        assertThat(underwear.reason()).contains("laundry");
        assertThat(find(list, "T-shirts")).get().extracting(Suggestion::quantity).isEqualTo(7);
    }

    @Test
    void dayTripHasNoSleepwearAndAtLeastOneOfEach() {
        List<Suggestion> list = engine.generate(0, TripType.CITY, WeatherSource.FORECAST, List.of(mild(1)));
        assertThat(find(list, "Sleepwear")).isEmpty();
        assertThat(find(list, "T-shirts")).get().extracting(Suggestion::quantity).isEqualTo(1);
    }

    @Test
    void rainAddsUmbrellaAndNamesTheRainyDays() {
        WeatherDay rainy = new WeatherDay(LocalDate.of(2026, 10, 13), 12, 18, true, 0, 15);
        List<Suggestion> list = engine.generate(2, TripType.CITY, WeatherSource.FORECAST, List.of(mild(12), rainy));
        assertThat(find(list, "Umbrella")).get().extracting(Suggestion::reason)
                .isEqualTo("Rain likely on Tue 13 Oct");
        assertThat(find(list, "Rain jacket")).isPresent();
    }

    @Test
    void dryWeatherHasNoUmbrella() {
        List<Suggestion> list = engine.generate(2, TripType.CITY, WeatherSource.FORECAST, List.of(mild(1), mild(2)));
        assertThat(find(list, "Umbrella")).isEmpty();
    }

    @Test
    void coldWeatherAddsWinterClothes() {
        WeatherDay cold = new WeatherDay(LocalDate.of(2026, 12, 6), -2, 3, false, 0, 10);
        List<Suggestion> list = engine.generate(3, TripType.CITY, WeatherSource.FORECAST, List.of(cold));
        assertThat(list).extracting(Suggestion::name).contains("Warm coat", "Gloves", "Hat");
        assertThat(find(list, "Warm coat")).get().extracting(Suggestion::reason).isEqualTo("Down to -2 °C");
        assertThat(find(list, "Jumper")).isEmpty();
    }

    @Test
    void coolEveningsAddOnlyAJumper() {
        WeatherDay cool = new WeatherDay(LocalDate.of(2026, 10, 1), 8, 16, false, 0, 10);
        List<Suggestion> list = engine.generate(3, TripType.CITY, WeatherSource.FORECAST, List.of(cool));
        assertThat(find(list, "Jumper")).isPresent();
        assertThat(find(list, "Warm coat")).isEmpty();
    }

    @Test
    void hotWeatherAddsSunProtection() {
        WeatherDay hot = new WeatherDay(LocalDate.of(2026, 10, 10), 28, 39.6, false, 0, 12);
        List<Suggestion> list = engine.generate(4, TripType.CITY, WeatherSource.FORECAST, List.of(hot));
        assertThat(list).extracting(Suggestion::name).contains("Sunscreen", "Sunglasses", "Shorts");
        assertThat(find(list, "Sunscreen")).get().extracting(Suggestion::reason).isEqualTo("Up to 40 °C");
    }

    @Test
    void snowAndStrongWindAddTheirItems() {
        WeatherDay stormy = new WeatherDay(LocalDate.of(2026, 1, 5), -5, 0, false, 4, 55);
        List<Suggestion> list = engine.generate(2, TripType.HIKING, WeatherSource.FORECAST, List.of(stormy));
        assertThat(list).extracting(Suggestion::name).contains("Waterproof boots", "Windbreaker");
    }

    @Test
    void typicalWeatherIsMarkedInTheReason() {
        WeatherDay rainy = new WeatherDay(LocalDate.of(2026, 12, 7), 2, 4, true, 0, 20);
        List<Suggestion> list = engine.generate(3, TripType.CITY, WeatherSource.TYPICAL, List.of(rainy));
        assertThat(find(list, "Umbrella")).get().extracting(Suggestion::reason).asString().endsWith("(typical weather)");
    }

    @Test
    void missingWeatherGivesAReminderInsteadOfWeatherItems() {
        List<Suggestion> list = engine.generate(3, TripType.CITY, WeatherSource.UNAVAILABLE, List.of());
        assertThat(find(list, "Check the weather before you leave")).isPresent();
        assertThat(list).noneMatch(s -> s.category() == Category.WEATHER);
    }

    @Test
    void eachTripTypeAddsItsOwnItems() {
        List<WeatherDay> w = List.of(mild(1));
        assertThat(engine.generate(3, TripType.BEACH, WeatherSource.FORECAST, w)).extracting(Suggestion::name)
                .contains("Swimwear", "Beach towel");
        assertThat(engine.generate(3, TripType.HIKING, WeatherSource.FORECAST, w)).extracting(Suggestion::name)
                .contains("Hiking boots", "First aid kit");
        assertThat(engine.generate(3, TripType.BUSINESS, WeatherSource.FORECAST, w)).extracting(Suggestion::name)
                .contains("Formal outfit", "Laptop and charger");
        assertThat(engine.generate(3, TripType.CITY, WeatherSource.FORECAST, w)).extracting(Suggestion::name)
                .contains("Comfortable walking shoes");
    }
}
