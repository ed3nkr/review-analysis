package kr.dongguk.backend.review;

import kr.dongguk.backend.sales.CsvReader;
import kr.dongguk.backend.sales.RowError;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 리뷰 CSV 텍스트 → ReviewLine 목록 + 오류 줄 목록.
 * 필수 열: 작성일 + (본문 또는 키워드). 나머지(별점·방문일·방문 시간대·작성자·리뷰ID)는 있으면 쓴다.
 */
public final class ReviewCsvParser {

    private static final Map<String, List<String>> COLUMNS = Map.of(
            "review_id", List.of("review_id", "id", "리뷰id", "리뷰번호"),
            "content", List.of("content", "review", "내용", "본문", "리뷰", "리뷰내용"),
            "rating", List.of("rating", "star", "별점", "평점"),
            "written_at", List.of("written_date", "written_at", "작성일", "작성일자"),
            "visited_at", List.of("visit_date", "visited_at", "방문일", "방문일자"),
            "visit_slot", List.of("visit_slot", "방문시간대", "방문시간"),
            "author", List.of("author_hash", "author", "작성자", "닉네임"),
            "keywords", List.of("keywords", "keyword", "키워드"));

    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy.M.d."),
            DateTimeFormatter.ofPattern("yyyy/M/d"));

    public record Result(List<ReviewLine> lines, List<RowError> errors, int totalRows,
                         LocalDate periodStart, LocalDate periodEnd) {}

    private ReviewCsvParser() {}

    public static Result parse(String text) {
        List<List<String>> rows = CsvReader.parse(text);
        if (rows.isEmpty()) throw new IllegalArgumentException("빈 파일이에요.");

        Map<String, Integer> col = mapHeader(rows.get(0));
        if (!col.containsKey("written_at") || !(col.containsKey("content") || col.containsKey("keywords"))) {
            throw new IllegalArgumentException("필요한 열을 찾지 못했어요. 첫 줄에 written_date(작성일)과 "
                    + "content(본문) 또는 keywords(키워드) 열이 있어야 해요.");
        }

        List<ReviewLine> lines = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        LocalDate start = null, end = null;
        int total = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            int fileLine = i + 1;
            if (r.stream().allMatch(String::isBlank)) continue;
            total++;
            try {
                String content = cell(r, col, "content");
                List<String> keywords = keywords(cell(r, col, "keywords"));
                if (content.isEmpty() && keywords.isEmpty()) {
                    throw new IllegalArgumentException("본문과 키워드가 모두 비어 있어요");
                }
                String writtenRaw = cell(r, col, "written_at");
                if (writtenRaw.isEmpty()) throw new IllegalArgumentException("작성일이 비어 있어요");
                LocalDate writtenAt = date(writtenRaw, "작성일");
                String visitedRaw = cell(r, col, "visited_at");
                LocalDate visitedAt = visitedRaw.isEmpty() ? null : date(visitedRaw, "방문일");
                Integer rating = rating(cell(r, col, "rating"));
                String slot = cut(cell(r, col, "visit_slot"), 20);
                String author = cell(r, col, "author");
                String authorHash = author.isEmpty() ? null : sha256(author);
                String reviewId = cell(r, col, "review_id");

                // 리뷰ID가 있으면 그걸로, 없으면 작성자·작성일·본문·키워드로 같은 리뷰인지 판단
                String dedupKey = sha256(!reviewId.isEmpty()
                        ? "id:" + reviewId
                        : "v:" + author + "|" + writtenAt + "|" + content + "|" + String.join(";", keywords));

                lines.add(new ReviewLine(fileLine, dedupKey, content.isEmpty() ? null : content, rating,
                        writtenAt, visitedAt, slot.isEmpty() ? null : slot, authorHash, keywords));

                LocalDate d = visitedAt != null ? visitedAt : writtenAt;
                if (start == null || d.isBefore(start)) start = d;
                if (end == null || d.isAfter(end)) end = d;
            } catch (IllegalArgumentException e) {
                errors.add(new RowError(fileLine, e.getMessage()));
            }
        }
        return new Result(lines, errors, total, start, end);
    }

    private static Map<String, Integer> mapHeader(List<String> header) {
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String name = header.get(i).strip().toLowerCase(Locale.ROOT).replace(" ", "");
            for (var e : COLUMNS.entrySet()) {
                if (e.getValue().contains(name)) col.putIfAbsent(e.getKey(), i);
            }
        }
        return col;
    }

    private static String cell(List<String> r, Map<String, Integer> col, String key) {
        Integer i = col.get(key);
        return (i == null || i >= r.size()) ? "" : r.get(i).strip();
    }

    private static List<String> keywords(String v) {
        if (v.isEmpty()) return List.of();
        return Arrays.stream(v.split("[;|]"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static LocalDate date(String v, String label) {
        String s = v.strip();
        int space = s.indexOf(' ');
        if (space > 0) s = s.substring(0, space);   // '2026-01-02 13:00' → 날짜만
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // 다음 형식 시도
            }
        }
        throw new IllegalArgumentException(label + " 형식을 읽을 수 없어요: " + v + " (예: 2026-01-02)");
    }

    private static Integer rating(String v) {
        if (v.isEmpty()) return null;
        try {
            int n = (int) Math.round(Double.parseDouble(v.replace("★", "").strip()));
            if (n < 1 || n > 5) throw new IllegalArgumentException("별점은 1~5여야 해요: " + v);
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("별점이 숫자가 아니에요: " + v);
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
