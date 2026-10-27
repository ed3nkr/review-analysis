package kr.dongguk.backend.owner;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OwnerRepository extends JpaRepository<Owner, Long> {
    Optional<Owner> findByProviderAndProviderUserId(String provider, String providerUserId);
}