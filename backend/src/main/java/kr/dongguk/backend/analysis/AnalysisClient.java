package kr.dongguk.backend.analysis;

import kr.dongguk.backend.common.exception.ExternalApiException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/** 분석 서버(FastAPI) 호출: POST /stores/{storeId}/analyze  {"job_id": ...} */
@Component
public class AnalysisClient {

    private final RestClient restClient;

    public AnalysisClient(AnalysisProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(props.timeoutSeconds()));
        this.restClient = RestClient.builder()
                .baseUrl(props.serverUrl())
                .requestFactory(factory)
                .build();
    }

    /** 분석 실행. 성공하면 분석 서버가 돌려준 요약(Map), 실패하면 ExternalApiException */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(Long storeId, Long jobId) {
        try {
            return restClient.post()
                    .uri("/stores/{storeId}/analyze", storeId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("job_id", jobId))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            // FastAPI 는 {"detail": "매출이 없어요 ..."} 형태로 이유를 돌려줌
            throw new ExternalApiException("분석에 실패했어요: " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            throw new ExternalApiException("분석 서버에 연결하지 못했어요. 분석 서버(uvicorn)가 켜져 있는지 확인해 주세요.");
        }
    }
}
