package com.codezeng.lms.security;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.UserRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
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
        Authentication authentication = currentAuthentication();
        if (!isBranchScoped(authentication)) {
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

    public Specification<Book> deletedBookScope() {
        return (root, query, builder) -> currentLocationPrefix()
                .map(prefix -> builder.and(
                        builder.isTrue(root.get("deleted")),
                        builder.like(root.get("location"), prefix + "%")))
                .orElseGet(() -> builder.isTrue(root.get("deleted")));
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

    public boolean canAccess(Book book) {
        return book != null && canAccessLocation(book.getLocation());
    }

    public boolean canAccess(BorrowRecord record) {
        return record != null && record.getBook() != null && canAccess(record.getBook());
    }

    public boolean canAccess(ReservationRecord record) {
        return record != null && record.getBook() != null && canAccess(record.getBook());
    }

    public boolean canAccess(FineRecord fine) {
        if (fine != null && !isBranchScoped(currentAuthentication())) {
            return true;
        }
        return fine != null
                && fine.getBorrowRecord() != null
                && fine.getBorrowRecord().getBook() != null
                && canAccess(fine.getBorrowRecord().getBook());
    }

    public void requireAccess(Book book) {
        if (!canAccess(book)) {
            throw new AccessDeniedException("No permission to access data outside the managed branch.");
        }
    }

    public void requireAccess(BorrowRecord record) {
        if (!canAccess(record)) {
            throw new AccessDeniedException("No permission to access data outside the managed branch.");
        }
    }

    public void requireAccess(ReservationRecord record) {
        if (!canAccess(record)) {
            throw new AccessDeniedException("No permission to access data outside the managed branch.");
        }
    }

    public void requireAccess(FineRecord fine) {
        if (!canAccess(fine)) {
            throw new AccessDeniedException("No permission to access data outside the managed branch.");
        }
    }

    private boolean canAccessLocation(String location) {
        Authentication authentication = currentAuthentication();
        if (!isBranchScoped(authentication)) {
            return true;
        }
        return currentLocationPrefix()
                .map(prefix -> StringUtils.hasText(location) && location.startsWith(prefix))
                .orElse(true);
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean isBranchScoped(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && hasAuthority(authentication, "ROLE_" + UserRole.LIBRARIAN.name())
                && !hasAuthority(authentication, Permission.DATA_ALL.name());
    }

    private boolean hasAuthority(Authentication authentication, String authorityName) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authorityName.equals(authority.getAuthority()));
    }
}
