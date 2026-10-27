package kr.dongguk.backend.owner;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Table(name = "owner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;          // NAVER, DEV
    private String providerUserId;
    private String email;

    @Column(insertable = false, updatable = false)   // DB 기본값 now() 사용
    private OffsetDateTime createdAt;
}