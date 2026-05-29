package com.codezeng.lms;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.UserRepository;
import com.codezeng.lms.service.BookService;
import com.codezeng.lms.service.BorrowService;
import com.codezeng.lms.service.ReaderService;
import com.codezeng.lms.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardWorkflowTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookService bookService;

    @Autowired
    private ReaderService readerService;

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @WithMockUser(authorities = {"BOOK_VIEW", "READER_VIEW", "BORROW_MANAGE", "RESERVATION_MANAGE"})
    void exportsDashboardSnapshotAsCsv() throws Exception {
        Book book = bookService.save(book());
        Reader reader = readerService.save(reader());
        borrowService.borrowBook(book.getId(), reader.getId());

        byte[] body = mockMvc.perform(get("/dashboard/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=dashboard-snapshot.csv"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Section,Metric,Value,Extra");
        assertThat(csv).contains("Summary");
        assertThat(csv).contains("Active Loans");
        assertThat(csv).contains("Borrow Trend Daily");
    }

    @Test
    @WithMockUser(authorities = "FINE_VIEW")
    void exportsFilteredFinesAsCsv() throws Exception {
        Book includedBook = bookService.save(book("9783000000101", "Fine Export Target"));
        Reader includedReader = readerService.save(reader("R-FINE-0001", "fine.reader.1@example.com", "FINE20260001"));
        BorrowRecord includedBorrow = borrowService.borrowBook(includedBook.getId(), includedReader.getId());
        borrowService.returnBook(includedBorrow.getId(), true, false);

        Book excludedBook = bookService.save(book("9783000000102", "Fine Export Other"));
        Reader excludedReader = readerService.save(reader("R-FINE-0002", "fine.reader.2@example.com", "FINE20260002"));
        BorrowRecord excludedBorrow = borrowService.borrowBook(excludedBook.getId(), excludedReader.getId());
        borrowService.returnBook(excludedBorrow.getId(), true, false);

        byte[] body = mockMvc.perform(get("/fines/export").param("keyword", "Target"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=fines.csv"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Reader No,Reader Name,Book Title,ISBN,Reason,Amount,Status");
        assertThat(csv).contains("Fine Export Target");
        assertThat(csv).doesNotContain("Fine Export Other");
    }

    @Test
    @WithMockUser(authorities = "BORROW_MANAGE")
    void exportsFilteredBorrowRecordsAsCsv() throws Exception {
        Book includedBook = bookService.save(book("9783000000201", "Borrow Export Target"));
        Reader includedReader = readerService.save(reader("R-BORROW-0001", "borrow.reader.1@example.com", "BORROW20260001"));
        borrowService.borrowBook(includedBook.getId(), includedReader.getId());

        Book excludedBook = bookService.save(book("9783000000202", "Borrow Export Other"));
        Reader excludedReader = readerService.save(reader("R-BORROW-0002", "borrow.reader.2@example.com", "BORROW20260002"));
        borrowService.borrowBook(excludedBook.getId(), excludedReader.getId());

        byte[] body = mockMvc.perform(get("/borrow/export").param("keyword", "Target"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=borrow-records.csv"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Book Title,ISBN,Reader No,Reader Name,Borrow Date,Due Date,Return Date,Status");
        assertThat(csv).contains("Borrow Export Target");
        assertThat(csv).doesNotContain("Borrow Export Other");
    }

    @Test
    @WithMockUser(authorities = "RESERVATION_MANAGE")
    void exportsFilteredReservationsAsCsv() throws Exception {
        Book includedBook = bookService.save(book("9783000000301", "Reservation Export Target", 1, 0));
        Reader includedReader = readerService.save(reader("R-RES-0001", "reservation.reader.1@example.com", "RES20260001"));
        reservationService.reserve(includedBook.getId(), includedReader.getId());

        Book excludedBook = bookService.save(book("9783000000302", "Reservation Export Other", 1, 0));
        Reader excludedReader = readerService.save(reader("R-RES-0002", "reservation.reader.2@example.com", "RES20260002"));
        reservationService.reserve(excludedBook.getId(), excludedReader.getId());

        byte[] body = mockMvc.perform(get("/reservations/export").param("keyword", "Target"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=reservations.csv"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Book Title,ISBN,Reader No,Reader Name,Status,Queue Position");
        assertThat(csv).contains("Reservation Export Target");
        assertThat(csv).doesNotContain("Reservation Export Other");
    }

    @Test
    @WithMockUser(authorities = "RECOMMENDATION_VIEW")
    void exportsRecommendationDashboardAsCsv() throws Exception {
        Book seedBook = bookService.save(book("9783000000401", "Recommendation Seed"));
        Book targetBook = bookService.save(book("9783000000402", "Recommendation Target"));
        Reader firstReader = readerService.save(reader("R-REC-0001", "recommendation.reader.1@example.com", "REC20260001"));
        Reader similarReader = readerService.save(reader("R-REC-0002", "recommendation.reader.2@example.com", "REC20260002"));
        borrowService.borrowBook(seedBook.getId(), firstReader.getId());
        borrowService.borrowBook(seedBook.getId(), similarReader.getId());
        borrowService.borrowBook(targetBook.getId(), similarReader.getId());

        byte[] body = mockMvc.perform(get("/recommendations/export").param("readerId", String.valueOf(firstReader.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=recommendations.csv"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Section,Book Title,ISBN,Score,Reason");
        assertThat(csv).contains("Recommendation Target");
    }

    @Test
    @WithMockUser(username = "portal_export_reader", authorities = "ROLE_READER")
    void readerPortalExportsOnlyCurrentReadersActivity() throws Exception {
        Reader currentReader = readerService.save(reader("R-PORTAL-0001", "portal.reader.1@example.com", "PORTAL20260001"));
        Reader otherReader = readerService.save(reader("R-PORTAL-0002", "portal.reader.2@example.com", "PORTAL20260002"));
        userRepository.save(user("portal_export_reader", "portal.user@example.com", currentReader.getReaderNo()));
        Book ownBook = bookService.save(book("9783000000501", "Portal Export Own"));
        Book otherBook = bookService.save(book("9783000000502", "Portal Export Other"));
        borrowService.borrowBook(ownBook.getId(), currentReader.getId());
        borrowService.borrowBook(otherBook.getId(), otherReader.getId());

        byte[] body = mockMvc.perform(get("/portal/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=reader-activity.csv"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String csv = new String(body, StandardCharsets.UTF_8);
        assertThat(csv).contains("Time,Type,Status,Title,ISBN,Detail");
        assertThat(csv).contains("Portal Export Own");
        assertThat(csv).doesNotContain("Portal Export Other");
    }

    private Book book() {
        return book("9783000000001", "Dashboard Handbook");
    }

    private Book book(String isbn, String title) {
        return book(isbn, title, 2, 2);
    }

    private Book book(String isbn, String title, int totalQuantity, int availableQuantity) {
        Book book = new Book();
        book.setIsbn(isbn);
        book.setTitle(title);
        book.setAuthor("Test Author");
        book.setPublisher("Test Publisher");
        book.setTotalQuantity(totalQuantity);
        book.setAvailableQuantity(availableQuantity);
        book.setPrice(new BigDecimal("49.00"));
        book.setLocation("Dashboard Hall C-01");
        return book;
    }

    private Reader reader() {
        return reader("R-DASH-0001", "dashboard.reader@example.com", "DASH20260001");
    }

    private Reader reader(String readerNo, String email, String identityNo) {
        Reader reader = new Reader();
        reader.setReaderNo(readerNo);
        reader.setName("Dashboard Reader");
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

    private User user(String username, String email, String readerNo) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("test-password"));
        user.setNickname("Portal Export Reader");
        user.setPhone("13900000001");
        user.setReaderNo(readerNo);
        user.setRole(UserRole.READER);
        user.setStatus(AccountStatus.NORMAL);
        return user;
    }
}
