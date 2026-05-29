package com.codezeng.lms;

import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.BorrowService;
import com.codezeng.lms.service.ReaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ReaderWorkflowTests {

    @Autowired
    private ReaderService readerService;

    @Autowired
    private BookService bookService;

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    @Autowired
    private FineRecordRepository fineRecordRepository;

    @Test
    void rejectsDuplicateReaderNoEmailAndIdentity() {
        readerService.save(reader("R-T-1001", "reader.1001@example.com", "ID-T-1001"));

        assertThatThrownBy(() -> readerService.save(reader("R-T-1001", "reader.1002@example.com", "ID-T-1002")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("读者编号");
        assertThatThrownBy(() -> readerService.save(reader("R-T-1002", "reader.1001@example.com", "ID-T-1003")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("邮箱");
        assertThatThrownBy(() -> readerService.save(reader("R-T-1003", "reader.1003@example.com", "ID-T-1001")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("证件号");
    }

    @Test
    void rejectsMissingCoreFieldsAndNegativeDeposit() {
        Reader missingName = reader("R-T-2001", "reader.2001@example.com", "ID-T-2001");
        missingName.setName(" ");
        assertThatThrownBy(() -> readerService.save(missingName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("姓名");

        Reader negativeDeposit = reader("R-T-2002", "reader.2002@example.com", "ID-T-2002");
        negativeDeposit.setDepositAmount(new BigDecimal("-1.00"));
        assertThatThrownBy(() -> readerService.save(negativeDeposit))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("押金");
    }

    @Test
    void preventsDeletingReaderWithActiveLoan() {
        Reader reader = readerService.save(reader("R-T-3001", "reader.3001@example.com", "ID-T-3001"));
        Long bookId = bookService.save(book("9782000000001")).getId();

        borrowService.borrowBook(bookId, reader.getId());

        assertThatThrownBy(() -> readerService.softDelete(reader.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未归还");
    }

    @Test
    void softDeletedLoanAndFineDoNotBlockBorrowing() {
        Reader reader = readerService.save(reader("R-T-4001", "reader.4001@example.com", "ID-T-4001"));
        Long firstBookId = bookService.save(book("9782000000002")).getId();
        Long secondBookId = bookService.save(book("9782000000003")).getId();
        BorrowRecord deletedLoan = borrowService.borrowBook(firstBookId, reader.getId());
        deletedLoan.setDeleted(true);
        borrowRecordRepository.save(deletedLoan);
        FineRecord deletedFine = new FineRecord();
        deletedFine.setReader(reader);
        deletedFine.setBorrowRecord(deletedLoan);
        deletedFine.setReason("Deleted fine");
        deletedFine.setAmount(new BigDecimal("10.00"));
        deletedFine.setStatus(FineStatus.UNPAID);
        deletedFine.setDeleted(true);
        fineRecordRepository.save(deletedFine);

        borrowService.borrowBook(secondBookId, reader.getId());
    }

    @Test
    void restoresSoftDeletedReaderWhenNoActiveIdentityConflictExists() {
        Reader reader = readerService.save(reader("R-T-5001", "reader.5001@example.com", "ID-T-5001"));
        readerService.softDelete(reader.getId());

        readerService.restore(reader.getId());

        assertThat(readerService.search("R-T-5001", org.springframework.data.domain.Pageable.unpaged()))
                .anySatisfy(restored -> assertThat(restored.isDeleted()).isFalse());
    }

    @Test
    void rejectsCreatingReaderWhenUniqueIdentifiersAreHeldByTrashRecord() {
        Reader reader = readerService.save(reader("R-T-5002", "reader.5002@example.com", "ID-T-5002"));
        readerService.softDelete(reader.getId());

        assertThatThrownBy(() -> readerService.save(reader("R-T-5002", "reader.5003@example.com", "ID-T-5003")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("回收站");
        assertThatThrownBy(() -> readerService.save(reader("R-T-5003", "reader.5002@example.com", "ID-T-5003")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("回收站");
        assertThatThrownBy(() -> readerService.save(reader("R-T-5004", "reader.5004@example.com", "ID-T-5002")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("回收站");
    }

    @Test
    void importsReaderCsvWithQuotedMultilineFields() throws Exception {
        String csv = "ReaderNo,Name,Gender,Phone,Email,IdentityNo,ReaderType,MemberLevel,Status,Deposit\n"
                + "R-T-CSV-01,\"Reader\nOne\",N/A,13900000001,reader.csv.01@example.com,ID-T-CSV-01,STUDENT,NORMAL,NORMAL,100.00\n";

        readerService.importCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(readerService.search("R-T-CSV-01", org.springframework.data.domain.Pageable.unpaged()))
                .anySatisfy(reader -> {
                    assertThat(reader.getReaderNo()).isEqualTo("R-T-CSV-01");
                    assertThat(reader.getName()).isEqualTo("Reader\nOne");
                });
    }

    @Test
    void reportsReaderCsvConflictWhenIdentifiersAreHeldByTrashRecord() throws Exception {
        Reader reader = readerService.save(reader("R-T-CSV-02", "reader.csv.02@example.com", "ID-T-CSV-02"));
        readerService.softDelete(reader.getId());
        String csv = "ReaderNo,Name,Gender,Phone,Email,IdentityNo,ReaderType,MemberLevel,Status,Deposit\n"
                + "R-T-CSV-02,Reader Two,N/A,13900000002,reader.csv.02@example.com,ID-T-CSV-02,STUDENT,NORMAL,NORMAL,100.00\n";

        var result = readerService.importCsv(csv.getBytes(StandardCharsets.UTF_8));

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.allErrorsText()).containsAnyOf("回收站", "trash");
    }

    @Test
    void rejectsHeaderOnlyReaderCsv() {
        String csv = "ReaderNo,Name,Gender,Phone,Email,IdentityNo,ReaderType,MemberLevel,Status,Deposit\n";

        assertThatThrownBy(() -> readerService.importCsv(csv.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one data row");
    }

    @Test
    void purgesSoftDeletedReaderWithoutHistoryAndReleasesIdentifiers() {
        Reader reader = readerService.save(reader("R-T-6001", "reader.6001@example.com", "ID-T-6001"));
        readerService.softDelete(reader.getId());

        readerService.purge(reader.getId());

        assertThat(readerService.save(reader("R-T-6001", "reader.6001@example.com", "ID-T-6001")).getId()).isNotNull();
    }

    @Test
    void rejectsPurgingReaderWithBorrowHistory() {
        Reader reader = readerService.save(reader("R-T-6002", "reader.6002@example.com", "ID-T-6002"));
        Long bookId = bookService.save(book("9782000000004")).getId();
        Long recordId = borrowService.borrowBook(bookId, reader.getId()).getId();
        borrowService.returnBook(recordId, false, false);
        readerService.softDelete(reader.getId());

        assertThatThrownBy(() -> readerService.purge(reader.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
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

    private com.codezeng.lms.domain.Book book(String isbn) {
        com.codezeng.lms.domain.Book book = new com.codezeng.lms.domain.Book();
        book.setIsbn(isbn);
        book.setTitle("Reader Lifecycle Handbook");
        book.setAuthor("Test Author");
        book.setPublisher("Test Publisher");
        book.setTotalQuantity(1);
        book.setAvailableQuantity(1);
        book.setPrice(new BigDecimal("39.00"));
        book.setLocation("Test Hall B-01");
        return book;
    }
}
