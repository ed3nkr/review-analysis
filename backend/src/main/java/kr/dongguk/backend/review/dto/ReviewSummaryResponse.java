package kr.dongguk.backend.review.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record ReviewSummaryResponse(
        long reviewCount,
        long withContentCount,
        Double avgRating,
        LocalDate periodStart,
        LocalDate periodEnd,
        OffsetDateTime lastCollectedAt,
        List<Collection> collections,   // 불러온 기록 (최근 10번)
        List<Item> recent               // 최근 리뷰 5건
) {
    public record Collection(Long jobId, String source, int savedRows, int errorRows,
                             LocalDate periodStart, LocalDate periodEnd, OffsetDateTime requestedAt) {}

    public record Item(Long id, String content, Integer rating, LocalDate writtenAt, LocalDate visitedAt,
                       String visitSlotTag, List<String> keywords) {}
}