package kr.dongguk.backend.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MenuGroupRepository extends JpaRepository<MenuGroup, Long> {
    List<MenuGroup> findByStoreIdOrderByNameAsc(Long storeId);
    Optional<MenuGroup> findByIdAndStoreId(Long id, Long storeId);
    Optional<MenuGroup> findByStoreIdAndName(Long storeId, String name);
}