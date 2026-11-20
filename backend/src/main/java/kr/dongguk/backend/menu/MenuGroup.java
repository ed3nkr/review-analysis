package kr.dongguk.backend.menu;

import jakarta.persistence.*;
import kr.dongguk.backend.store.Store;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "menu_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "store_id")
    private Store store;

    private String name;

    @JdbcTypeCode(SqlTypes.ARRAY)            // PostgreSQL text[] ↔ List<String>
    @Column(columnDefinition = "text[]")
    private List<String> aliases = new ArrayList<>();

    public static MenuGroup create(Store store, String name) {
        MenuGroup g = new MenuGroup();
        g.store = store;
        g.name = name;
        return g;
    }

    public void updateAliases(List<String> aliases) {
        this.aliases = new ArrayList<>(aliases);
    }
}