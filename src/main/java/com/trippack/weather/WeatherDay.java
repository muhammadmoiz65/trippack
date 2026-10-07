package com.trippack.weather;

import java.time.LocalDate;

import jakarta.persistence.Embeddable;

/**
 * Weather of one day in the simplified form the app needs. Forecast and
 * archive data from Open-Meteo are both converted into this shape.
 *
 * @param date       the day
 * @param minTemp    lowest temperature in °C
 * @param maxTemp    highest temperature in °C
 * @param rainLikely true if rain is likely (forecast chance of at least 50 percent,
 *                   or at least 1 mm of rain in the archive)
 * @param snowCm     snowfall in cm
 * @param maxWindKmh strongest wind in km/h
 */
@Embeddable
public record WeatherDay(LocalDate date, double minTemp, double maxTemp, boolean rainLikely,
                         double snowCm, double maxWindKmh) {
}
