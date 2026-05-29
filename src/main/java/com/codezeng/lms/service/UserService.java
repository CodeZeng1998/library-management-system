package com.codezeng.lms.service;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.UserRepository;
import com.codezeng.lms.security.Permission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,64}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OperationLogService operationLogService;
    private final I18nMessageService i18n;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       OperationLogService operationLogService,
                       I18nMessageService i18n) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.operationLogService = operationLogService;
        this.i18n = i18n;
    }

    public Page<User> search(String keyword, UserRole role, AccountStatus status, Pageable pageable) {
        return userRepository.findAll(userSpec(keyword, role, status), pageable);
    }

    public Page<User> trash(Pageable pageable) {
        return userRepository.findByDeletedTrue(pageable);
    }

    public User getEditable(Long id) {
        return userRepository.findByIdAndDeletedFalse(id).orElseThrow();
    }

    @Transactional
    public User save(User form, String rawPassword, List<String> permissionCodes, String currentUsername) {
        User existing = form.getId() == null ? null : getEditable(form.getId());
        User user = existing == null ? new User() : existing;
        String previousUsername = user.getUsername();

        user.setUsername(required(form.getUsername(), "error.user.usernameRequired"));
        user.setEmail(required(form.getEmail(), "error.user.emailRequired").toLowerCase());
        user.setNickname(required(form.getNickname(), "error.user.nicknameRequired"));
        user.setPhone(trimToNull(form.getPhone()));
        user.setReaderNo(trimToNull(form.getReaderNo()));
        user.setAvatarUrl(trimToNull(form.getAvatarUrl()));
        user.setManagedLocationPrefix(trimToNull(form.getManagedLocationPrefix()));
        user.setRole(form.getRole() == null ? UserRole.READER : form.getRole());
        user.setStatus(form.getStatus() == null ? AccountStatus.NORMAL : form.getStatus());
        user.setPermissionCodes(normalizePermissionCodes(permissionCodes));

        validateUser(user, existing);
        protectSelfAndLastAdmin(existing, user, currentUsername);
        applyPassword(user, existing, rawPassword);

        User saved = userRepository.save(user);
        operationLogService.record(i18n.get("log.module.user"), i18n.get("log.user.save"),
                previousUsername == null ? saved.getUsername() : previousUsername + " -> " + saved.getUsername());
        return saved;
    }

    @Transactional
    public void disable(Long id, String currentUsername) {
        User user = getEditable(id);
        protectDisableOrDelete(user, currentUsername);
        user.setStatus(AccountStatus.DISABLED);
        userRepository.save(user);
        operationLogService.record(i18n.get("log.module.user"), i18n.get("log.user.disable"), user.getUsername());
    }

    @Transactional
    public void enable(Long id) {
        User user = getEditable(id);
        user.setStatus(AccountStatus.NORMAL);
        userRepository.save(user);
        operationLogService.record(i18n.get("log.module.user"), i18n.get("log.user.enable"), user.getUsername());
    }

    @Transactional
    public void softDelete(Long id, String currentUsername) {
        User user = getEditable(id);
        protectDisableOrDelete(user, currentUsername);
        user.setDeleted(true);
        user.setStatus(AccountStatus.DISABLED);
        userRepository.save(user);
        operationLogService.record(i18n.get("log.module.user"), i18n.get("log.user.delete"), user.getUsername());
    }

    @Transactional
    public User restore(Long id) {
        User user = userRepository.findByIdAndDeletedTrue(id).orElseThrow();
        assertUniqueUserField(user, user.getUsername(), userRepository::findByUsername,
                value -> userRepository.existsByUsernameAndIdNot(value, user.getId()),
                "error.user.restoreDuplicateUsername", "error.user.restoreDuplicateUsername");
        assertUniqueUserField(user, user.getEmail(), userRepository::findByEmail,
                value -> userRepository.existsByEmailAndIdNot(value, user.getId()),
                "error.user.restoreDuplicateEmail", "error.user.restoreDuplicateEmail");
        user.setDeleted(false);
        user.setStatus(AccountStatus.NORMAL);
        User restored = userRepository.save(user);
        operationLogService.record(i18n.get("log.module.user"), i18n.get("log.user.restore"), restored.getUsername());
        return restored;
    }

    @Transactional
    public void purge(Long id) {
        User user = userRepository.findByIdAndDeletedTrue(id).orElseThrow();
        userRepository.delete(user);
        operationLogService.record(i18n.get("log.module.user"), i18n.get("log.user.purge"), user.getUsername());
    }

    public String normalizePermissionCodes(List<String> permissionCodes) {
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

    public Set<String> selectedPermissionCodes(User user) {
        if (!StringUtils.hasText(user.getPermissionCodes())) {
            return Set.of();
        }
        return Arrays.stream(user.getPermissionCodes().split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateUser(User user, User existing) {
        if (!USERNAME_PATTERN.matcher(user.getUsername()).matches()) {
            throw new IllegalArgumentException(i18n.get("error.user.usernameFormat"));
        }
        if (!EMAIL_PATTERN.matcher(user.getEmail()).matches()) {
            throw new IllegalArgumentException(i18n.get("error.user.emailFormat"));
        }
        assertUniqueUserField(user, user.getUsername(), userRepository::findByUsername,
                value -> userRepository.existsByUsernameAndIdNot(value, user.getId()),
                "error.user.duplicateUsername", "error.user.duplicateUsernameInTrash");
        assertUniqueUserField(user, user.getEmail(), userRepository::findByEmail,
                value -> userRepository.existsByEmailAndIdNot(value, user.getId()),
                "error.user.duplicateEmail", "error.user.duplicateEmailInTrash");
        if (user.getRole() == UserRole.LIBRARIAN
                && !user.getPermissionCodes().contains(Permission.DATA_ALL.name())
                && !StringUtils.hasText(user.getManagedLocationPrefix())) {
            throw new IllegalArgumentException(i18n.get("error.user.locationRequired"));
        }
    }

    private void assertUniqueUserField(User user,
                                       String value,
                                       Function<String, Optional<User>> findByValue,
                                       Predicate<String> existsByValueAndDifferentId,
                                       String activeMessageKey,
                                       String deletedMessageKey) {
        boolean duplicate = user.getId() == null ? findByValue.apply(value).isPresent() : existsByValueAndDifferentId.test(value);
        if (!duplicate) {
            return;
        }
        boolean deletedConflict = findByValue.apply(value)
                .filter(conflict -> !conflict.getId().equals(user.getId()))
                .filter(User::isDeleted)
                .isPresent();
        throw new IllegalArgumentException(i18n.get(deletedConflict ? deletedMessageKey : activeMessageKey));
    }

    private void protectSelfAndLastAdmin(User existing, User updated, String currentUsername) {
        if (existing == null) {
            return;
        }
        if (isCurrentUser(existing, currentUsername)
                && (updated.getRole() != existing.getRole() || updated.getStatus() != AccountStatus.NORMAL)) {
            throw new IllegalStateException(i18n.get("error.user.selfOperation"));
        }
        if (isProtectedAdmin(existing)
                && (updated.getRole() != UserRole.SUPER_ADMIN || updated.getStatus() != AccountStatus.NORMAL)) {
            throw new IllegalStateException(i18n.get("error.user.lastAdmin"));
        }
    }

    private void protectDisableOrDelete(User user, String currentUsername) {
        if (isCurrentUser(user, currentUsername)) {
            throw new IllegalStateException(i18n.get("error.user.selfOperation"));
        }
        if (isProtectedAdmin(user)) {
            throw new IllegalStateException(i18n.get("error.user.lastAdmin"));
        }
    }

    private void applyPassword(User user, User existing, String rawPassword) {
        if (existing != null && !StringUtils.hasText(rawPassword)) {
            return;
        }
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 8) {
            throw new IllegalArgumentException(i18n.get("error.user.passwordRequired"));
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
    }

    private boolean isProtectedAdmin(User user) {
        return user.getRole() == UserRole.SUPER_ADMIN
                && user.getStatus() == AccountStatus.NORMAL
                && userRepository.countByRoleAndStatusAndDeletedFalse(UserRole.SUPER_ADMIN, AccountStatus.NORMAL) <= 1;
    }

    private boolean isCurrentUser(User user, String currentUsername) {
        return StringUtils.hasText(currentUsername) && user.getUsername().equals(currentUsername);
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

    private String required(String value, String messageKey) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(i18n.get(messageKey));
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
