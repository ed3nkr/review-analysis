package kr.dongguk.backend.menu;

import java.util.List;

/**
 * 어느 가게에나 흔한 시판 음료·술을 메뉴군 '음료' / '주류'로 묶는다.
 * 가게별 메뉴가 아니라 공통 상품명이라 사전으로 둔다. 잘못 묶이면 점주가 화면에서 옮긴다.
 * 메뉴군 이름 후보(baseName)로 판단한다 → '치킨+맥주 세트'처럼 토핑에만 술이 있으면 묶지 않음.
 */
public final class MenuCategory {

    public static final String DRINK = "음료";
    public static final String ALCOHOL = "주류";

    /** 이름 어디에 있어도 되는 말 (다른 단어와 헷갈릴 일이 거의 없음) */
    private static final List<String> ALCOHOL_ANYWHERE = List.of(
            "맥주", "생맥", "소주", "막걸리", "동동주", "하이볼", "참이슬", "처음처럼", "클라우드", "하이트",
            "필라이트", "켈리", "위스키", "사케", "소맥", "칭따오", "아사히", "기네스", "하이네켄", "진로이즈백",
            "레드와인", "화이트와인", "하우스와인", "좋은데이", "한라산");
    private static final List<String> DRINK_ANYWHERE = List.of(
            "사이다", "펩시", "코카콜라", "제로콜라", "콜라제로", "스프라이트", "환타", "칠성", "탄산수", "음료수",
            "주스", "쥬스", "에이드", "웰치스", "밀키스", "맥콜", "데미소다", "핫식스", "레드불", "토레타", "포카리");

    /** 짧아서 다른 말 속에 들어갈 수 있는 말 → 앞뒤가 한글이 아닐 때만 (콜라겐, 카스테라, 테라스 제외) */
    private static final List<String> ALCOHOL_WORD = List.of("카스", "테라", "진로", "새로", "청하", "와인");
    private static final List<String> DRINK_WORD = List.of("콜라", "제로", "음료", "생수", "토닉");

    private MenuCategory() {}

    /** '음료' / '주류' / 해당 없으면 null */
    public static String of(String posName) {
        String name = MenuNameParser.parse(posName).baseName();
        String compact = name.replace(" ", "");
        for (String w : ALCOHOL_ANYWHERE) if (compact.contains(w)) return ALCOHOL;
        for (String w : ALCOHOL_WORD) if (hasWord(name, w)) return ALCOHOL;
        for (String w : DRINK_ANYWHERE) if (compact.contains(w)) return DRINK;
        for (String w : DRINK_WORD) if (hasWord(name, w)) return DRINK;
        return null;
    }

    private static boolean hasWord(String s, String w) {
        for (int i = s.indexOf(w); i >= 0; i = s.indexOf(w, i + 1)) {
            boolean before = i == 0 || !isHangul(s.charAt(i - 1));
            int j = i + w.length();
            boolean after = j == s.length() || !isHangul(s.charAt(j));
            if (before && after) return true;
        }
        return false;
    }

    private static boolean isHangul(char c) {
        return c >= '가' && c <= '힣';
    }
}