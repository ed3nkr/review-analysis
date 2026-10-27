package kr.dongguk.backend.store;

import jakarta.persistence.*;
import kr.dongguk.backend.owner.Owner;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Entity
@Table(name = "store")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    public static final LocalTime DEFAULT_SLOT_CUTOFF = LocalTime.of(16, 0);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private Owner owner;

    private String name;
    private String category;
    private String placeId;           // 네이버 플레이스 ID (리뷰 첫 수집 때 매칭)
    private String placeName;
    private String address;
    private String phone;             // V3에서 추가
    private LocalTime slotCutoff;
    private OffsetDateTime lastCollectedAt;

    public static Store create(Owner owner, String name, String category,
                               String address, String phone, LocalTime slotCutoff) {
        Store store = new Store();
        store.owner = owner;
        store.name = name;
        store.category = category;
        store.address = address;
        store.phone = phone;
        store.slotCutoff = (slotCutoff != null) ? slotCutoff : DEFAULT_SLOT_CUTOFF;
        return store;
    }

    /** PATCH: null로 온 값은 바꾸지 않음 */
    public void update(String name, String category, String address, String phone, LocalTime slotCutoff) {
        if (name != null) this.name = name;
        if (category != null) this.category = category;
        if (address != null) this.address = address;
        if (phone != null) this.phone = phone;
        if (slotCutoff != null) this.slotCutoff = slotCutoff;
    }

    public boolean isOwnedBy(Long ownerId) {
        return owner.getId().equals(ownerId);
    }
}