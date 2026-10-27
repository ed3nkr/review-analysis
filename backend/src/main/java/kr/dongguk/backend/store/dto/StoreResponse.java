package kr.dongguk.backend.store.dto;

import kr.dongguk.backend.store.Store;
import java.time.LocalTime;
import java.time.OffsetDateTime;

public record StoreResponse(
        Long id, String name, String category, String address, String phone,
        String placeId, String placeName,
        LocalTime slotCutoff, OffsetDateTime lastCollectedAt
) {
    public static StoreResponse from(Store s) {
        return new StoreResponse(s.getId(), s.getName(), s.getCategory(), s.getAddress(), s.getPhone(),
                s.getPlaceId(), s.getPlaceName(), s.getSlotCutoff(), s.getLastCollectedAt());
    }
}