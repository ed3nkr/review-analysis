package kr.dongguk.backend.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * POS 메뉴명 → 메뉴군 이름 + 종류 + 토핑.
 * 어느 가게에나 흔한 표기만 처리한다: (옵션), [옵션], +토핑, 끝의 수량(4ps, 2인, 3개), 끝의 '세트'.
 * 처리하지 못한 메뉴는 메뉴명 그대로 메뉴군이 되고, 점주가 화면에서 합친다.
 */
public final class MenuNameParser {

    private static final Pattern BRACKET = Pattern.compile("[(\\[（【]([^)\\]）】]*)[)\\]）】]");
    private static final Pattern QUANTITY =
            Pattern.compile("^(.*?)\\s*(\\d+\\s*(?:pcs|ps|p|개|인분|인|피스|조각))$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET = Pattern.compile("^(.*?)\\s*(세트|set)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private MenuNameParser() {}

    /** 앞뒤 공백 제거 + 연속 공백(전각 공백 포함)을 하나로 */
    public static String normalize(String s) {
        if (s == null) return "";
        return SPACES.matcher(s.replace('\u3000', ' ')).replaceAll(" ").strip();
    }

    public static ParsedMenu parse(String posName) {
        String normalized = normalize(posName);
        String rest = normalized;
        List<String> variants = new ArrayList<>();
        String topping = null;

        // 1) "+토핑" (단, "1+1" 같은 숫자 사이의 +는 행사 표기라 건드리지 않음)
        int plus = toppingPlus(rest);
        if (plus > 0) {
            String t = normalize(rest.substring(plus + 1));
            topping = t.isEmpty() ? null : t;
            rest = rest.substring(0, plus);
        }

        // 2) 괄호 속 옵션: (1인) [냉] （대）
        Matcher m = BRACKET.matcher(rest);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = normalize(m.group(1));
            if (!v.isEmpty()) variants.add(v);
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        rest = normalize(sb.toString());

        // 3) 끝의 수량: 4ps, 2인, 3개
        Matcher q = QUANTITY.matcher(rest);
        if (q.matches() && !q.group(1).isBlank()) {
            variants.add(q.group(2).replace(" ", ""));
            rest = normalize(q.group(1));
        }

        // 4) 끝의 '세트'
        Matcher st = SET.matcher(rest);
        if (st.matches() && !st.group(1).isBlank()) {
            variants.add(st.group(2));
            rest = normalize(st.group(1));
        }

        String base = rest.isEmpty() ? normalized : rest;
        boolean changed = topping != null || !variants.isEmpty() || !base.equals(normalized);
        return new ParsedMenu(
                normalized,
                cut(base, 50),
                variants.isEmpty() ? null : cut(String.join(",", variants), 30),
                topping == null ? null : cut(topping, 50),
                changed ? ParseRule.RULE : ParseRule.AS_IS);
    }

    /** 토핑 구분용 '+'의 위치. 숫자+숫자(1+1)는 제외. 없으면 -1 */
    private static int toppingPlus(String s) {
        for (int i = 1; i < s.length() - 1; i++) {
            if (s.charAt(i) != '+') continue;
            boolean numberPlusNumber = Character.isDigit(s.charAt(i - 1)) && Character.isDigit(s.charAt(i + 1));
            if (!numberPlusNumber) return i;
        }
        return -1;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}