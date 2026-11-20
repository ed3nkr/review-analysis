package kr.dongguk.backend.menu;

public enum ParseRule {
    RULE,    // 규칙으로 옵션·토핑을 떼어 메뉴군을 정함
    AS_IS,   // 뗄 것이 없어 메뉴명 그대로 메뉴군이 됨
    MANUAL   // 점주가 직접 메뉴군을 바꿈
}