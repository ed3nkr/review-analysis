package kr.dongguk.backend.analysis;

import kr.dongguk.backend.analysis.dto.AnalysisReportResponse;
import kr.dongguk.backend.analysis.dto.EvaluationRequest;
import kr.dongguk.backend.common.auth.CurrentOwner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stores/{storeId}")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;
    private final CurrentOwner currentOwner;

    /** 분석하기 (리뷰 분석 + 리뷰-매출 연계 분석) → 끝나면 리포트 */
    @PostMapping("/analysis")
    public AnalysisReportResponse run(@PathVariable Long storeId) {
        return analysisService.run(currentOwner.id(), storeId);
    }

    /** 최근 분석 리포트 (분석한 적 없으면 jobId 가 null) */
    @GetMapping("/analysis/latest")
    public AnalysisReportResponse latest(@PathVariable Long storeId) {
        return analysisService.latest(currentOwner.id(), storeId);
    }

    /** 점주 평가 저장 */
    @PutMapping("/insights/{insightId}/evaluation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void evaluate(@PathVariable Long storeId, @PathVariable Long insightId, @RequestBody EvaluationRequest request) {
        analysisService.evaluate(currentOwner.id(), storeId, insightId, request);
    }
}
