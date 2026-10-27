package kr.dongguk.backend.store;

import kr.dongguk.backend.common.exception.NotFoundException;
import kr.dongguk.backend.owner.OwnerRepository;
import kr.dongguk.backend.store.dto.StoreCreateRequest;
import kr.dongguk.backend.store.dto.StoreResponse;
import kr.dongguk.backend.store.dto.StoreUpdateRequest;
import kr.dongguk.backend.common.exception.ConflictException;
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
        String name = clean(req.name());
        String address = clean(req.address());
        if (storeRepository.existsByOwnerIdAndNameAndAddress(ownerId, name, address)) {
            throw new ConflictException("이미 등록된 매장이에요: " + name);
        }
        Store store = Store.create(ownerRepository.getReferenceById(ownerId),
                name, clean(req.category()), address, clean(req.phone()), req.slotCutoff());
        return StoreResponse.from(storeRepository.save(store));
    }

    @Transactional
    public StoreResponse update(Long ownerId, Long storeId, StoreUpdateRequest req) {
        Store store = findOwnedStore(ownerId, storeId);
        String name = req.name() != null ? clean(req.name()) : store.getName();
        String address = req.address() != null ? clean(req.address()) : store.getAddress();
        if (storeRepository.existsByOwnerIdAndNameAndAddressAndIdNot(ownerId, name, address, storeId)) {
            throw new ConflictException("이미 등록된 매장이에요: " + name);
        }
        store.update(name, clean(req.category()), address, clean(req.phone()), req.slotCutoff());
        return StoreResponse.from(store);
    }

    @Transactional
    public void delete(Long ownerId, Long storeId) {
        Store store = findOwnedStore(ownerId, storeId);   // 남의 매장이면 404
        storeRepository.delete(store);
    }

    /** 다른 기능(메뉴, 업로드 등)에서도 "내 매장인지" 확인할 때 사용 */
    public Store findOwnedStore(Long ownerId, Long storeId) {
        return storeRepository.findById(storeId)
                .filter(s -> s.isOwnedBy(ownerId))
                .orElseThrow(() -> new NotFoundException("매장을 찾을 수 없습니다: " + storeId));
    }

    /** 앞뒤 공백 제거 + 연속 공백 1개로. 빈 문자열은 null */
    private static String clean(String s) {
        if (s == null) return null;
        String t = s.strip().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }
}