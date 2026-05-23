package com.codezeng.lms.security;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.User;
import com.codezeng.lms.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
public class DataScopeService {

    private final UserRepository userRepository;

    public DataScopeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<String> currentLocationPrefix() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities().stream()
                .anyMatch(authority -> Permission.DATA_ALL.name().equals(authority.getAuthority()))) {
            return Optional.empty();
        }
        return userRepository.findByUsernameOrEmail(authentication.getName(), authentication.getName())
                .map(User::getManagedLocationPrefix)
                .filter(StringUtils::hasText);
    }

    public Specification<Book> bookScope() {
        return (root, query, builder) -> currentLocationPrefix()
                .map(prefix -> builder.and(
                        builder.isFalse(root.get("deleted")),
                        builder.like(root.get("location"), prefix + "%")))
                .orElseGet(() -> builder.isFalse(root.get("deleted")));
    }

    public Specification<BorrowRecord> borrowRecordScope() {
        return (root, query, builder) -> currentLocationPrefix()
                .map(prefix -> builder.and(
                        builder.isFalse(root.get("deleted")),
                        builder.like(root.join("book").get("location"), prefix + "%")))
                .orElseGet(() -> builder.isFalse(root.get("deleted")));
    }

    public Specification<ReservationRecord> reservationScope() {
        return (root, query, builder) -> currentLocationPrefix()
                .map(prefix -> builder.and(
                        builder.isFalse(root.get("deleted")),
                        builder.like(root.join("book").get("location"), prefix + "%")))
                .orElseGet(() -> builder.isFalse(root.get("deleted")));
    }

    public Specification<FineRecord> fineScope() {
        return (root, query, builder) -> currentLocationPrefix()
                .map(prefix -> builder.and(
                        builder.isFalse(root.get("deleted")),
                        builder.like(root.join("borrowRecord").join("book").get("location"), prefix + "%")))
                .orElseGet(() -> builder.isFalse(root.get("deleted")));
    }
}
