package kr.dongguk.backend.sales;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 매출 CSV 텍스트 → SalesLine 목록 + 오류 줄 목록.
 * 열 이름은 영어/한글 둘 다 받고, 순서는 상관없다. 필요 없는 열(결제수단 등)은 무시한다.
 */
public final class SalesCsvParser {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 필요한 열 → 허용하는 열 이름들 (소문자로 비교) */
    private static final Map<String, List<String>> COLUMNS = Map.of(
            "sold_at", List.of("sold_at", "판매일시", "결제일시", "주문일시", "판매시각"),
            "bill_no", List.of("bill_no", "영수증번호", "주문번호", "전표번호"),
            "menu", List.of("menu", "menu_name", "메뉴", "메뉴명", "상품명"),
            "quantity", List.of("quantity", "qty", "수량"),
            "amount", List.of("amount", "금액", "판매금액", "결제금액"),
            "discount", List.of("discount", "할인", "할인금액"),
            "status", List.of("status", "상태", "구분"));
    private static final List<String> REQUIRED = List.of("sold_at", "bill_no", "menu", "quantity", "amount");
    private static final String REQUIRED_LABEL = "sold_at(판매일시), bill_no(영수증번호), menu(메뉴명), quantity(수량), amount(금액)";

    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm[:ss]"));

    public record Result(List<SalesLine> lines, List<RowError> errors, int totalRows,
                         LocalDate periodStart, LocalDate periodEnd) {}

    private SalesCsvParser() {}

    /** 열 이름이 맞지 않으면 IllegalArgumentException (메시지는 점주에게 그대로 보여 줄 문장) */
    public static Result parse(String text) {
        List<List<String>> rows = CsvReader.parse(text);
        if (rows.isEmpty()) throw new IllegalArgumentException("빈 파일이에요.");

        Map<String, Integer> col = mapHeader(rows.get(0));
        List<String> missing = REQUIRED.stream().filter(k -> !col.containsKey(k)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("필요한 열을 찾지 못했어요: " + String.join(", ", missing)
                    + ". 첫 줄에 " + REQUIRED_LABEL + " 열이 있어야 해요.");
        }

        List<SalesLine> lines = new ArrayList<>();
        List<RowError> errors = new ArrayList<>();
        Map<String, Integer> lineCounter = new HashMap<>();   // (영수증번호|판매일시) → 지금까지 품목 수
        LocalDate start = null, end = null;
        int total = 0;

        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            int fileLine = i + 1;
            if (r.stream().allMatch(String::isBlank)) continue;   // 빈 줄
            total++;
            try {
                String billNo = required(r, col, "bill_no", "영수증번호");
                String menu = required(r, col, "menu", "메뉴명");
                OffsetDateTime soldAt = dateTime(required(r, col, "sold_at", "판매일시"));
                int quantity = number(required(r, col, "quantity", "수량"), "수량");
                int amount = number(required(r, col, "amount", "금액"), "금액");
                String discount = cell(r, col, "discount");
                if (!discount.isBlank()) amount -= number(discount, "할인");
                String status = status(cell(r, col, "status"));

                if (billNo.length() > 30) throw new IllegalArgumentException("영수증번호가 너무 길어요(30자 이하)");
                if (menu.length() > 200) throw new IllegalArgumentException("메뉴명이 너무 길어요(200자 이하)");

                String key = billNo + "|" + soldAt;
                int lineNo = lineCounter.merge(key, 1, Integer::sum);
                lines.add(new SalesLine(fileLine, billNo, lineNo, menu, soldAt, quantity, amount, status));

                LocalDate d = soldAt.toLocalDate();
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
            String name = header.get(i).strip().toLowerCase(Locale.ROOT);
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

    private static String required(List<String> r, Map<String, Integer> col, String key, String label) {
        String v = cell(r, col, key);
        if (v.isEmpty()) throw new IllegalArgumentException(label + "이(가) 비어 있어요");
        return v;
    }

    private static OffsetDateTime dateTime(String v) {
        for (DateTimeFormatter f : DATE_TIMES) {
            try {
                return LocalDateTime.parse(v, f).atZone(ZONE).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // 다음 형식 시도
            }
        }
        throw new IllegalArgumentException("판매일시 형식을 읽을 수 없어요: " + v + " (예: 2026-01-01 12:08:13)");
    }

    private static int number(String v, String label) {
        try {
            return Integer.parseInt(v.replace(",", "").replace("원", "").strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "이(가) 숫자가 아니에요: " + v);
        }
    }

    private static String status(String v) {
        String s = v.strip().toUpperCase(Locale.ROOT);
        return switch (s) {
            case "", "정상", "NORMAL", "결제", "승인", "서비스" -> "NORMAL";
            case "반품", "취소", "환불", "RETURN" -> "RETURN";
            default -> throw new IllegalArgumentException("상태 값을 알 수 없어요: " + v + " (정상/반품)");
        };
    }
}