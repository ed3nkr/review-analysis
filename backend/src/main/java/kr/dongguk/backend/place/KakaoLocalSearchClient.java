package kr.dongguk.backend.place;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import kr.dongguk.backend.common.exception.ExternalApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.Arrays;
import java.util.List;

@Component
public class KakaoLocalSearchClient {

    private final RestClient restClient;

    public KakaoLocalSearchClient(KakaoLocalProperties props) {
        this.restClient = RestClient.builder()
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + props.restApiKey())
                .build();
    }

    /** 키워드로 장소 검색: 최대 5건 */
    public List<PlaceCandidate> search(String query) {
        try {
            KakaoResponse res = restClient.get()
                    .uri(uri -> uri.path("/v2/local/search/keyword.json")
                            .queryParam("query", "{q}")
                            .queryParam("size", 5)
                            .build(query))               // 한글 검색어를 UTF-8로 안전하게 인코딩
                    .retrieve()
                    .body(KakaoResponse.class);
            if (res == null || res.documents() == null) return List.of();
            return res.documents().stream().map(KakaoLocalSearchClient::toCandidate).toList();
        } catch (RestClientException e) {
            throw new ExternalApiException("카카오 장소 검색에 실패했습니다", e);
        }
    }

    private static PlaceCandidate toCandidate(Doc d) {
        return new PlaceCandidate(d.id(), d.placeName(), shortCategory(d.categoryName()),
                d.addressName(), blankToNull(d.roadAddressName()), blankToNull(d.phone()),
                d.x(), d.y(), d.placeUrl());
    }

    // "음식점 > 일식 > 돈까스,우동" → "일식 > 돈까스,우동" (store.category는 50자)
    private static String shortCategory(String c) {
        if (c == null || c.isBlank()) return null;
        String[] parts = c.split(" > ");
        String s = parts.length > 1
                ? String.join(" > ", Arrays.copyOfRange(parts, 1, parts.length))
                : c;
        return s.length() > 50 ? s.substring(0, 50) : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    // ---- 카카오 응답 모양 (필요한 필드만) ----
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoResponse(List<Doc> documents) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Doc(
            String id,
            @JsonProperty("place_name") String placeName,
            @JsonProperty("category_name") String categoryName,
            String phone,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("road_address_name") String roadAddressName,
            String x,
            String y,
            @JsonProperty("place_url") String placeUrl
    ) {}
}