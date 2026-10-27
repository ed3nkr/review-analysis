package kr.dongguk.backend.store;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {
    List<Store> findByOwnerIdOrderByIdAsc(Long ownerId);

    boolean existsByOwnerIdAndNameAndAddress(Long ownerId, String name, String address);

    // 수정할 때: 자기 자신은 빼고 확인
    boolean existsByOwnerIdAndNameAndAddressAndIdNot(Long ownerId, String name, String address, Long id);
}