package com.trippack.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import com.trippack.weather.WeatherService.WeatherReport;

/** Tests the choice between forecast and typical weather, with "today" fixed to 7 Oct 2026. */
class WeatherServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private final Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private final WeatherClient client = mock(WeatherClient.class);
    private final WeatherService service = new WeatherService(client, clock, 16);

    @Test
    void tripInsideTheForecastRangeUsesTheForecast() {
        assertThat(service.sourceFor(TODAY.plusDays(15))).isEqualTo(WeatherSource.FORECAST);
    }

    @Test
    void tripEndingAfterTheForecastRangeUsesTypicalWeather() {
        assertThat(service.sourceFor(TODAY.plusDays(16))).isEqualTo(WeatherSource.TYPICAL);
    }

    @Test
    void typicalWeatherUsesLastYearAndMovesTheDatesForward() {
        LocalDate start = LocalDate.of(2026, 12, 6);
        LocalDate end = LocalDate.of(2026, 12, 7);
        when(client.history(anyDouble(), anyDouble(), any(), any())).thenReturn(List.of(
                new WeatherDay(LocalDate.of(2025, 12, 6), 1, 4, true, 0, 10),
                new WeatherDay(LocalDate.of(2025, 12, 7), 0, 3, false, 0, 12)));

        WeatherReport report = service.weatherFor(59.9, 10.7, start, end);

        verify(client).history(59.9, 10.7, LocalDate.of(2025, 12, 6), LocalDate.of(2025, 12, 7));
        assertThat(report.source()).isEqualTo(WeatherSource.TYPICAL);
        assertThat(report.days()).extracting(WeatherDay::date).containsExactly(start, end);
    }

    @Test
    void apiErrorGivesAnUnavailableReportInsteadOfAnException() {
        when(client.forecast(anyDouble(), anyDouble(), any(), any()))
                .thenThrow(new ResourceAccessException("timeout"));

        WeatherReport report = service.weatherFor(25.3, 51.5, TODAY.plusDays(1), TODAY.plusDays(3));

        assertThat(report.source()).isEqualTo(WeatherSource.UNAVAILABLE);
        assertThat(report.days()).isEmpty();
    }
}
