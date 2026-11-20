package kr.dongguk.backend.sales.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record SalesSummaryResponse(
        long lineCount,
        long billCount,
        LocalDate periodStart,
        LocalDate periodEnd,
        long normalAmount,   // 반품 제외 매출 합계
        List<Upload> uploads
) {
    public record Upload(Long jobId, String fileName, int savedRows, int errorRows,
                         LocalDate periodStart, LocalDate periodEnd, OffsetDateTime requestedAt) {}
}