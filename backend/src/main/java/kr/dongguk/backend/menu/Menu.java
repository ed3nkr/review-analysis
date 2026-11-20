package kr.dongguk.backend.menu;

import jakarta.persistence.*;
import kr.dongguk.backend.store.Store;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "menu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_group_id")
    private MenuGroup menuGroup;

    private String posName;          // POS 표기 그대로
    private String normalizedName;   // 매칭 키
    private String variant;
    private String topping;

    @Enumerated(EnumType.STRING)     // DB에는 'RULE' 같은 문자열로 저장
    private ParseRule parseRule;

    public static Menu create(Store store, MenuGroup group, String posName, ParsedMenu p) {
        Menu m = new Menu();
        m.store = store;
        m.menuGroup = group;
        m.posName = posName.length() > 200 ? posName.substring(0, 200) : posName;
        m.normalizedName = p.normalizedName();
        m.variant = p.variant();
        m.topping = p.topping();
        m.parseRule = p.rule();
        return m;
    }

    @Column(nullable = false)
    private boolean discontinued;

    /** 점주가 다른 메뉴군으로 옮김 */
    public void moveTo(MenuGroup group) {
        this.menuGroup = group;
        this.parseRule = ParseRule.MANUAL;
    }

    public void changeDiscontinued(boolean discontinued) {
        this.discontinued = discontinued;
    }
}