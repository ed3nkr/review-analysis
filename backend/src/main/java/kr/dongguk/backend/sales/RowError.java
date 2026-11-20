package kr.dongguk.backend.sales;

import java.util.List;

/** 저장하지 못한 줄과 이유 */
public record RowError(int line, String reason) {

    /** job.error_detail(jsonb)에 넣을 JSON 배열. 앞에서부터 max개만 */
    public static String toJsonArray(List<RowError> errors, int max) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < Math.min(errors.size(), max); i++) {
            RowError e = errors.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"line\":").append(e.line())
                    .append(",\"reason\":\"").append(escape(e.reason())).append("\"}");
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}