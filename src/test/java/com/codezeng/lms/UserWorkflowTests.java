package com.codezeng.lms;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.UserRepository;
import com.codezeng.lms.security.Permission;
import com.codezeng.lms.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserWorkflowTests {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void rejectsInvalidUserIdentityFields() {
        User invalidUsername = user("x", "valid@example.com", UserRole.READER);
        assertThatThrownBy(() -> userService.save(invalidUsername, "password-1", List.of(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);

        User invalidEmail = user("reader_valid", "not-an-email", UserRole.READER);
        assertThatThrownBy(() -> userService.save(invalidEmail, "password-1", List.of(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBranchLibrarianWithoutDataScope() {
        User librarian = user("branch_librarian", "branch@example.com", UserRole.LIBRARIAN);

        assertThatThrownBy(() -> userService.save(librarian, "password-1", List.of(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);

        User globalLibrarian = user("global_librarian", "global@example.com", UserRole.LIBRARIAN);
        User saved = userService.save(globalLibrarian, "password-1", List.of(Permission.DATA_ALL.name()), "admin");

        assertThat(saved.getPermissionCodes()).contains(Permission.DATA_ALL.name());
    }

    @Test
    void protectsLastActiveSuperAdminFromDisableOrDelete() {
        User admin = userService.save(user("root_admin", "root@example.com", UserRole.SUPER_ADMIN), "password-1", List.of(), "admin");

        assertThatThrownBy(() -> userService.disable(admin.getId(), "another-admin"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> userService.softDelete(admin.getId(), "another-admin"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void keepsExistingPasswordWhenEditingWithoutNewPassword() {
        User saved = userService.save(user("reader_edit", "reader.edit@example.com", UserRole.READER), "password-1", List.of(), "admin");
        String encoded = saved.getPassword();
        saved.setNickname("Updated Reader");

        userService.save(saved, "", List.of(), "admin");

        assertThat(userRepository.findById(saved.getId()).orElseThrow().getPassword()).isEqualTo(encoded);
    }

    @Test
    void rejectsCreatingUserWhenUsernameOrEmailIsHeldByTrashRecord() {
        User saved = userService.save(user("reader_trash", "reader.trash@example.com", UserRole.READER), "password-1", List.of(), "admin");
        userService.softDelete(saved.getId(), "admin");

        assertThatThrownBy(() -> userService.save(user("reader_trash", "reader.new@example.com", UserRole.READER), "password-1", List.of(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> userService.save(user("reader_new", "reader.trash@example.com", UserRole.READER), "password-1", List.of(), "admin"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoresSoftDeletedUserAsActiveAccount() {
        User saved = userService.save(user("reader_restore", "reader.restore@example.com", UserRole.READER), "password-1", List.of(), "admin");
        userService.softDelete(saved.getId(), "admin");

        User restored = userService.restore(saved.getId());

        assertThat(restored.isDeleted()).isFalse();
        assertThat(restored.getStatus()).isEqualTo(AccountStatus.NORMAL);
    }

    @Test
    void purgesSoftDeletedUserAndReleasesIdentity() {
        User saved = userService.save(user("reader_purge", "reader.purge@example.com", UserRole.READER), "password-1", List.of(), "admin");
        userService.softDelete(saved.getId(), "admin");

        userService.purge(saved.getId());

        assertThat(userRepository.findById(saved.getId())).isEmpty();
        assertThat(userService.save(user("reader_purge", "reader.purge@example.com", UserRole.READER), "password-1", List.of(), "admin").getId())
                .isNotNull();
    }

    private User user(String username, String email, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname("Test User");
        user.setPhone("13900000000");
        user.setRole(role);
        user.setStatus(AccountStatus.NORMAL);
        return user;
    }
}
