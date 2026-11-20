package kr.dongguk.backend.menu;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MenuNameParserTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "김치찌개(1인)                | 김치찌개                   | 1인   | -     | RULE",
            "김치찌개 (2인)               | 김치찌개                   | 2인   | -     | RULE",
            "(냉)가라아게 붓가케우동      | 가라아게 붓가케우동        | 냉    | -     | RULE",
            "마제소바+돈카츠              | 마제소바                   | -     | 돈카츠 | RULE",
            "(특)마제소바+에비텐          | 마제소바                   | 특    | 에비텐 | RULE",
            "치즈카츠 4ps                 | 치즈카츠                   | 4ps   | -     | RULE",
            "삼겹살 1인분                 | 삼겹살                     | 1인분 | -     | RULE",
            "돈까스 세트                  | 돈까스                     | 세트  | -     | RULE",
            "돈까스세트                   | 돈까스                     | 세트  | -     | RULE",
            "카라이( )마제소바            | 카라이마제소바             | -     | -     | RULE",
            "'  김치찌개   (1인) '        | 김치찌개                   | 1인   | -     | RULE",
            "아메리카노                   | 아메리카노                 | -     | -     | AS_IS",
            "콜라 500ml                   | 콜라 500ml                 | -     | -     | AS_IS",
            "클라우드 생맥주 425ml 1+1    | 클라우드 생맥주 425ml 1+1  | -     | -     | AS_IS",
            "세트                         | 세트                       | -     | -     | AS_IS",
    })
    void parse(String pos, String base, String variant, String topping, ParseRule rule) {
        ParsedMenu p = MenuNameParser.parse(pos);

        assertThat(p.baseName()).isEqualTo(base);
        assertThat(p.variant()).isEqualTo(variant);
        assertThat(p.topping()).isEqualTo(topping);
        assertThat(p.rule()).isEqualTo(rule);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "'  김치찌개   (1인) ' | 김치찌개 (1인)",
    })
    void normalize(String raw, String expected) {
        assertThat(MenuNameParser.normalize(raw)).isEqualTo(expected);
    }
}