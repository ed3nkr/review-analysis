package kr.dongguk.backend.analysis.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 리포트 화면: 최근 분석 1회분 (분석한 적이 없으면 jobId = null) */
public record AnalysisReportResponse(
        Long jobId,
        OffsetDateTime analyzedAt,
        LocalDate periodStart,
        LocalDate periodEnd,
        ReviewStats reviews,
        List<Insight> insights
) {
    /** 리뷰 분석 결과 요약 (review 테이블의 분석 칸 기준) */
    public record ReviewStats(
            long total,
            long used,                          // 분석에 쓴 리뷰 (제외되지 않은 것)
            long excluded,
            Map<String, Long> excludeReasons,   // AD, DUPLICATE, LOW_RELIABILITY ...
            long positive,
            long negative,
            Map<String, Long> negTypes,         // 맛, 가격, 서비스, 위생, 대기시간, 분위기
            String modelVersion                 // klue-noise+klue-negtype+rule-v1 / rule-v1
    ) {}

    /** 연계 분석 판정 1건 */
    public record Insight(
            Long id,
            String questionCode,    // Q1 ~ Q9, R1
            String targetType,      // STORE, MENU_GROUP, SLOT, MONTH, WEEK, NEG_TYPE
            String targetId,
            String result,          // FLAGGED / NORMAL / INSUFFICIENT
            String sentence,
            Double pValue,
            String evidence,        // 근거 수치 (JSON 문자열)
            Boolean evalNew,
            Boolean evalCorrect,
            Boolean evalAction,
            String evalComment
    ) {}
}
