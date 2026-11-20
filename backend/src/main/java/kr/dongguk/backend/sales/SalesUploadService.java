package kr.dongguk.backend.sales;

import kr.dongguk.backend.common.exception.BadRequestException;
import kr.dongguk.backend.menu.Menu;
import kr.dongguk.backend.menu.MenuNameParser;
import kr.dongguk.backend.menu.MenuRepository;
import kr.dongguk.backend.menu.MenuService;
import kr.dongguk.backend.sales.dto.SalesSummaryResponse;
import kr.dongguk.backend.sales.dto.SalesUploadResponse;
import kr.dongguk.backend.store.Store;
import kr.dongguk.backend.store.StoreService;
import kr.dongguk.backend.sales.dto.SalesDeleteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesUploadService {

    private static final int BATCH_SIZE = 1000;
    private static final int ERRORS_IN_RESPONSE = 20;
    private static final int ERRORS_IN_JOB = 100;

    private final StoreService storeService;
    private final MenuService menuService;
    private final MenuRepository menuRepository;
    private final JdbcTemplate jdbc;

    @Transactional
    public SalesUploadResponse upload(Long ownerId, Long storeId, MultipartFile file) {
        Store store = storeService.findOwnedStore(ownerId, storeId);

        if (file == null || file.isEmpty()) throw new BadRequestException("CSV 파일을 골라 주세요.");
        String fileName = file.getOriginalFilename() == null ? "sales.csv" : file.getOriginalFilename();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BadRequestException("CSV 파일만 올릴 수 있어요.");
        }

        SalesCsvParser.Result parsed;
        try {
            parsed = SalesCsvParser.parse(CsvReader.decode(file.getBytes()));
        } catch (IOException e) {
            throw new BadRequestException("파일을 읽지 못했어요.");
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        if (parsed.lines().isEmpty()) {
            String first = parsed.errors().isEmpty() ? "" : " (예: " + parsed.errors().get(0).line()
                    + "번째 줄 - " + parsed.errors().get(0).reason() + ")";
            throw new BadRequestException("저장할 수 있는 줄이 없어요." + first);
        }

        // 1) 작업 기록 (원본 파일 보관은 아직 안 해서 file_path에는 파일 이름만 남김)
        Long jobId = jdbc.queryForObject("""
                INSERT INTO job (store_id, type, status, file_path, started_at)
                VALUES (?, 'SALES_UPLOAD', 'RUNNING', ?, now())
                RETURNING id
                """, Long.class, storeId, fileName);

        // 2) 메뉴 자동 등록 (2-B의 registerIfAbsent)
        Set<String> names = parsed.lines().stream()
                .map(SalesLine::menuName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        MenuService.RegisterResult registered = menuService.registerIfAbsent(store, names);
        menuRepository.flush();   // 아래 JDBC insert가 menu id를 참조하므로 먼저 DB에 반영

        Map<String, Long> menuIdByName = new HashMap<>();
        for (String name : names) {
            Menu menu = registered.menusByName().get(name);
            if (menu == null) menu = registered.menusByName().get(MenuNameParser.normalize(name));
            if (menu != null) menuIdByName.put(name, menu.getId());
        }

        // 3) 매출 저장 (같은 줄은 건너뜀)
        int[][] counts = jdbc.batchUpdate("""
                INSERT INTO sales_record
                    (store_id, job_id, menu_id, bill_no, line_no, menu_name, sold_at, quantity, amount, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (store_id, bill_no, sold_at, line_no) DO NOTHING
                """, parsed.lines(), BATCH_SIZE, (ps, line) -> {
            Long menuId = menuIdByName.get(line.menuName());
            ps.setLong(1, storeId);
            ps.setLong(2, jobId);
            if (menuId == null) ps.setNull(3, Types.BIGINT);
            else ps.setLong(3, menuId);
            ps.setString(4, line.billNo());
            ps.setInt(5, line.lineNo());
            ps.setString(6, line.menuName());
            ps.setObject(7, line.soldAt());
            ps.setInt(8, line.quantity());
            ps.setInt(9, line.amount());
            ps.setString(10, line.status());
        });
        int saved = 0;
        for (int[] batch : counts) {
            for (int c : batch) if (c > 0) saved += c;
        }

        // 4) 작업 완료
        jdbc.update("""
                UPDATE job
                SET status = 'SUCCESS', processed_count = ?, error_count = ?,
                    error_detail = CAST(? AS jsonb), period_start = ?, period_end = ?, finished_at = now()
                WHERE id = ?
                """, saved, parsed.errors().size(), RowError.toJsonArray(parsed.errors(), ERRORS_IN_JOB),
                parsed.periodStart(), parsed.periodEnd(), jobId);

        return new SalesUploadResponse(
                jobId, fileName, parsed.totalRows(), saved, parsed.lines().size() - saved,
                parsed.errors().size(),
                parsed.errors().subList(0, Math.min(parsed.errors().size(), ERRORS_IN_RESPONSE)),
                parsed.periodStart(), parsed.periodEnd(),
                registered.newMenus(), registered.newGroups());
    }

    @Transactional(readOnly = true)
    public SalesSummaryResponse summary(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);

        List<SalesSummaryResponse.Upload> uploads = jdbc.query("""
                SELECT id, file_path, processed_count, error_count, period_start, period_end, requested_at
                FROM job
                WHERE store_id = ? AND type = 'SALES_UPLOAD' AND status = 'SUCCESS'
                ORDER BY requested_at DESC
                LIMIT 10
                """, (rs, i) -> new SalesSummaryResponse.Upload(
                rs.getLong("id"), rs.getString("file_path"),
                rs.getInt("processed_count"), rs.getInt("error_count"),
                rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                rs.getObject("requested_at", OffsetDateTime.class)), storeId);

        return jdbc.queryForObject("""
                SELECT count(*)                                   AS line_count,
                       count(DISTINCT bill_no || '|' || sold_at)  AS bill_count,
                       min(sold_at)                               AS first_sold,
                       max(sold_at)                               AS last_sold,
                       coalesce(sum(amount) FILTER (WHERE status = 'NORMAL'), 0) AS normal_amount
                FROM sales_record
                WHERE store_id = ?
                """, (rs, i) -> new SalesSummaryResponse(
                rs.getLong("line_count"), rs.getLong("bill_count"),
                toDate(rs.getObject("first_sold", OffsetDateTime.class)),
                toDate(rs.getObject("last_sold", OffsetDateTime.class)),
                rs.getLong("normal_amount"), uploads), storeId);
    }

    /** 올린 파일 하나로 들어온 매출만 삭제 (메뉴는 남김) */
    @Transactional
    public SalesDeleteResponse deleteUpload(Long ownerId, Long storeId, Long jobId) {
        storeService.findOwnedStore(ownerId, storeId);
        Integer found = jdbc.queryForObject(
                "SELECT count(*) FROM job WHERE id = ? AND store_id = ? AND type = 'SALES_UPLOAD'",
                Integer.class, jobId, storeId);
        if (found == null || found == 0) {
            throw new BadRequestException("해당 업로드 기록을 찾을 수 없어요.");
        }
        int deleted = jdbc.update("DELETE FROM sales_record WHERE store_id = ? AND job_id = ?", storeId, jobId);
        jdbc.update("DELETE FROM job WHERE id = ?", jobId);
        return new SalesDeleteResponse(deleted);
    }

    /** 매장의 매출 전체 삭제 (메뉴는 남김) */
    @Transactional
    public SalesDeleteResponse deleteAll(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);
        int deleted = jdbc.update("DELETE FROM sales_record WHERE store_id = ?", storeId);
        jdbc.update("DELETE FROM job WHERE store_id = ? AND type = 'SALES_UPLOAD'", storeId);
        return new SalesDeleteResponse(deleted);
    }

    private static LocalDate toDate(OffsetDateTime t) {
        return t == null ? null : t.atZoneSameInstant(SalesCsvParser.ZONE).toLocalDate();
    }
}