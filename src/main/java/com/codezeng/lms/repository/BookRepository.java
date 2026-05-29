package com.codezeng.lms.repository;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.domain.BookTag;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    @EntityGraph(attributePaths = {"category"})
    Optional<Book> findByIdAndDeletedFalse(Long id);

    Optional<Book> findByIdAndDeletedTrue(Long id);

    Optional<Book> findByIsbnAndDeletedFalse(String isbn);

    long countByCategoryAndDeletedFalse(BookCategory category);

    @Query("""
            select category.id, count(book.id)
            from Book book join book.category category
            where book.deleted = false and category.id in :categoryIds
            group by category.id
            """)
    List<Object[]> countActiveBooksByCategoryIds(@Param("categoryIds") Collection<Long> categoryIds);

    long countByTagsContainingAndDeletedFalse(BookTag tag);

    @Query("""
            select tag.id, count(book.id)
            from Book book join book.tags tag
            where book.deleted = false and tag.id in :tagIds
            group by tag.id
            """)
    List<Object[]> countActiveBooksByTagIds(@Param("tagIds") Collection<Long> tagIds);

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    boolean existsByIsbnAndDeletedFalseAndIdNot(String isbn, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id and b.deleted = false")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"category"})
    Page<Book> findAll(org.springframework.data.jpa.domain.Specification<Book> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"category", "tags"})
    List<Book> findAll(org.springframework.data.jpa.domain.Specification<Book> spec);

    Page<Book> findByDeletedFalseAndTitleContainingIgnoreCaseOrDeletedFalseAndAuthorContainingIgnoreCaseOrDeletedFalseAndIsbnContainingIgnoreCase(
            String title, String author, String isbn, Pageable pageable);

    Page<Book> findByDeletedFalse(Pageable pageable);

    long countByDeletedFalse();

    List<Book> findTop10ByDeletedFalseOrderByBorrowCountDesc();

    @Query("select distinct b.location from Book b where b.deleted = false and b.location is not null and b.location <> '' order by b.location")
    List<String> findDistinctLocations();

    @Query("""
            select distinct b.location
            from Book b
            where b.deleted = false
              and b.location is not null
              and b.location <> ''
              and b.location like concat(:prefix, '%')
            order by b.location
            """)
    List<String> findDistinctLocationsByPrefix(@Param("prefix") String prefix);
}
