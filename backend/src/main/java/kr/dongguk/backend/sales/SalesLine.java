package kr.dongguk.backend.sales;

import java.time.OffsetDateTime;

/** CSV 한 줄을 검사해 통과한 값 (sales_record 1행이 됨) */
public record SalesLine(
        int fileLine,          // CSV 파일의 몇 번째 줄인지 (오류 안내용)
        String billNo,
        int lineNo,            // 같은 영수증 안에서 몇 번째 품목인지 (파일 순서대로 1, 2, 3...)
        String menuName,
        OffsetDateTime soldAt,
        int quantity,
        int amount,            // 할인 열이 있으면 금액 - 할인
        String status          // NORMAL / RETURN
) {}