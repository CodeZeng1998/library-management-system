package com.codezeng.lms;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.service.BookSearchCriteria;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.BorrowService;
import com.codezeng.lms.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BookWorkflowTests {

    @Autowired
    private BookService bookService;

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private FineRecordRepository fineRecordRepository;

    @Autowired
    private ReservationRecordRepository reservationRecordRepository;

    @Test
    void rejectsDuplicateIsbnOnSave() {
        bookService.save(book("9781000000001", 2, 2));

        assertThatThrownBy(() -> bookService.save(book("9781000000001", 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISBN");
    }

    @Test
    void rejectsInventoryLowerThanActiveCopies() {
        Book saved = bookService.save(book("9781000000002", 3, 1));
        Book update = book("9781000000002", 1, 1);
        update.setId(saved.getId());

        assertThatThrownBy(() -> bookService.save(update))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("在借");
    }

    @Test
    void rejectsBorrowingSameBookTwiceBeforeReturn() {
        Book book = bookRepository.save(book("9781000000003", 2, 2));
        Reader reader = readerRepository.save(reader("R-T-0001"));

        borrowService.borrowBook(book.getId(), reader.getId());

        assertThatThrownBy(() -> borrowService.borrowBook(book.getId(), reader.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void lostReturnIsIdempotentAndDoesNotDuplicateFine() {
        Book book = bookRepository.save(book("9781000000004", 1, 1));
        Reader reader = readerRepository.save(reader("R-T-0002"));
        Long recordId = borrowService.borrowBook(book.getId(), reader.getId()).getId();

        borrowService.returnBook(recordId, false, true);
        long fineCountAfterFirstReturn = fineRecordRepository.count();
        borrowService.returnBook(recordId, false, true);

        org.assertj.core.api.Assertions.assertThat(fineRecordRepository.count()).isEqualTo(fineCountAfterFirstReturn);
    }

    @Test
    void rejectsDeletingBookWithActiveReservation() {
        Book book = bookRepository.save(book("9781000000005", 1, 0));
        Reader reader = readerRepository.save(reader("R-T-0003", "reader3@example.com", "S20260003"));

        reservationService.reserve(book.getId(), reader.getId());

        assertThatThrownBy(() -> bookService.softDelete(book.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("预约");
    }

    @Test
    void softDeletedReservationDoesNotBlockBookDeletion() {
        Book book = bookRepository.save(book("9781000000006", 1, 0));
        Reader reader = readerRepository.save(reader("R-T-0004", "reader4@example.com", "S20260004"));
        ReservationRecord reservation = reservationService.reserve(book.getId(), reader.getId());
        reservation.setDeleted(true);
        reservationRecordRepository.save(reservation);

        bookService.softDelete(book.getId());

        org.assertj.core.api.Assertions.assertThat(bookRepository.findById(book.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void restoresSoftDeletedBookWhenNoActiveIsbnConflictExists() {
        Book book = bookRepository.save(book("9781000000007", 1, 1));
        bookService.softDelete(book.getId());

        bookService.restore(book.getId());

        assertThat(bookRepository.findById(book.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(bookRepository.findByIsbnAndDeletedFalse("9781000000007")).isPresent();
    }

    @Test
    void rejectsCreatingBookWhenIsbnIsHeldByTrashRecord() {
        Book book = bookService.save(book("9781000000008", 1, 1));
        bookService.softDelete(book.getId());

        assertThatThrownBy(() -> bookService.save(book("9781000000008", 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("回收站");
    }

    @Test
    void importsBookCsvWithQuotedMultilineFields() throws Exception {
        String csv = "ISBN,Title,Author,Publisher,Category,TotalQuantity,AvailableQuantity,Location,Price\n"
                + "9781000000099,\"Architecture\nPatterns\",Test Author,Test Publisher,Engineering,2,2,Stack A,49.00\n";

        bookService.importCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(bookRepository.findByIsbnAndDeletedFalse("9781000000099"))
                .hasValueSatisfying(book -> assertThat(book.getTitle()).isEqualTo("Architecture\nPatterns"));
    }

    @Test
    void savesSearchesAndExportsBookTags() throws Exception {
        Book tagged = book("9781000000110", 1, 1);
        tagged.setTags(bookService.resolveTags(null, "AI;Reading List"));
        bookService.save(tagged);

        assertThat(bookService.search("Reading List", org.springframework.data.domain.Pageable.unpaged()))
                .anySatisfy(book -> assertThat(book.getIsbn()).isEqualTo("9781000000110"));
        assertThat(bookService.exportCsv()).contains("AI;Reading List");
    }

    @Test
    void exportsBooksWithCurrentSearchCriteria() {
        Book matched = book("9781000000112", 1, 1);
        matched.setTitle("Filtered Architecture");
        bookService.save(matched);
        Book skipped = book("9781000000113", 1, 1);
        skipped.setTitle("Unrelated Catalog");
        bookService.save(skipped);

        BookSearchCriteria criteria = new BookSearchCriteria();
        criteria.setKeyword("Architecture");

        String csv = bookService.exportCsv(criteria);

        assertThat(csv).contains("Filtered Architecture");
        assertThat(csv).doesNotContain("Unrelated Catalog");
    }

    @Test
    void batchSoftDeleteReportsSuccessAndFailures() {
        Book deletable = bookService.save(book("9781000000114", 1, 1));
        Book blocked = bookService.save(book("9781000000115", 1, 1));
        Reader reader = readerRepository.save(reader("R-T-0006", "reader6@example.com", "S20260006"));
        borrowService.borrowBook(blocked.getId(), reader.getId());

        BookService.BatchOperationResult result = bookService.batchSoftDelete(List.of(deletable.getId(), blocked.getId()));

        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(bookRepository.findById(deletable.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(bookRepository.findById(blocked.getId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void importsBookCsvTags() throws Exception {
        String csv = "ISBN,Title,Author,Publisher,Category,Tags,TotalQuantity,AvailableQuantity,Location,Price\n"
                + "9781000000111,Tagged Import,Test Author,Test Publisher,Engineering,\"Course;Featured\",2,2,Stack A,49.00\n";

        bookService.importCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(bookRepository.findByIsbnAndDeletedFalse("9781000000111"))
                .hasValueSatisfying(book -> assertThat(book.getTags())
                        .extracting("name")
                        .contains("Course", "Featured"));
    }

    @Test
    void reportsBookCsvConflictWhenIsbnIsHeldByTrashRecord() throws Exception {
        Book book = bookService.save(book("9781000000101", 1, 1));
        bookService.softDelete(book.getId());
        String csv = "ISBN,Title,Author,Publisher,Category,TotalQuantity,AvailableQuantity,Location,Price\n"
                + "9781000000101,Duplicate Trash Book,Test Author,Test Publisher,Engineering,1,1,Stack A,49.00\n";

        var result = bookService.importCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.allErrorsText()).containsAnyOf("回收站", "trash");
    }

    @Test
    void rejectsMalformedBookCsvWithUnclosedQuotedField() {
        String csv = "ISBN,Title,Author,Publisher,Category,TotalQuantity\n"
                + "9781000000100,\"Broken title,Test Author,Test Publisher,Engineering,2\n";

        assertThatThrownBy(() -> bookService.importCsv(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unclosed quoted field");
    }

    @Test
    void purgesSoftDeletedBookWithoutHistoryAndReleasesIsbn() {
        Book book = bookService.save(book("9781000000102", 1, 1));
        bookService.softDelete(book.getId());

        bookService.purge(book.getId());

        assertThat(bookRepository.findById(book.getId())).isEmpty();
        assertThat(bookService.save(book("9781000000102", 1, 1)).getId()).isNotNull();
    }

    @Test
    void rejectsPurgingBookWithBorrowHistory() {
        Book book = bookService.save(book("9781000000103", 1, 1));
        Reader reader = readerRepository.save(reader("R-T-0005", "reader5@example.com", "S20260005"));
        Long recordId = borrowService.borrowBook(book.getId(), reader.getId()).getId();
        borrowService.returnBook(recordId, false, false);
        bookService.softDelete(book.getId());

        assertThatThrownBy(() -> bookService.purge(book.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
        assertThat(bookRepository.findById(book.getId())).isPresent();
    }

    private Book book(String isbn, int totalQuantity, int availableQuantity) {
        Book book = new Book();
        book.setIsbn(isbn);
        book.setTitle("Engineering Handbook");
        book.setAuthor("Test Author");
        book.setPublisher("Test Publisher");
        book.setTotalQuantity(totalQuantity);
        book.setAvailableQuantity(availableQuantity);
        book.setPrice(new BigDecimal("42.00"));
        book.setLocation("Test Hall A-01");
        return book;
    }

    private Reader reader(String readerNo) {
        return reader(readerNo, "reader@example.com", "S20260000");
    }

    private Reader reader(String readerNo, String email, String identityNo) {
        Reader reader = new Reader();
        reader.setReaderNo(readerNo);
        reader.setName("Test Reader");
        reader.setGender("N/A");
        reader.setPhone("13900000000");
        reader.setEmail(email);
        reader.setIdentityNo(identityNo);
        reader.setReaderType(ReaderType.STUDENT);
        reader.setMemberLevel(MemberLevel.NORMAL);
        reader.setStatus(AccountStatus.NORMAL);
        reader.setDepositAmount(new BigDecimal("100.00"));
        return reader;
    }
}
