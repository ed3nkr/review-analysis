package kr.dongguk.backend.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record StoreCreateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 50) String category,
        @Size(max = 255) String address,
        @Size(max = 20) String phone,
        LocalTime slotCutoff
) {}