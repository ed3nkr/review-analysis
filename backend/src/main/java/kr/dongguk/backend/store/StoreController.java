package kr.dongguk.backend.store;

import jakarta.validation.Valid;
import kr.dongguk.backend.common.auth.CurrentOwner;
import kr.dongguk.backend.store.dto.StoreCreateRequest;
import kr.dongguk.backend.store.dto.StoreResponse;
import kr.dongguk.backend.store.dto.StoreUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;
    private final CurrentOwner currentOwner;

    @GetMapping
    public List<StoreResponse> list() {
        return storeService.getMyStores(currentOwner.id());
    }

    @GetMapping("/{storeId}")
    public StoreResponse get(@PathVariable Long storeId) {
        return storeService.getStore(currentOwner.id(), storeId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StoreResponse create(@Valid @RequestBody StoreCreateRequest req) {
        return storeService.create(currentOwner.id(), req);
    }

    @PatchMapping("/{storeId}")
    public StoreResponse update(@PathVariable Long storeId,
                                @Valid @RequestBody StoreUpdateRequest req) {
        return storeService.update(currentOwner.id(), storeId, req);
    }
}