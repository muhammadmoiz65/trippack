package com.trippack.packing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** JSON shapes of the packing item API. */
public final class ItemDtos {

    private ItemDtos() { }

    /** Body of POST /api/trips/{id}/items: an item the user adds by hand. */
    public record ItemRequest(@NotBlank @Size(max = 80) String name, Category category,
                              @Min(1) @Max(99) Integer quantity) { }

    /** Body of PATCH /api/trips/{id}/items/{itemId}: only the given fields change. */
    public record ItemUpdate(Boolean packed, @Min(1) @Max(99) Integer quantity) { }

    public record ItemResponse(Long id, String name, Category category, int quantity, String reason,
                               boolean packed, boolean custom) {
        static ItemResponse of(PackingItem i) {
            return new ItemResponse(i.getId(), i.getName(), i.getCategory(), i.getQuantity(), i.getReason(),
                    i.isPacked(), i.isCustom());
        }
    }
}
