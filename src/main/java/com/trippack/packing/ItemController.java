package com.trippack.packing;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.trippack.packing.ItemDtos.ItemRequest;
import com.trippack.packing.ItemDtos.ItemResponse;
import com.trippack.packing.ItemDtos.ItemUpdate;

import jakarta.validation.Valid;

/** REST API for the packing list of one trip. */
@RestController
@RequestMapping("/api/trips/{tripId}")
public class ItemController {

    private final PackingService service;

    public ItemController(PackingService service) {
        this.service = service;
    }

    @GetMapping("/items")
    public List<ItemResponse> list(@PathVariable Long tripId) {
        return service.list(tripId).stream().map(ItemResponse::of).toList();
    }

    /** Builds the list from weather, trip length and trip type. Own items are kept. */
    @PostMapping("/list/generate")
    public List<ItemResponse> generate(@PathVariable Long tripId) {
        return service.generate(tripId).stream().map(ItemResponse::of).toList();
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse add(@PathVariable Long tripId, @Valid @RequestBody ItemRequest request) {
        return ItemResponse.of(service.add(tripId, request));
    }

    @PatchMapping("/items/{itemId}")
    public ItemResponse update(@PathVariable Long tripId, @PathVariable Long itemId,
                               @Valid @RequestBody ItemUpdate update) {
        return ItemResponse.of(service.update(tripId, itemId, update));
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long tripId, @PathVariable Long itemId) {
        service.delete(tripId, itemId);
    }
}
