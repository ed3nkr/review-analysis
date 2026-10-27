package kr.dongguk.backend.common.auth;

import kr.dongguk.backend.owner.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CurrentOwner {

    private final OwnerRepository ownerRepository;

    /** TODO(9단계): 네이버 로그인 후 JWT에서 점주 id를 꺼내도록 교체 */
    public Long id() {
        return ownerRepository.findByProviderAndProviderUserId("DEV", "dev-owner")
                .orElseThrow(() -> new IllegalStateException("개발용 점주(DEV) 데이터가 없습니다"))
                .getId();
    }
}