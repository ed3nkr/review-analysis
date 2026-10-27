package kr.dongguk.backend.store;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreRepository extends JpaRepository<Store, Long> {
    List<Store> findByOwnerIdOrderByIdAsc(Long ownerId);
}