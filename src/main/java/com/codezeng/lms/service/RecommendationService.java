package com.codezeng.lms.service;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.security.DataScopeService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final BookRepository bookRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final ReaderRepository readerRepository;
    private final DataScopeService dataScopeService;

    public RecommendationService(BookRepository bookRepository,
                                 BorrowRecordRepository borrowRecordRepository,
                                 ReaderRepository readerRepository,
                                 DataScopeService dataScopeService) {
        this.bookRepository = bookRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.readerRepository = readerRepository;
        this.dataScopeService = dataScopeService;
    }

    public RecommendationDashboard dashboard(Long readerId, Long bookId) {
        List<Book> books = bookRepository.findAll(dataScopeService.bookScope(), Sort.by("title"));
        List<BorrowRecord> records = borrowRecordRepository.findAll(dataScopeService.borrowRecordScope());
        Optional<Reader> selectedReader = readerId == null ? Optional.empty() : readerRepository.findById(readerId);
        Optional<Book> selectedBook = bookId == null
                ? defaultSelectedBook(books, records)
                : bookRepository.findById(bookId).filter(dataScopeService::canAccess);

        return new RecommendationDashboard(
                selectedReader.orElse(null),
                selectedBook.orElse(null),
                collaborativeRecommendations(selectedReader.orElse(null), books, records),
                borrowedTogether(selectedBook.orElse(null), records),
                preferredNewBooks(selectedReader.orElse(null), books, records),
                hotRanking(records, 7),
                hotRanking(records, 30),
                hotRanking(records, 365),
                subjectLists(books));
    }

    private List<BookRecommendation> collaborativeRecommendations(Reader reader, List<Book> books, List<BorrowRecord> records) {
        if (reader == null) {
            return fallbackHotBooks(books);
        }

        Set<Long> readerBookIds = records.stream()
                .filter(record -> sameId(record.getReader(), reader))
                .map(record -> record.getBook().getId())
                .collect(Collectors.toSet());
        if (readerBookIds.isEmpty()) {
            return fallbackHotBooks(books);
        }

        Map<Long, Long> similarReaderScores = records.stream()
                .filter(record -> !sameId(record.getReader(), reader))
                .filter(record -> readerBookIds.contains(record.getBook().getId()))
                .collect(Collectors.groupingBy(record -> record.getReader().getId(), Collectors.counting()));

        Map<Long, Book> bookIndex = books.stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        Map<Long, Long> scores = new LinkedHashMap<>();
        for (BorrowRecord record : records) {
            Long similarScore = similarReaderScores.get(record.getReader().getId());
            Long bookId = record.getBook().getId();
            if (similarScore != null && !readerBookIds.contains(bookId) && bookIndex.containsKey(bookId)) {
                scores.merge(bookId, similarScore, Long::sum);
            }
        }

        Map<Long, Long> categoryPreferences = categoryPreferences(reader, records);
        return scores.entrySet().stream()
                .map(entry -> {
                    Book book = bookIndex.get(entry.getKey());
                    long categoryBoost = categoryScore(book, categoryPreferences);
                    return new BookRecommendation(book, entry.getValue() + categoryBoost, "recommendation.reason.collaborative");
                })
                .sorted(recommendationComparator())
                .limit(8)
                .toList();
    }

    private List<BookRecommendation> borrowedTogether(Book selectedBook, List<BorrowRecord> records) {
        if (selectedBook == null) {
            return List.of();
        }

        Set<Long> readerIds = records.stream()
                .filter(record -> sameId(record.getBook(), selectedBook))
                .map(record -> record.getReader().getId())
                .collect(Collectors.toSet());
        Map<Long, Book> books = records.stream()
                .map(BorrowRecord::getBook)
                .filter(dataScopeService::canAccess)
                .collect(Collectors.toMap(Book::getId, Function.identity(), (first, second) -> first));

        return records.stream()
                .filter(record -> readerIds.contains(record.getReader().getId()))
                .filter(record -> !sameId(record.getBook(), selectedBook))
                .collect(Collectors.groupingBy(record -> record.getBook().getId(), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> books.containsKey(entry.getKey()))
                .map(entry -> new BookRecommendation(books.get(entry.getKey()), entry.getValue(), "recommendation.reason.alsoBorrowed"))
                .sorted(recommendationComparator())
                .limit(8)
                .toList();
    }

    private List<BookRecommendation> preferredNewBooks(Reader reader, List<Book> books, List<BorrowRecord> records) {
        Map<Long, Long> categoryPreferences = categoryPreferences(reader, records);
        Set<Long> borrowedIds = reader == null ? Set.of() : records.stream()
                .filter(record -> sameId(record.getReader(), reader))
                .map(record -> record.getBook().getId())
                .collect(Collectors.toSet());

        return books.stream()
                .filter(book -> !borrowedIds.contains(book.getId()))
                .filter(book -> categoryPreferences.isEmpty() || categoryScore(book, categoryPreferences) > 0)
                .sorted(Comparator
                        .comparingLong((Book book) -> categoryScore(book, categoryPreferences)).reversed()
                        .thenComparing(Comparator.comparing(RecommendationService::createdAtSafe).reversed())
                        .thenComparing(Book::getBorrowCount, Comparator.reverseOrder()))
                .limit(8)
                .map(book -> new BookRecommendation(book, Math.max(1, categoryScore(book, categoryPreferences)), "recommendation.reason.newPreferred"))
                .toList();
    }

    private List<RankingBook> hotRanking(List<BorrowRecord> records, int days) {
        LocalDate since = LocalDate.now().minusDays(days - 1L);
        Map<Long, Book> books = records.stream()
                .map(BorrowRecord::getBook)
                .filter(dataScopeService::canAccess)
                .collect(Collectors.toMap(Book::getId, Function.identity(), (first, second) -> first));

        return records.stream()
                .filter(record -> !record.getBorrowDate().isBefore(since))
                .collect(Collectors.groupingBy(record -> record.getBook().getId(), Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> books.containsKey(entry.getKey()))
                .map(entry -> new RankingBook(books.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator
                        .comparingLong(RankingBook::borrowCount).reversed()
                        .thenComparing(item -> item.book().getTitle()))
                .limit(10)
                .toList();
    }

    private List<SubjectBookList> subjectLists(List<Book> books) {
        return books.stream()
                .filter(book -> book.getCategory() != null)
                .collect(Collectors.groupingBy(book -> book.getCategory().getId(), LinkedHashMap::new, Collectors.toList()))
                .values().stream()
                .map(categoryBooks -> {
                    BookCategory category = categoryBooks.get(0).getCategory();
                    List<BookRecommendation> recommendations = categoryBooks.stream()
                            .sorted(Comparator
                                    .comparingLong(Book::getBorrowCount).reversed()
                                    .thenComparing(Comparator.comparing(RecommendationService::createdAtSafe).reversed()))
                            .limit(5)
                            .map(book -> new BookRecommendation(book, Math.max(1, book.getBorrowCount()), "recommendation.reason.subject"))
                            .toList();
                    return new SubjectBookList(category.getName(), recommenderName(category), recommendations);
                })
                .sorted(Comparator.comparing(SubjectBookList::subject))
                .limit(8)
                .toList();
    }

    private Map<Long, Long> categoryPreferences(Reader reader, List<BorrowRecord> records) {
        if (reader == null) {
            return Map.of();
        }
        return records.stream()
                .filter(record -> sameId(record.getReader(), reader))
                .map(record -> record.getBook().getCategory())
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(BookCategory::getId, Collectors.counting()));
    }

    private List<BookRecommendation> fallbackHotBooks(List<Book> books) {
        return books.stream()
                .sorted(Comparator
                        .comparingLong(Book::getBorrowCount).reversed()
                        .thenComparing(Comparator.comparing(RecommendationService::createdAtSafe).reversed()))
                .limit(8)
                .map(book -> new BookRecommendation(book, Math.max(1, book.getBorrowCount()), "recommendation.reason.popular"))
                .toList();
    }

    private Optional<Book> defaultSelectedBook(List<Book> books, List<BorrowRecord> records) {
        Set<Long> borrowedBookIds = records.stream()
                .map(record -> record.getBook().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return books.stream()
                .filter(book -> borrowedBookIds.contains(book.getId()))
                .max(Comparator.comparingLong(Book::getBorrowCount));
    }

    private long categoryScore(Book book, Map<Long, Long> categoryPreferences) {
        if (book.getCategory() == null) {
            return 0;
        }
        return categoryPreferences.getOrDefault(book.getCategory().getId(), 0L);
    }

    private Comparator<BookRecommendation> recommendationComparator() {
        return Comparator
                .comparingLong(BookRecommendation::score).reversed()
                .thenComparing(item -> item.book().getBorrowCount(), Comparator.reverseOrder())
                .thenComparing(item -> item.book().getTitle());
    }

    private boolean sameId(Reader left, Reader right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private boolean sameId(Book left, Book right) {
        return left != null && right != null && Objects.equals(left.getId(), right.getId());
    }

    private String recommenderName(BookCategory category) {
        return category.getName() + " Faculty Reading Group";
    }

    private static LocalDateTime createdAtSafe(Book book) {
        return book.getCreateTime() == null ? LocalDateTime.MIN : book.getCreateTime();
    }

    public record RecommendationDashboard(
            Reader selectedReader,
            Book selectedBook,
            List<BookRecommendation> collaborative,
            List<BookRecommendation> borrowedTogether,
            List<BookRecommendation> newArrivals,
            List<RankingBook> weeklyHot,
            List<RankingBook> monthlyHot,
            List<RankingBook> yearlyHot,
            List<SubjectBookList> subjectLists) {
    }

    public record BookRecommendation(Book book, long score, String reason) {
    }

    public record RankingBook(Book book, long borrowCount) {
    }

    public record SubjectBookList(String subject, String recommender, List<BookRecommendation> books) {
    }
}
