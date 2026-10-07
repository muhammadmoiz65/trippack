package com.trippack.weather;

/** Where the weather of a trip comes from. */
public enum WeatherSource {
    /** Real forecast, used when the whole trip is within the forecast range. */
    FORECAST,
    /** Weather of the same dates last year, used for trips further away. */
    TYPICAL,
    /** Weather could not be loaded, the list is built without weather rules. */
    UNAVAILABLE
}
