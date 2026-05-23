package com.codezeng.lms.web;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.UserRepository;
import com.codezeng.lms.service.OperationLogService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, OperationLogService operationLogService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("users", userRepository.findAll(PageRequest.of(page, 12, Sort.by(Sort.Direction.DESC, "createTime"))));
        return "user/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        addFormData(model, new User());
        return "user/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        addFormData(model, userRepository.findById(id).orElseThrow());
        return "user/form";
    }

    @PostMapping
    public String save(@ModelAttribute User user,
                       @RequestParam(required = false) String rawPassword,
                       RedirectAttributes redirectAttributes) {
        User existing = null;
        if (user.getId() != null) {
            existing = userRepository.findById(user.getId()).orElseThrow();
            user.setCreateTime(existing.getCreateTime());
            user.setDeleted(existing.isDeleted());
        }
        if (user.getId() != null && !StringUtils.hasText(rawPassword)) {
            user.setPassword(existing.getPassword());
        } else {
            user.setPassword(passwordEncoder.encode(StringUtils.hasText(rawPassword) ? rawPassword : "123456"));
        }
        userRepository.save(user);
        operationLogService.record("用户管理", "保存用户", user.getUsername());
        redirectAttributes.addFlashAttribute("message", "用户信息已保存");
        return "redirect:/users";
    }

    private void addFormData(Model model, User user) {
        model.addAttribute("user", user);
        model.addAttribute("roles", UserRole.values());
        model.addAttribute("accountStatuses", AccountStatus.values());
    }
}
