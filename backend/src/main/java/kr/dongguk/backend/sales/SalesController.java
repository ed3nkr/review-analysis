package kr.dongguk.backend.sales;

import kr.dongguk.backend.common.auth.CurrentOwner;
import kr.dongguk.backend.sales.dto.SalesSummaryResponse;
import kr.dongguk.backend.sales.dto.SalesUploadResponse;
import kr.dongguk.backend.sales.dto.SalesDeleteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/api/stores/{storeId}/sales")
@RequiredArgsConstructor
public class SalesController {

    private final SalesUploadService salesUploadService;
    private final CurrentOwner currentOwner;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SalesUploadResponse upload(@PathVariable Long storeId, @RequestParam("file") MultipartFile file) {
        return salesUploadService.upload(currentOwner.id(), storeId, file);
    }

    @GetMapping("/summary")
    public SalesSummaryResponse summary(@PathVariable Long storeId) {
        return salesUploadService.summary(currentOwner.id(), storeId);
    }

    @DeleteMapping("/uploads/{jobId}")
    public SalesDeleteResponse deleteUpload(@PathVariable Long storeId, @PathVariable Long jobId) {
        return salesUploadService.deleteUpload(currentOwner.id(), storeId, jobId);
    }

    @DeleteMapping
    public SalesDeleteResponse deleteAll(@PathVariable Long storeId) {
        return salesUploadService.deleteAll(currentOwner.id(), storeId);
    }
}