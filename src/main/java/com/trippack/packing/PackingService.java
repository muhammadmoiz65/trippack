package com.trippack.packing;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.trippack.packing.ItemDtos.ItemRequest;
import com.trippack.packing.ItemDtos.ItemUpdate;
import com.trippack.trip.Trip;
import com.trippack.trip.TripService;

/** Packing list logic: generate from the rules, and let the user edit the list. */
@Service
public class PackingService {

    private final PackingItemRepository items;
    private final TripService tripService;
    private final PackingRuleEngine rules;

    public PackingService(PackingItemRepository items, TripService tripService, PackingRuleEngine rules) {
        this.items = items;
        this.tripService = tripService;
        this.rules = rules;
    }

    public List<PackingItem> list(Long tripId) {
        tripService.get(tripId);
        return sorted(tripId);
    }

    /**
     * Builds the list from the rules. Generated items from earlier runs are
     * replaced, items the user added by hand are kept.
     */
    @Transactional
    public List<PackingItem> generate(Long tripId) {
        Trip trip = tripService.withWeather(tripId, false);
        items.deleteByTripIdAndCustomFalse(tripId);
        rules.generate(trip.nights(), trip.getType(), trip.getWeatherSource(), trip.getWeather())
                .forEach(s -> items.save(new PackingItem(trip, s.name(), s.category(), s.quantity(), s.reason(), false)));
        return sorted(tripId);
    }

    /** Items in category order (documents first, as defined in {@link Category}), then by name. */
    private List<PackingItem> sorted(Long tripId) {
        return items.findByTripId(tripId).stream()
                .sorted(Comparator.comparing(PackingItem::getCategory).thenComparing(PackingItem::getName))
                .toList();
    }

    @Transactional
    public PackingItem add(Long tripId, ItemRequest r) {
        Trip trip = tripService.get(tripId);
        Category category = r.category() == null ? Category.OTHER : r.category();
        int quantity = r.quantity() == null ? 1 : r.quantity();
        return items.save(new PackingItem(trip, r.name().trim(), category, quantity, "Added by you", true));
    }

    @Transactional
    public PackingItem update(Long tripId, Long itemId, ItemUpdate u) {
        PackingItem item = get(tripId, itemId);
        if (u.packed() != null) {
            item.setPacked(u.packed());
        }
        if (u.quantity() != null) {
            item.setQuantity(u.quantity());
        }
        return items.save(item);
    }

    @Transactional
    public void delete(Long tripId, Long itemId) {
        items.delete(get(tripId, itemId));
    }

    private PackingItem get(Long tripId, Long itemId) {
        return items.findByIdAndTripId(itemId, tripId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Item " + itemId + " not found"));
    }
}
