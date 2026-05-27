package com.codezeng.lms.web;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.UserRepository;
import com.codezeng.lms.security.Permission;
import com.codezeng.lms.service.I18nMessageService;
import com.codezeng.lms.service.OperationLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final I18nMessageService i18n;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, OperationLogService operationLogService, I18nMessageService i18n) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
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
        model.addAttribute("users", userRepository.findAll(
                userSpec(keyword, role, status),
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

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String create(Model model) {
        addFormData(model, new User());
        return "user/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String edit(@PathVariable Long id, Model model) {
        addFormData(model, userRepository.findById(id).orElseThrow());
        return "user/form";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String save(@ModelAttribute User user,
                       @RequestParam(required = false) String rawPassword,
                       @RequestParam(required = false) List<String> permissionCodes,
                       RedirectAttributes redirectAttributes) {
        User existing = null;
        if (hasDuplicateUsername(user) || hasDuplicateEmail(user)) {
            redirectAttributes.addFlashAttribute("error", i18n.get("error.user.duplicate"));
            return "redirect:" + (user.getId() == null ? "/users/new" : "/users/" + user.getId() + "/edit");
        }
        if (user.getId() != null) {
            existing = userRepository.findById(user.getId()).orElseThrow();
            user.setCreateTime(existing.getCreateTime());
            user.setDeleted(existing.isDeleted());
            if (isCurrentUser(existing) && (user.getRole() != existing.getRole() || user.getStatus() != AccountStatus.NORMAL)) {
                redirectAttributes.addFlashAttribute("error", i18n.get("error.user.selfOperation"));
                return "redirect:/users";
            }
            if (isProtectedAdmin(existing) && demotesOrDisablesLastAdmin(existing, user)) {
                redirectAttributes.addFlashAttribute("error", i18n.get("error.user.lastAdmin"));
                return "redirect:/users";
            }
        }
        if (user.getId() != null && !StringUtils.hasText(rawPassword)) {
            user.setPassword(existing.getPassword());
        } else {
            if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 8) {
                redirectAttributes.addFlashAttribute("error", i18n.get("error.user.passwordRequired"));
                return "redirect:" + (user.getId() == null ? "/users/new" : "/users/" + user.getId() + "/edit");
            }
            user.setPassword(passwordEncoder.encode(rawPassword));
        }
        user.setPermissionCodes(normalizePermissionCodes(permissionCodes));
        userRepository.save(user);
        operationLogService.record("用户管理", "保存用户", user.getUsername());
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.saved"));
        return "redirect:/users";
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String disable(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow();
        if (isCurrentUser(user)) {
            redirectAttributes.addFlashAttribute("error", i18n.get("error.user.selfOperation"));
            return "redirect:/users";
        }
        if (isProtectedAdmin(user)) {
            redirectAttributes.addFlashAttribute("error", i18n.get("error.user.lastAdmin"));
            return "redirect:/users";
        }
        user.setStatus(AccountStatus.DISABLED);
        userRepository.save(user);
        operationLogService.record("User management", "Disable user", user.getUsername());
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.disabled"));
        return "redirect:/users";
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String enable(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow();
        user.setStatus(AccountStatus.NORMAL);
        userRepository.save(user);
        operationLogService.record("User management", "Enable user", user.getUsername());
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.enabled"));
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(id).orElseThrow();
        if (isCurrentUser(user)) {
            redirectAttributes.addFlashAttribute("error", i18n.get("error.user.selfOperation"));
            return "redirect:/users";
        }
        if (isProtectedAdmin(user)) {
            redirectAttributes.addFlashAttribute("error", i18n.get("error.user.lastAdmin"));
            return "redirect:/users";
        }
        user.setDeleted(true);
        user.setStatus(AccountStatus.DISABLED);
        userRepository.save(user);
        operationLogService.record("User management", "Delete user", user.getUsername());
        redirectAttributes.addFlashAttribute("message", i18n.get("flash.user.deleted"));
        return "redirect:/users";
    }

    private void addFormData(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
        model.addAttribute("permissions", Permission.values());
        model.addAttribute("selectedPermissionCodes", selectedPermissionCodes(user));
    }

    private String normalizePermissionCodes(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return "";
        }
        Set<String> allowed = Arrays.stream(Permission.values())
                .map(Permission::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return permissionCodes.stream()
                .map(String::trim)
                .filter(allowed::contains)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private Set<String> selectedPermissionCodes(User user) {
        if (!StringUtils.hasText(user.getPermissionCodes())) {
            return Set.of();
        }
        return Arrays.stream(user.getPermissionCodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Specification<User> userSpec(String keyword, UserRole role, AccountStatus status) {
        Specification<User> spec = (root, query, builder) -> builder.isFalse(root.get("deleted"));
        if (role != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("role"), role));
        }
        if (status != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("username")), like),
                    builder.like(builder.lower(root.get("email")), like),
                    builder.like(builder.lower(root.get("nickname")), like),
                    builder.like(builder.lower(root.get("phone")), like),
                    builder.like(builder.lower(root.get("readerNo")), like)));
        }
        return spec;
    }

    private boolean hasDuplicateUsername(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            return false;
        }
        return user.getId() == null
                ? userRepository.existsByUsername(user.getUsername())
                : userRepository.existsByUsernameAndIdNot(user.getUsername(), user.getId());
    }

    private boolean hasDuplicateEmail(User user) {
        if (!StringUtils.hasText(user.getEmail())) {
            return false;
        }
        return user.getId() == null
                ? userRepository.existsByEmail(user.getEmail())
                : userRepository.existsByEmailAndIdNot(user.getEmail(), user.getId());
    }

    private boolean demotesOrDisablesLastAdmin(User existing, User updated) {
        if (!isProtectedAdmin(existing)) {
            return false;
        }
        return updated.getRole() != UserRole.SUPER_ADMIN || updated.getStatus() != AccountStatus.NORMAL;
    }

    private boolean isProtectedAdmin(User user) {
        return user.getRole() == UserRole.SUPER_ADMIN
                && user.getStatus() == AccountStatus.NORMAL
                && userRepository.countByRoleAndStatusAndDeletedFalse(UserRole.SUPER_ADMIN, AccountStatus.NORMAL) <= 1;
    }

    private boolean isCurrentUser(User user) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && user.getUsername().equals(authentication.getName());
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
