package kr.dongguk.backend.store.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record StoreUpdateRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 50) String category,
        @Size(max = 255) String address,
        @Size(max = 20) String phone,
        LocalTime slotCutoff
) {}