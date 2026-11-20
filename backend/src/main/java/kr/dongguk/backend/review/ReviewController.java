package kr.dongguk.backend.review;

import kr.dongguk.backend.common.auth.CurrentOwner;
import kr.dongguk.backend.review.dto.ReviewCollectResponse;
import kr.dongguk.backend.review.dto.ReviewSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stores/{storeId}/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentOwner currentOwner;

    @PostMapping("/collect")
    public ReviewCollectResponse collect(@PathVariable Long storeId) {
        return reviewService.collect(currentOwner.id(), storeId);
    }

    @GetMapping("/summary")
    public ReviewSummaryResponse summary(@PathVariable Long storeId) {
        return reviewService.summary(currentOwner.id(), storeId);
    }
}