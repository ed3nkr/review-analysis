package kr.dongguk.backend.review;

import kr.dongguk.backend.common.exception.BadRequestException;
import kr.dongguk.backend.common.exception.ExternalApiException;
import kr.dongguk.backend.review.dto.ReviewCollectResponse;
import kr.dongguk.backend.review.dto.ReviewSummaryResponse;
import kr.dongguk.backend.sales.RowError;
import kr.dongguk.backend.store.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int BATCH_SIZE = 500;
    private static final int ERRORS_IN_RESPONSE = 20;
    private static final int ERRORS_IN_JOB = 100;

    private final StoreService storeService;
    private final ReviewCollector collector;
    private final JdbcTemplate jdbc;

    /** '리뷰 불러오기' — 매출 기간에 맞춰 수집(또는 시연용 파일) → 기간 안 리뷰만 저장 → 작업 기록 */
    public ReviewCollectResponse collect(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);

        // 1) 매출 기간 (한국 시각 날짜). 매출이 없으면 리뷰 기간을 정할 수 없음
        LocalDate[] period = jdbc.queryForObject("""
                SELECT min(sold_at) AS first_sold, max(sold_at) AS last_sold FROM sales_record WHERE store_id = ?
                """, (rs, i) -> new LocalDate[]{toDate(rs.getObject("first_sold", OffsetDateTime.class)),
                toDate(rs.getObject("last_sold", OffsetDateTime.class))}, storeId);
        if (period == null || period[0] == null) {
            throw new BadRequestException("먼저 매출 CSV를 올려 주세요. 리뷰는 매출 기간에 맞춰 불러와요.");
        }
        LocalDate from = period[0], to = period[1];

        ReviewCollector.Target target = jdbc.queryForObject("""
                SELECT name, address, phone, slot_cutoff FROM store WHERE id = ?
                """, (rs, i) -> new ReviewCollector.Target(storeId, rs.getString("name"), rs.getString("address"),
                rs.getString("phone"), rs.getObject("slot_cutoff", LocalTime.class), from), storeId);

        ReviewCollector.Collected collected = collector.collect(target);

        ReviewCsvParser.Result parsed;
        try {
            parsed = collected.csvText().isBlank()
                    ? new ReviewCsvParser.Result(List.of(), List.of(), 0, null, null)
                    : ReviewCsvParser.parse(collected.csvText());
        } catch (IllegalArgumentException e) {
            throw new ExternalApiException("수집 결과를 읽지 못했어요: " + e.getMessage());
        }

        // 2) 매출 기간 안의 리뷰만 (방문일, 없으면 작성일 기준)
        List<ReviewLine> inPeriod = parsed.lines().stream()
                .filter(r -> {
                    LocalDate d = r.visitedAt() != null ? r.visitedAt() : r.writtenAt();
                    return !d.isBefore(from) && !d.isAfter(to);
                })
                .toList();
        int outOfPeriod = parsed.lines().size() - inPeriod.size();
        LocalDate first = inPeriod.stream().map(r -> r.visitedAt() != null ? r.visitedAt() : r.writtenAt())
                .min(LocalDate::compareTo).orElse(null);
        LocalDate last = inPeriod.stream().map(r -> r.visitedAt() != null ? r.visitedAt() : r.writtenAt())
                .max(LocalDate::compareTo).orElse(null);

        Long jobId = Objects.requireNonNull(jdbc.queryForObject("""
                INSERT INTO job (store_id, type, status, file_path, started_at)
                VALUES (?, 'REVIEW_COLLECT', 'RUNNING', ?, now())
                RETURNING id
                """, Long.class, storeId, collected.source()));

        int saved = save(storeId, jobId, inPeriod);

        jdbc.update("""
                UPDATE job
                SET status = 'SUCCESS', processed_count = ?, error_count = ?,
                    error_detail = CAST(? AS jsonb), period_start = ?, period_end = ?, finished_at = now()
                WHERE id = ?
                """, saved, parsed.errors().size(), RowError.toJsonArray(parsed.errors(), ERRORS_IN_JOB),
                first, last, jobId);
        jdbc.update("UPDATE store SET last_collected_at = now() WHERE id = ?", storeId);

        return new ReviewCollectResponse(jobId, collected.source(), inPeriod.size(), saved,
                inPeriod.size() - saved, outOfPeriod, parsed.errors().size(),
                parsed.errors().subList(0, Math.min(parsed.errors().size(), ERRORS_IN_RESPONSE)),
                first, last);
    }

    private static LocalDate toDate(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
    }

    private int save(Long storeId, Long jobId, List<ReviewLine> lines) {
        if (lines.isEmpty()) return 0;
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO review
                    (store_id, job_id, source, dedup_key, content, rating, written_at, visited_at,
                     visit_slot_tag, author_hash, keywords)
                VALUES (?, ?, 'NAVER_PLACE', ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (store_id, dedup_key) DO NOTHING
                """, lines, BATCH_SIZE, (ps, r) -> {
            ps.setLong(1, storeId);
            ps.setLong(2, jobId);
            ps.setString(3, r.dedupKey());
            ps.setString(4, r.content());
            if (r.rating() == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, r.rating());
            ps.setObject(6, r.writtenAt());
            if (r.visitedAt() == null) ps.setNull(7, Types.DATE);
            else ps.setObject(7, r.visitedAt());
            ps.setString(8, r.visitSlotTag());
            ps.setString(9, r.authorHash());
            ps.setArray(10, ps.getConnection().createArrayOf("text", r.keywords().toArray()));
        });
        int saved = 0;
        for (int[] batch : counts) {
            for (int c : batch) if (c > 0) saved += c;
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse summary(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);

        List<ReviewSummaryResponse.Collection> collections = jdbc.query("""
                SELECT id, file_path, processed_count, error_count, period_start, period_end, requested_at
                FROM job
                WHERE store_id = ? AND type = 'REVIEW_COLLECT' AND status = 'SUCCESS'
                ORDER BY requested_at DESC
                LIMIT 10
                """, (rs, i) -> new ReviewSummaryResponse.Collection(
                rs.getLong("id"), rs.getString("file_path"),
                rs.getInt("processed_count"), rs.getInt("error_count"),
                rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                rs.getObject("requested_at", OffsetDateTime.class)), storeId);

        List<ReviewSummaryResponse.Item> recent = jdbc.query("""
                SELECT id, content, rating, written_at, visited_at, visit_slot_tag, keywords
                FROM review
                WHERE store_id = ?
                ORDER BY coalesce(visited_at, written_at) DESC, id DESC
                LIMIT 5
                """, (rs, i) -> new ReviewSummaryResponse.Item(
                rs.getLong("id"), rs.getString("content"), rs.getObject("rating", Integer.class),
                rs.getObject("written_at", LocalDate.class), rs.getObject("visited_at", LocalDate.class),
                rs.getString("visit_slot_tag"), textArray(rs.getArray("keywords"))), storeId);

        OffsetDateTime lastCollectedAt = jdbc.queryForObject(
                "SELECT last_collected_at FROM store WHERE id = ?",
                (rs, i) -> rs.getObject("last_collected_at", OffsetDateTime.class), storeId);

        return jdbc.queryForObject("""
                SELECT count(*)                                 AS review_count,
                       count(content)                           AS with_content,
                       avg(rating)                              AS avg_rating,
                       min(coalesce(visited_at, written_at))    AS first_day,
                       max(coalesce(visited_at, written_at))    AS last_day
                FROM review
                WHERE store_id = ?
                """, (rs, i) -> {
            double avg = rs.getDouble("avg_rating");
            Double avgRating = rs.wasNull() ? null : avg;
            return new ReviewSummaryResponse(
                    rs.getLong("review_count"), rs.getLong("with_content"), avgRating,
                    rs.getObject("first_day", LocalDate.class), rs.getObject("last_day", LocalDate.class),
                    lastCollectedAt, collections, recent);
        }, storeId);
    }

    private static List<String> textArray(Array array) throws SQLException {
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }
}