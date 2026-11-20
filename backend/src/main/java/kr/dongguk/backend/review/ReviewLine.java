package kr.dongguk.backend.review;

import java.time.LocalDate;
import java.util.List;

/** CSV 한 줄을 검사해 통과한 리뷰 (review 1행이 됨) */
public record ReviewLine(
        int fileLine,
        String dedupKey,       // 같은 리뷰를 다시 올려도 한 번만 저장하기 위한 키 (SHA-256)
        String content,        // 본문 (없으면 null — 키워드만 고른 리뷰)
        Integer rating,        // 1~5, 없으면 null
        LocalDate writtenAt,
        LocalDate visitedAt,   // 없으면 null
        String visitSlotTag,   // '점심에 방문' 등 표기 원문
        String authorHash,     // 작성자는 해시로만
        List<String> keywords  // 네이버 키워드 리뷰 ('음식이 맛있어요' ...)
) {}
