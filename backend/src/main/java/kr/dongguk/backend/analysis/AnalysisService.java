package kr.dongguk.backend.analysis;

import kr.dongguk.backend.analysis.dto.AnalysisReportResponse;
import kr.dongguk.backend.analysis.dto.EvaluationRequest;
import kr.dongguk.backend.common.exception.BadRequestException;
import kr.dongguk.backend.common.exception.ExternalApiException;
import kr.dongguk.backend.store.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private final StoreService storeService;
    private final AnalysisClient client;
    private final JdbcTemplate jdbc;

    /**
     * '분석하기' — 작업(job) 기록 → 분석 서버 호출(리뷰 분석 + 연계 분석, 결과는 분석 서버가 DB에 저장) → 작업 완료.
     * 분석 서버가 1~3분 걸릴 수 있어 트랜잭션으로 묶지 않음 (그동안 DB 연결을 붙잡지 않도록)
     */
    public AnalysisReportResponse run(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);

        LocalDate[] period = jdbc.queryForObject("""
                SELECT min(sold_at) AS first_sold, max(sold_at) AS last_sold FROM sales_record WHERE store_id = ?
                """, (rs, i) -> new LocalDate[]{toDate(rs.getObject("first_sold", OffsetDateTime.class)),
                toDate(rs.getObject("last_sold", OffsetDateTime.class))}, storeId);
        if (period == null || period[0] == null) {
            throw new BadRequestException("매출이 없어요. 매출 CSV를 먼저 올려 주세요.");
        }
        Long reviews = jdbc.queryForObject("SELECT count(*) FROM review WHERE store_id = ?", Long.class, storeId);
        if (reviews == null || reviews == 0) {
            throw new BadRequestException("리뷰가 없어요. 리뷰를 먼저 불러와 주세요.");
        }

        Long jobId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO job (store_id, type, status, period_start, period_end, started_at)
                VALUES (?, 'LINK_ANALYSIS', 'RUNNING', ?, ?, now())
                RETURNING id
                """, Long.class, storeId, period[0], period[1]));

        try {
            Map<String, Object> result = client.analyze(storeId, jobId);
            int insights = result != null && result.get("insights") instanceof Number n ? n.intValue() : 0;
            jdbc.update("""
                    UPDATE job SET status = 'SUCCESS', processed_count = ?, finished_at = now() WHERE id = ?
                    """, insights, jobId);
        } catch (ExternalApiException e) {
            jdbc.update("""
                    UPDATE job SET status = 'FAILED', error_detail = CAST(? AS jsonb), finished_at = now() WHERE id = ?
                    """, "{\"message\": " + jsonString(e.getMessage()) + "}", jobId);
            throw e;
        }
        return latest(ownerId, storeId);
    }

    /** 리포트 화면용: 가장 최근에 성공한 분석 */
    @Transactional(readOnly = true)
    public AnalysisReportResponse latest(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);

        List<Object[]> jobs = jdbc.query("""
                SELECT id, period_start, period_end, finished_at FROM job
                WHERE store_id = ? AND type = 'LINK_ANALYSIS' AND status = 'SUCCESS'
                ORDER BY id DESC LIMIT 1
                """, (rs, i) -> new Object[]{rs.getLong("id"), rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class), rs.getObject("finished_at", OffsetDateTime.class)}, storeId);
        if (jobs.isEmpty()) {
            return new AnalysisReportResponse(null, null, null, null, null, List.of());
        }
        Object[] job = jobs.get(0);
        Long jobId = (Long) job[0];

        List<AnalysisReportResponse.Insight> insights = jdbc.query("""
                SELECT id, question_code, target_type, target_id, result, sentence, p_value, evidence::text AS evidence,
                       eval_new, eval_correct, eval_action, eval_comment
                FROM insight
                WHERE store_id = ? AND job_id = ?
                ORDER BY question_code, id
                """, (rs, i) -> new AnalysisReportResponse.Insight(
                rs.getLong("id"), rs.getString("question_code"), rs.getString("target_type"), rs.getString("target_id"),
                rs.getString("result"), rs.getString("sentence"), rs.getObject("p_value", Double.class),
                rs.getString("evidence"), rs.getObject("eval_new", Boolean.class), rs.getObject("eval_correct", Boolean.class),
                rs.getObject("eval_action", Boolean.class), rs.getString("eval_comment")), storeId, jobId);

        return new AnalysisReportResponse(jobId, (OffsetDateTime) job[3], (LocalDate) job[1], (LocalDate) job[2],
                reviewStats(storeId), insights);
    }

    /** 점주 평가 저장 */
    @Transactional
    public void evaluate(Long ownerId, Long storeId, Long insightId, EvaluationRequest req) {
        storeService.findOwnedStore(ownerId, storeId);
        String comment = req.evalComment() == null ? null : req.evalComment().strip();
        if (comment != null && comment.length() > 500) {
            throw new BadRequestException("의견은 500자 이하로 써 주세요.");
        }
        int updated = jdbc.update("""
                UPDATE insight
                SET eval_new = ?, eval_correct = ?, eval_action = ?, eval_comment = ?, evaluated_at = now()
                WHERE id = ? AND store_id = ?
                """, req.evalNew(), req.evalCorrect(), req.evalAction(),
                comment == null || comment.isEmpty() ? null : comment, insightId, storeId);
        if (updated == 0) {
            throw new BadRequestException("평가할 분석 결과를 찾지 못했어요.");
        }
    }

    private AnalysisReportResponse.ReviewStats reviewStats(Long storeId) {
        Object[] base = jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE excluded) AS excluded,
                       count(*) FILTER (WHERE sentiment = 'POSITIVE') AS positive,
                       count(*) FILTER (WHERE sentiment = 'NEGATIVE') AS negative,
                       max(model_version) AS model_version
                FROM review WHERE store_id = ?
                """, (rs, i) -> new Object[]{rs.getLong("total"), rs.getLong("excluded"), rs.getLong("positive"),
                rs.getLong("negative"), rs.getString("model_version")}, storeId);
        Map<String, Long> reasons = new LinkedHashMap<>();
        jdbc.query("""
                SELECT exclude_reason, count(*) AS n FROM review
                WHERE store_id = ? AND excluded GROUP BY exclude_reason ORDER BY n DESC
                """, (rs, i) -> reasons.put(rs.getString("exclude_reason"), rs.getLong("n")), storeId);
        Map<String, Long> types = new LinkedHashMap<>();
        jdbc.query("""
                SELECT t, count(*) AS n FROM review, unnest(neg_types) AS t
                WHERE store_id = ? AND NOT excluded GROUP BY t ORDER BY n DESC
                """, (rs, i) -> types.put(rs.getString("t"), rs.getLong("n")), storeId);

        long total = (Long) Objects.requireNonNull(base)[0];
        long excluded = (Long) base[1];
        return new AnalysisReportResponse.ReviewStats(total, total - excluded, excluded, reasons,
                (Long) base[2], (Long) base[3], types, (String) base[4]);
    }

    private static LocalDate toDate(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ZONE).toLocalDate();
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
