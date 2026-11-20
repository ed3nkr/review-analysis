package kr.dongguk.backend.menu;

import jakarta.validation.Valid;
import kr.dongguk.backend.common.auth.CurrentOwner;
import kr.dongguk.backend.menu.dto.AliasesRequest;
import kr.dongguk.backend.menu.dto.MenuGroupResponse;
import kr.dongguk.backend.menu.dto.MenuUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores/{storeId}")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;
    private final CurrentOwner currentOwner;

    @GetMapping("/menu-groups")
    public List<MenuGroupResponse> groups(@PathVariable Long storeId) {
        return menuService.getGroups(currentOwner.id(), storeId);
    }

    @PutMapping("/menu-groups/{groupId}/aliases")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateAliases(@PathVariable Long storeId, @PathVariable Long groupId,
                              @RequestBody @Valid AliasesRequest request) {
        menuService.updateAliases(currentOwner.id(), storeId, groupId, request.aliases());
    }

    @PatchMapping("/menus/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateMenu(@PathVariable Long storeId, @PathVariable Long menuId,
                           @RequestBody MenuUpdateRequest request) {
        if (request.groupName() != null) {
            menuService.moveMenu(currentOwner.id(), storeId, menuId, request.groupName());
        }
        if (request.discontinued() != null) {
            menuService.changeDiscontinued(currentOwner.id(), storeId, menuId, request.discontinued());
        }
    }

    @DeleteMapping("/menus/{menuId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMenu(@PathVariable Long storeId, @PathVariable Long menuId) {
        menuService.deleteMenu(currentOwner.id(), storeId, menuId);
    }
}