package kr.dongguk.backend.sales.dto;

import kr.dongguk.backend.sales.RowError;
import java.time.LocalDate;
import java.util.List;

public record SalesUploadResponse(
        Long jobId,
        String fileName,
        int totalRows,       // 파일의 데이터 줄 수 (빈 줄 제외)
        int savedRows,       // 새로 저장
        int duplicateRows,   // 이미 있어서 건너뜀
        int errorRows,       // 형식 오류로 저장 못 함
        List<RowError> errors, // 앞에서부터 20개
        LocalDate periodStart,
        LocalDate periodEnd,
        int newMenus,
        int newGroups
) {}