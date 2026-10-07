package com.trippack.weather;

/** A destination found by the place search. */
public record Place(String name, String region, String country, double latitude, double longitude) {
}
