package kr.dongguk.backend.menu.dto;

import kr.dongguk.backend.menu.Menu;
import kr.dongguk.backend.menu.MenuGroup;
import kr.dongguk.backend.menu.MenuService;
import kr.dongguk.backend.menu.ParseRule;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record MenuGroupResponse(Long id, String name, List<String> aliases, List<MenuItem> menus) {

    public record MenuItem(Long id, String posName, String variant, String topping, ParseRule parseRule,
                           boolean discontinued, long soldQuantity, LocalDate lastSoldOn) {}

    public static MenuGroupResponse of(MenuGroup group, List<Menu> menus, Map<Long, MenuService.MenuStats> stats) {
        List<MenuItem> items = menus.stream()
                .sorted(Comparator.comparing(Menu::getPosName))
                .map(m -> {
                    MenuService.MenuStats s = stats.get(m.getId());
                    return new MenuItem(m.getId(), m.getPosName(), m.getVariant(), m.getTopping(), m.getParseRule(),
                            m.isDiscontinued(), s == null ? 0 : s.soldQuantity(), s == null ? null : s.lastSoldOn());
                })
                .toList();
        return new MenuGroupResponse(group.getId(), group.getName(), List.copyOf(group.getAliases()), items);
    }
}