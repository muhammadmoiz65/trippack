package com.trippack;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.trippack.weather.Place;
import com.trippack.weather.WeatherClient;
import com.trippack.weather.WeatherDay;

/**
 * Integration tests of the REST API with the whole Spring context and an
 * in-memory database. The Open-Meteo client is mocked, so the tests are
 * repeatable and do not need the internet.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trippack-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class TripApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WeatherClient weatherClient;

    private final LocalDate start = LocalDate.now().plusDays(2);
    private final LocalDate end = start.plusDays(3);

    private String tripJson(LocalDate from, LocalDate to) {
        return """
                {"destination":"Doha","country":"Qatar","latitude":25.29,"longitude":51.53,
                 "startDate":"%s","endDate":"%s","type":"BEACH"}""".formatted(from, to);
    }

    private long createTrip() throws Exception {
        MvcResult result = mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content(tripJson(start, end)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nights").value(3))
                .andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void placeSearchIsAnsweredByTheBackEnd() throws Exception {
        when(weatherClient.searchPlaces("Doha")).thenReturn(List.of(new Place("Doha", "Ad Dawhah", "Qatar", 25.29, 51.53)));

        mvc.perform(get("/api/places").param("q", "Doha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].country").value("Qatar"));
    }

    @Test
    void endDateBeforeStartDateIsRejected() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON).content(tripJson(end, start)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The end date must be on or after the start date"));
    }

    @Test
    void missingDestinationIsRejected() throws Exception {
        mvc.perform(post("/api/trips").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"latitude\":1,\"longitude\":1,\"startDate\":\"%s\",\"endDate\":\"%s\",\"type\":\"CITY\"}"
                                .formatted(start, end)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownTripGives404() throws Exception {
        mvc.perform(get("/api/trips/99999")).andExpect(status().isNotFound());
    }

    @Test
    void fullFlowCreateGenerateTickAddAndDelete() throws Exception {
        when(weatherClient.forecast(anyDouble(), anyDouble(), any(), any())).thenReturn(List.of(
                new WeatherDay(start, 28, 39, false, 0, 12),
                new WeatherDay(start.plusDays(1), 27, 38, true, 0, 14)));
        long id = createTrip();

        // weather is loaded through the back-end and stored with the trip
        mvc.perform(get("/api/trips/{id}/weather", id))
                .andExpect(jsonPath("$.source").value("FORECAST"))
                .andExpect(jsonPath("$.days", hasSize(2)));

        // generated list reacts to heat, rain and the beach trip type
        MvcResult generated = mvc.perform(post("/api/trips/{id}/list/generate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Sunscreen")))
                .andExpect(jsonPath("$[*].name", hasItem("Umbrella")))
                .andExpect(jsonPath("$[*].name", hasItem("Swimwear")))
                .andReturn();
        Number firstItem = JsonPath.read(generated.getResponse().getContentAsString(), "$[0].id");

        // tick an item and change its quantity
        mvc.perform(patch("/api/trips/{id}/items/{item}", id, firstItem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packed\":true,\"quantity\":2}"))
                .andExpect(jsonPath("$.packed").value(true))
                .andExpect(jsonPath("$.quantity").value(2));

        // own item survives regeneration
        mvc.perform(post("/api/trips/{id}/items", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Travel adapter\",\"category\":\"TECH\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.custom").value(true));
        mvc.perform(post("/api/trips/{id}/list/generate", id))
                .andExpect(jsonPath("$[*].name", hasItem("Travel adapter")));

        // invalid quantity is rejected
        mvc.perform(patch("/api/trips/{id}/items/{item}", id, firstItem).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isBadRequest());

        // delete the trip with its list
        mvc.perform(delete("/api/trips/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/{id}", id)).andExpect(status().isNotFound());
    }
}
