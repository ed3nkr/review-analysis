package kr.dongguk.backend.analysis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 analysis.* — 분석 서버(FastAPI) 주소와 기다릴 시간 */
@ConfigurationProperties(prefix = "analysis")
public record AnalysisProperties(
        String serverUrl,       // 예: http://localhost:8000
        int timeoutSeconds      // 분석은 CPU 추론이라 1~3분 걸릴 수 있음
) {}
