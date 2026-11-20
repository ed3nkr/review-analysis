package kr.dongguk.backend.review;

import kr.dongguk.backend.common.exception.ExternalApiException;
import kr.dongguk.backend.sales.CsvReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 리뷰 가져오기. 결과는 항상 같은 CSV 형식(ReviewCsvParser 가 읽는 형식)으로 돌려준다.
 *  - crawler: crawler/naver_reviews.py 를 실행해 네이버 플레이스 리뷰를 수집 (since 날짜 이후)
 *  - dummy  : 정해 둔 시연용 리뷰 파일을 읽음 (분석 시연용 6개월치 가상 데이터)
 */
@Component
@RequiredArgsConstructor
public class ReviewCollector {

    /** 수집 대상 매장 정보 (since = 이 날짜 이후 리뷰만 — 매출 시작일) */
    public record Target(Long storeId, String name, String address, String phone,
                         LocalTime slotCutoff, LocalDate since) {}

    /** 수집 결과: CSV 텍스트(새 리뷰가 없으면 빈 문자열) + 출처(NAVER_PLACE / DUMMY) */
    public record Collected(String csvText, String source) {}

    private final ReviewCollectorProperties props;

    public Collected collect(Target target) {
        return props.isCrawler() ? crawl(target) : dummy();
    }

    private Collected dummy() {
        Path file = Path.of(props.dummyFile()).toAbsolutePath().normalize();
        try {
            return new Collected(CsvReader.decode(Files.readAllBytes(file)), "DUMMY");
        } catch (IOException e) {
            throw new ExternalApiException("시연용 리뷰 파일을 찾지 못했어요: " + file);
        }
    }

    private Collected crawl(Target t) {
        Path script = Path.of(props.script()).toAbsolutePath().normalize();
        Path python = Path.of(props.python()).toAbsolutePath().normalize();
        Path out;
        try {
            out = Files.createTempFile("reviews-", ".csv");
        } catch (IOException e) {
            throw new ExternalApiException("임시 파일을 만들지 못했어요.");
        }

        List<String> cmd = new ArrayList<>(List.of(python.toString(), script.toString(),
                "--name", t.name(), "--max", "3000",
                "--cutoff", t.slotCutoff().toString().substring(0, 5),
                "--out", out.toString()));
        if (t.address() != null && !t.address().isBlank()) {
            cmd.add("--address");
            cmd.add(t.address());
        }
        if (t.phone() != null && !t.phone().isBlank()) {
            cmd.add("--phone");
            cmd.add(t.phone());
        }
        if (t.since() != null) {   // 매출 시작일보다 오래된 리뷰가 나오면 수집을 멈춤
            cmd.add("--since");
            cmd.add(t.since().toString());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true).directory(script.getParent().toFile());
        pb.environment().put("PYTHONUTF8", "1");
        try {
            Process p = pb.start();
            CompletableFuture<String> log = CompletableFuture.supplyAsync(() -> read(p.getInputStream()));
            if (!p.waitFor(props.timeoutSeconds(), TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new ExternalApiException("리뷰 수집이 " + props.timeoutSeconds() + "초 안에 끝나지 않았어요.");
            }
            String output = log.get(5, TimeUnit.SECONDS);
            if (p.exitValue() != 0) {
                throw new ExternalApiException("리뷰 수집에 실패했어요: " + lastLine(output));
            }
            byte[] bytes = Files.readAllBytes(out);
            return new Collected(bytes.length == 0 ? "" : CsvReader.decode(bytes), "NAVER_PLACE");
        } catch (IOException | ExecutionException | TimeoutException e) {
            throw new ExternalApiException("리뷰 수집 프로그램을 실행하지 못했어요: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalApiException("리뷰 수집이 중단됐어요.");
        } finally {
            try {
                Files.deleteIfExists(out);
            } catch (IOException ignored) {
                // 임시 파일이라 못 지워도 그냥 둠
            }
        }
    }

    private static String read(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String lastLine(String s) {
        String[] lines = s.strip().split("\\R");
        return lines.length == 0 ? "" : lines[lines.length - 1];
    }
}
