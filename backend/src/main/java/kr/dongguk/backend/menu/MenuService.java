package kr.dongguk.backend.menu;

import kr.dongguk.backend.common.exception.BadRequestException;
import kr.dongguk.backend.common.exception.ConflictException;
import kr.dongguk.backend.common.exception.NotFoundException;
import kr.dongguk.backend.menu.dto.MenuGroupResponse;
import kr.dongguk.backend.store.Store;
import kr.dongguk.backend.store.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final int MAX_ALIASES = 20;
    private static final int MAX_ALIAS_LENGTH = 30;
    private static final int MAX_GROUP_NAME = 50;

    private final MenuRepository menuRepository;
    private final MenuGroupRepository menuGroupRepository;
    private final StoreService storeService;
    private final JdbcTemplate jdbc;

    public record RegisterResult(Map<String, Menu> menusByName, int newMenus, int newGroups) {}

    public record MenuStats(long soldQuantity, LocalDate lastSoldOn) {}

    /** 매출 업로드 때 호출: 처음 보는 메뉴명만 등록. 음료·주류는 공통 메뉴군으로 */
    @Transactional
    public RegisterResult registerIfAbsent(Store store, Collection<String> posNames) {
        Map<String, Menu> byNormalized = new HashMap<>();
        for (Menu m : menuRepository.findByStoreId(store.getId())) byNormalized.put(m.getNormalizedName(), m);
        Map<String, MenuGroup> groups = new HashMap<>();
        for (MenuGroup g : menuGroupRepository.findByStoreIdOrderByNameAsc(store.getId())) groups.put(g.getName(), g);

        Map<String, Menu> result = new HashMap<>();
        int newMenus = 0;
        int newGroups = 0;
        for (String raw : posNames) {
            ParsedMenu parsed = MenuNameParser.parse(raw);
            if (parsed.normalizedName().isEmpty()) continue;

            Menu menu = byNormalized.get(parsed.normalizedName());
            if (menu == null) {
                String category = MenuCategory.of(raw);
                String groupName = category != null ? category : parsed.baseName();
                MenuGroup group = groups.get(groupName);
                if (group == null) {
                    group = menuGroupRepository.save(MenuGroup.create(store, groupName));
                    groups.put(groupName, group);
                    newGroups++;
                }
                menu = menuRepository.save(Menu.create(store, group, raw, parsed));
                byNormalized.put(parsed.normalizedName(), menu);
                newMenus++;
            }
            result.put(raw, menu);
        }
        return new RegisterResult(result, newMenus, newGroups);
    }

    @Transactional(readOnly = true)
    public List<MenuGroupResponse> getGroups(Long ownerId, Long storeId) {
        storeService.findOwnedStore(ownerId, storeId);
        Map<Long, MenuStats> stats = salesStats(storeId);
        Map<Long, List<Menu>> menusByGroup = menuRepository.findByStoreId(storeId).stream()
                .collect(Collectors.groupingBy(m -> m.getMenuGroup().getId()));
        return menuGroupRepository.findByStoreIdOrderByNameAsc(storeId).stream()
                .map(g -> MenuGroupResponse.of(g, menusByGroup.getOrDefault(g.getId(), List.of()), stats))
                .toList();
    }

    @Transactional
    public void updateAliases(Long ownerId, Long storeId, Long groupId, List<String> aliases) {
        storeService.findOwnedStore(ownerId, storeId);
        MenuGroup group = menuGroupRepository.findByIdAndStoreId(groupId, storeId)
                .orElseThrow(() -> new NotFoundException("메뉴군을 찾을 수 없어요."));
        List<String> cleaned = aliases.stream()
                .map(MenuNameParser::normalize)
                .filter(a -> !a.isEmpty() && !a.equals(group.getName()))
                .distinct()
                .toList();
        if (cleaned.size() > MAX_ALIASES) {
            throw new BadRequestException("별칭은 " + MAX_ALIASES + "개까지 넣을 수 있어요.");
        }
        for (String a : cleaned) {
            if (a.length() > MAX_ALIAS_LENGTH) throw new BadRequestException("별칭은 30자 이하로 써 주세요: " + a);
        }
        group.updateAliases(cleaned);
    }

    /** 다른 메뉴군으로 옮김 (같은 이름의 메뉴군이 없으면 새로 만들고, 비게 된 메뉴군은 지움) */
    @Transactional
    public void moveMenu(Long ownerId, Long storeId, Long menuId, String groupName) {
        Store store = storeService.findOwnedStore(ownerId, storeId);
        Menu menu = findMenu(storeId, menuId);
        String name = MenuNameParser.normalize(groupName);
        if (name.isEmpty() || name.length() > MAX_GROUP_NAME) {
            throw new BadRequestException("메뉴군 이름은 1~50자로 써 주세요.");
        }
        MenuGroup old = menu.getMenuGroup();
        if (old.getName().equals(name)) return;

        MenuGroup target = menuGroupRepository.findByStoreIdAndName(storeId, name)
                .orElseGet(() -> menuGroupRepository.save(MenuGroup.create(store, name)));
        menu.moveTo(target);
        menuRepository.flush();
        deleteIfEmpty(old);
    }

    @Transactional
    public void changeDiscontinued(Long ownerId, Long storeId, Long menuId, boolean discontinued) {
        storeService.findOwnedStore(ownerId, storeId);
        findMenu(storeId, menuId).changeDiscontinued(discontinued);
    }

    /** 판매 기록이 없는 메뉴만 삭제 (있으면 지난 매출 분석이 깨지므로 단종 표시로 안내) */
    @Transactional
    public void deleteMenu(Long ownerId, Long storeId, Long menuId) {
        storeService.findOwnedStore(ownerId, storeId);
        Menu menu = findMenu(storeId, menuId);
        Boolean sold = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM sales_record WHERE menu_id = ?)", Boolean.class, menuId);
        if (Boolean.TRUE.equals(sold)) {
            throw new ConflictException("판매 기록이 있는 메뉴는 지울 수 없어요. 대신 '단종'으로 표시해 주세요.");
        }
        MenuGroup group = menu.getMenuGroup();
        menuRepository.delete(menu);
        menuRepository.flush();
        deleteIfEmpty(group);
    }

    private Menu findMenu(Long storeId, Long menuId) {
        return menuRepository.findByIdAndStoreId(menuId, storeId)
                .orElseThrow(() -> new NotFoundException("메뉴를 찾을 수 없어요."));
    }

    private void deleteIfEmpty(MenuGroup group) {
        if (!menuRepository.existsByMenuGroupId(group.getId())) menuGroupRepository.delete(group);
    }

    /** 메뉴별 판매 수량(반품 제외)과 마지막 판매일 */
    private Map<Long, MenuStats> salesStats(Long storeId) {
        Map<Long, MenuStats> map = new HashMap<>();
        jdbc.query("""
                SELECT menu_id, sum(quantity) AS qty, max(sold_at) AS last_sold
                FROM sales_record
                WHERE store_id = ? AND menu_id IS NOT NULL AND status = 'NORMAL'
                GROUP BY menu_id
                """, (RowCallbackHandler) rs -> map.put(rs.getLong("menu_id"), new MenuStats(
                rs.getLong("qty"),
                rs.getObject("last_sold", OffsetDateTime.class).atZoneSameInstant(ZONE).toLocalDate())), storeId);
        return map;
    }
}