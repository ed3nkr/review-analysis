package kr.dongguk.backend.menu;

/** 메뉴명 파싱 결과 */
public record ParsedMenu(
        String normalizedName, // 공백 정리한 전체 메뉴명 (매장 안에서 메뉴를 구분하는 키)
        String baseName,       // 메뉴군 이름 후보
        String variant,        // 1인, 냉, 4ps, 세트 ... (여러 개면 쉼표로)
        String topping,        // + 뒤에 붙은 추가 메뉴
        ParseRule rule
) {}