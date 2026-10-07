package com.trippack.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.trippack.weather.WeatherClient.Daily;
import com.trippack.weather.WeatherClient.DailyResponse;

/** Tests how Open-Meteo's column data is turned into one WeatherDay per day. */
class WeatherClientTest {

    private static final List<LocalDate> DAYS = List.of(LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 10));

    @Test
    void forecastRainIsLikelyFromFiftyPercent() {
        Daily daily = new Daily(DAYS, List.of(20.0, 21.0), List.of(10.0, 11.0), List.of(50.0, 49.0), null,
                List.of(0.0, 0.0), List.of(10.0, 12.0));

        List<WeatherDay> days = WeatherClient.toDays(new DailyResponse(daily), true);

        assertThat(days).extracting(WeatherDay::rainLikely).containsExactly(true, false);
        assertThat(days.get(0).maxTemp()).isEqualTo(20.0);
    }

    @Test
    void archiveRainIsLikelyFromOneMillimetre() {
        Daily daily = new Daily(DAYS, List.of(8.0, 9.0), List.of(2.0, 3.0), null, List.of(1.0, 0.4),
                List.of(0.0, 0.0), List.of(10.0, 12.0));

        assertThat(WeatherClient.toDays(new DailyResponse(daily), false))
                .extracting(WeatherDay::rainLikely).containsExactly(true, false);
    }

    @Test
    void missingValuesCountAsZeroAndEmptyResponsesGiveNoDays() {
        Daily daily = new Daily(DAYS, Arrays.asList(20.0, null), List.of(10.0, 11.0), null, null, null, null);

        List<WeatherDay> days = WeatherClient.toDays(new DailyResponse(daily), true);

        assertThat(days.get(1).maxTemp()).isZero();
        assertThat(WeatherClient.toDays(null, true)).isEmpty();
    }
}
