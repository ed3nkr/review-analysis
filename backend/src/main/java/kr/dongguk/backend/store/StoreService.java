package kr.dongguk.backend.store;

import kr.dongguk.backend.common.exception.NotFoundException;
import kr.dongguk.backend.owner.OwnerRepository;
import kr.dongguk.backend.store.dto.StoreCreateRequest;
import kr.dongguk.backend.store.dto.StoreResponse;
import kr.dongguk.backend.store.dto.StoreUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final OwnerRepository ownerRepository;

    public List<StoreResponse> getMyStores(Long ownerId) {
        return storeRepository.findByOwnerIdOrderByIdAsc(ownerId).stream()
                .map(StoreResponse::from)
                .toList();
    }

    public StoreResponse getStore(Long ownerId, Long storeId) {
        return StoreResponse.from(findOwnedStore(ownerId, storeId));
    }

    @Transactional
    public StoreResponse create(Long ownerId, StoreCreateRequest req) {
        Store store = Store.create(ownerRepository.getReferenceById(ownerId),
                req.name(), req.category(), req.address(), req.phone(), req.slotCutoff());
        return StoreResponse.from(storeRepository.save(store));
    }

    @Transactional
    public StoreResponse update(Long ownerId, Long storeId, StoreUpdateRequest req) {
        Store store = findOwnedStore(ownerId, storeId);
        store.update(req.name(), req.category(), req.address(), req.phone(), req.slotCutoff());
        return StoreResponse.from(store);
    }

    /** 다른 기능(메뉴, 업로드 등)에서도 "내 매장인지" 확인할 때 사용 */
    public Store findOwnedStore(Long ownerId, Long storeId) {
        return storeRepository.findById(storeId)
                .filter(s -> s.isOwnedBy(ownerId))
                .orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다: " + storeId));
    }
}