package kr.dongguk.backend.review.dto;

import kr.dongguk.backend.sales.RowError;

import java.time.LocalDate;
import java.util.List;

public record ReviewCollectResponse(
        Long jobId,
        String source,          // NAVER_PLACE(실제 수집) / DUMMY(시연용 데이터)
        int totalRows,          // 매출 기간 안의 리뷰 수
        int savedRows,          // 새로 저장
        int duplicateRows,      // 이미 있어서 건너뜀
        int outOfPeriodRows,    // 매출 기간 밖이라 저장하지 않은 리뷰
        int errorRows,
        List<RowError> errors,
        LocalDate periodStart,
        LocalDate periodEnd
) {}