package kr.dongguk.backend.review;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "review.collector")
public record ReviewCollectorProperties(
        String mode,          // dummy | crawler
        String dummyFile,
        String python,
        String script,
        int timeoutSeconds
) {
    public boolean isCrawler() {
        return "crawler".equalsIgnoreCase(mode);
    }
}