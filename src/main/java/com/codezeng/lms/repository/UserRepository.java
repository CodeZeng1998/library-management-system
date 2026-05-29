package com.codezeng.lms.repository;

import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsernameOrEmail(String username, String email);

    Optional<User> findByIdAndDeletedFalse(Long id);

    Optional<User> findByIdAndDeletedTrue(Long id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    org.springframework.data.domain.Page<User> findByDeletedTrue(org.springframework.data.domain.Pageable pageable);

    long countByRoleAndStatusAndDeletedFalse(UserRole role, AccountStatus status);
}
