package com.codezeng.lms.web;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.security.Permission;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final I18nMessageService i18n;

    public UserController(UserService userService, I18nMessageService i18n) {
        this.userService = userService;
        this.i18n = i18n;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "20") int size,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) UserRole role,
                       @RequestParam(required = false) AccountStatus status,
                       Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        model.addAttribute("users", userService.search(keyword, role, status,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createTime"))));
        model.addAttribute("keyword", keyword);
        model.addAttribute("role", role);
        model.addAttribute("status", status);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
        model.addAttribute("pageSize", pageSize);
        model.addAttribute("queryString", queryString(keyword, role, status, pageSize));
        return "user/list";
    }

    @GetMapping("/trash")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String trash(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Model model) {
        int pageSize = Math.min(Math.max(size, 1), 100);
        model.addAttribute("users", userService.trash(PageRequest.of(
                Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "updateTime"))));
        model.addAttribute("pageSize", pageSize);
        return "user/trash";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String create(Model model) {
        addFormData(model, new User());
        return "user/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String edit(@PathVariable Long id, Model model) {
        addFormData(model, userService.getEditable(id));
        return "user/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @PreventDuplicateSubmit
    public String save(@ModelAttribute User user,
                       @RequestParam(required = false) String rawPassword,
                       @RequestParam(required = false) List<String> permissionCodes,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            userService.save(user, rawPassword, permissionCodes, currentUsername());
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.saved"));
            return "redirect:/users";
        } catch (IllegalArgumentException | IllegalStateException ex) {
            addFormData(model, user);
            model.addAttribute("error", ex.getMessage());
            return "user/form";
        }
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @PreventDuplicateSubmit
    public String disable(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.disable(id, currentUsername());
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.disabled"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @PreventDuplicateSubmit
    public String enable(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.enable(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.enabled"));
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @PreventDuplicateSubmit
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.softDelete(id, currentUsername());
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.deleted"));
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/users";
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @PreventDuplicateSubmit
    public String restore(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userService.restore(id);
            redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.restored"));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/users/trash";
    }

    @PostMapping("/{id}/purge")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    @PreventDuplicateSubmit
    public String purge(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.purge(id);
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.purged"));
        return "redirect:/users/trash";
    }

    private void addFormData(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
        model.addAttribute("permissions", Permission.values());
        model.addAttribute("selectedPermissionCodes", userService.selectedPermissionCodes(user));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? null : authentication.getName();
    }

    private String queryString(String keyword, UserRole role, AccountStatus status, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        queryParam(builder, "keyword", keyword);
        queryParam(builder, "role", role);
        queryParam(builder, "status", status);
        builder.queryParam("size", size);
        String query = builder.build().encode().toUriString();
        return query.startsWith("?") ? "&" + query.substring(1) : query;
    }

    private void queryParam(UriComponentsBuilder builder, String name, Object value) {
        if (value == null) {
            return;
        }
        String text = String.valueOf(value);
        if (!text.isBlank()) {
            builder.queryParam(name, text);
        }
    }
}
