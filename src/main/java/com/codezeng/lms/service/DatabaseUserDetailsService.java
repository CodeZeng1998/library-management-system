package com.codezeng.lms.service;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.repository.UserRepository;
import com.codezeng.lms.security.Permission;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        if (user.isDeleted()) {
            throw new UsernameNotFoundException("Account has been deleted.");
        }
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities(user))
                .disabled(user.getStatus() == AccountStatus.DISABLED || user.getStatus() == AccountStatus.PENDING)
                .accountLocked(user.getStatus() == AccountStatus.FROZEN || user.getStatus() == AccountStatus.BLACKLISTED)
                .build();
    }

    private Set<SimpleGrantedAuthority> authorities(User user) {
        Set<String> codes = new LinkedHashSet<>();
        codes.add("ROLE_" + user.getRole().name());
        for (Permission permission : Permission.values()) {
            if (permission.grantedByDefault(user.getRole())) {
                codes.add(permission.name());
            }
        }
        if (StringUtils.hasText(user.getPermissionCodes())) {
            codes.addAll(Arrays.stream(user.getPermissionCodes().split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        addImpliedPermissions(codes);
        return codes.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void addImpliedPermissions(Set<String> codes) {
        if (codes.contains(Permission.BOOK_EDIT.name())
                || codes.contains(Permission.BOOK_DELETE.name())
                || codes.contains(Permission.BOOK_IMPORT.name())) {
            codes.add(Permission.BOOK_VIEW.name());
        }
        if (codes.contains(Permission.READER_EDIT.name()) || codes.contains(Permission.READER_DELETE.name())) {
            codes.add(Permission.READER_VIEW.name());
        }
        if (codes.contains(Permission.FINE_PAY.name()) || codes.contains(Permission.FINE_WAIVE.name())) {
            codes.add(Permission.FINE_VIEW.name());
        }
    }
}
