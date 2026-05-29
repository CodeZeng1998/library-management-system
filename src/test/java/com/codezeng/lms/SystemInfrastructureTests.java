package com.codezeng.lms;

import com.codezeng.lms.config.DataInitializer;
import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.OperationLog;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.SystemConfig;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.security.PreventDuplicateSubmit;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.NotificationRepository;
import com.codezeng.lms.repository.OperationLogRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.repository.SystemConfigRepository;
import com.codezeng.lms.service.BorrowService;
import com.codezeng.lms.service.CsvImportGuard;
import com.codezeng.lms.service.OperationLogQueryService;
import com.codezeng.lms.service.ReservationService;
import com.codezeng.lms.service.ReminderService;
import com.codezeng.lms.service.SystemConfigService;
import com.codezeng.lms.web.BorrowController;
import com.codezeng.lms.web.BookController;
import com.codezeng.lms.web.ReaderController;
import com.codezeng.lms.web.ReaderPortalController;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemInfrastructureTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private SystemConfigRepository systemConfigRepository;

    @Autowired
    private OperationLogQueryService operationLogQueryService;

    @Autowired
    private OperationLogRepository operationLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CsvImportGuard csvImportGuard;

    @Autowired
    private BorrowService borrowService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ReaderRepository readerRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    @Autowired
    private ReservationRecordRepository reservationRecordRepository;

    @Autowired
    private FineRecordRepository fineRecordRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ReminderService reminderService;

    @Test
    void rejectsInvalidBorrowLimitConfig() {
        SystemConfig config = systemConfigRepository.findByConfigKey("borrow.normal.max_books").orElseThrow();

        assertThatThrownBy(() -> systemConfigService.update(config.getId(), "0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Borrow limit");
    }

    @Test
    void normalizesValidMoneyConfig() {
        SystemConfig config = systemConfigRepository.findByConfigKey("fine.overdue.per_day").orElseThrow();

        systemConfigService.update(config.getId(), "0.50");

        assertThat(systemConfigRepository.findById(config.getId()).orElseThrow().getConfigValue()).isEqualTo("0.5");
    }

    @Test
    void exportRowLimitIsConfigurableAndValidated() {
        SystemConfig config = systemConfigRepository.findByConfigKey("export.max_rows").orElseThrow();

        systemConfigService.update(config.getId(), "2500");

        assertThat(systemConfigService.exportMaxRows()).isEqualTo(2500);
        assertThatThrownBy(() -> systemConfigService.update(config.getId(), "50"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Export row limit");
    }

    @Test
    void configuredFineRateDrivesReturnAndPreviewAmounts() {
        systemConfigService.update(systemConfigRepository.findByConfigKey("fine.overdue.per_day").orElseThrow().getId(), "0.25");
        Book book = bookRepository.save(book("9784000000001", 1, 1));
        Reader reader = readerRepository.save(reader("R-CFG-FINE", "reader.cfg.fine@example.com", "ID-CFG-FINE"));
        BorrowRecord record = borrowService.borrowBook(book.getId(), reader.getId());
        record.setDueDate(LocalDate.now().minusDays(4));
        borrowRecordRepository.saveAndFlush(record);

        assertThat(borrowService.estimateFine(record, false, false)).isEqualByComparingTo("1.00");

        BorrowRecord returned = borrowService.returnBook(record.getId(), false, false);

        assertThat(returned.getFineAmount()).isEqualByComparingTo("1.00");
    }

    @Test
    void configuredNormalBorrowLimitDrivesBorrowValidationAndReaderCards() {
        systemConfigService.update(systemConfigRepository.findByConfigKey("borrow.normal.max_books").orElseThrow().getId(), "1");
        Reader reader = readerRepository.save(reader("R-CFG-LIMIT", "reader.cfg.limit@example.com", "ID-CFG-LIMIT"));
        Book firstBook = bookRepository.save(book("9784000000002", 1, 1));
        Book secondBook = bookRepository.save(book("9784000000003", 1, 1));

        borrowService.borrowBook(firstBook.getId(), reader.getId());

        assertThat(borrowService.maxBorrowBooks(reader)).isEqualTo(1);
        assertThatThrownBy(() -> borrowService.borrowBook(secondBook.getId(), reader.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void configuredReservationRulesDriveQueueLimitAndExpiryWindows() {
        systemConfigService.update(systemConfigRepository.findByConfigKey("reservation.max_queue").orElseThrow().getId(), "1");
        systemConfigService.update(systemConfigRepository.findByConfigKey("reservation.waiting_hold_days").orElseThrow().getId(), "2");
        systemConfigService.update(systemConfigRepository.findByConfigKey("reservation.pickup_window_hours").orElseThrow().getId(), "6");
        Book book = bookRepository.save(book("9784000000004", 1, 0));
        Reader firstReader = readerRepository.save(reader("R-CFG-QUEUE-1", "reader.cfg.queue1@example.com", "ID-CFG-QUEUE-1"));
        Reader secondReader = readerRepository.save(reader("R-CFG-QUEUE-2", "reader.cfg.queue2@example.com", "ID-CFG-QUEUE-2"));

        LocalDateTime beforeReserve = LocalDateTime.now();
        ReservationRecord reservation = reservationService.reserve(book.getId(), firstReader.getId());

        assertThat(reservation.getExpiresAt()).isBetween(beforeReserve.plusDays(2), LocalDateTime.now().plusDays(2).plusSeconds(2));
        assertThatThrownBy(() -> reservationService.reserve(book.getId(), secondReader.getId()))
                .isInstanceOf(IllegalStateException.class);

        book.setAvailableQuantity(1);
        bookRepository.saveAndFlush(book);
        LocalDateTime beforeLock = LocalDateTime.now();
        ReservationRecord notified = reservationService.lockNextReservation(book).orElseThrow();

        assertThat(notified.getStatus()).isEqualTo(ReservationStatus.NOTIFIED);
        assertThat(notified.getExpiresAt()).isBetween(beforeLock.plusHours(6), LocalDateTime.now().plusHours(6).plusSeconds(2));
    }

    @Test
    void maintenanceSwitchDisablesAutomatedReminders() {
        systemConfigService.update(systemConfigRepository.findByConfigKey("maintenance.enabled").orElseThrow().getId(), "false");
        systemConfigService.update(systemConfigRepository.findByConfigKey("maintenance.due_reminder_days").orElseThrow().getId(), "1");
        Book book = bookRepository.save(book("9784000000005", 1, 1));
        Reader reader = readerRepository.save(reader("R-MAINT-OFF", "reader.maint.off@example.com", "ID-MAINT-OFF"));
        BorrowRecord record = borrowService.borrowBook(book.getId(), reader.getId());
        record.setDueDate(LocalDate.now().plusDays(1));
        borrowRecordRepository.saveAndFlush(record);

        assertThat(reminderService.runDueReminders()).isZero();
        assertThat(notificationRepository.countByReaderAndStatusAndDeletedFalse(reader, NotificationStatus.UNREAD)).isZero();
    }

    @Test
    void dueReminderUsesConfiguredWindowAndDoesNotDuplicateMessages() {
        systemConfigService.update(systemConfigRepository.findByConfigKey("maintenance.due_reminder_days").orElseThrow().getId(), "2");
        Book book = bookRepository.save(book("9784000000006", 1, 1));
        Reader reader = readerRepository.save(reader("R-MAINT-DUE", "reader.maint.due@example.com", "ID-MAINT-DUE"));
        BorrowRecord record = borrowService.borrowBook(book.getId(), reader.getId());
        record.setDueDate(LocalDate.now().plusDays(2));
        borrowRecordRepository.saveAndFlush(record);

        assertThat(reminderService.runDueReminders()).isEqualTo(1);
        assertThat(reminderService.runDueReminders()).isZero();
        assertThat(notificationRepository.countByReaderAndStatusAndDeletedFalse(reader, NotificationStatus.UNREAD)).isEqualTo(1);
    }

    @Test
    void overdueMaintenanceCreatesFineAndFreezesByConfiguredThreshold() {
        systemConfigService.update(systemConfigRepository.findByConfigKey("maintenance.overdue_freeze_days").orElseThrow().getId(), "2");
        Book book = bookRepository.save(book("9784000000007", 1, 1));
        Reader reader = readerRepository.save(reader("R-MAINT-OVERDUE", "reader.maint.overdue@example.com", "ID-MAINT-OVERDUE"));
        BorrowRecord record = borrowService.borrowBook(book.getId(), reader.getId());
        record.setDueDate(LocalDate.now().minusDays(3));
        borrowRecordRepository.saveAndFlush(record);

        ReminderService.OverdueMaintenanceResult result = reminderService.runOverdueMaintenance();

        assertThat(result.notificationsSent()).isEqualTo(1);
        assertThat(result.finesCreated()).isEqualTo(1);
        assertThat(result.readersFrozen()).isEqualTo(1);
        assertThat(borrowRecordRepository.findById(record.getId()).orElseThrow().getStatus()).isEqualTo(BorrowStatus.OVERDUE);
        assertThat(readerRepository.findById(reader.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.FROZEN);
        assertThat(fineRecordRepository.existsByBorrowRecordAndStatus(record, FineStatus.UNPAID)).isTrue();
    }

    @Test
    void exportsFilteredOperationLogs() {
        operationLogRepository.save(new OperationLog("auditor", "Risk", "Review", "Export target", "127.0.0.1"));
        operationLogRepository.save(new OperationLog("auditor", "Other", "Review", "Should be filtered", "127.0.0.1"));

        String csv = operationLogQueryService.exportCsv("target", "Risk", "127.0.0.1");

        assertThat(csv).contains("Time,User,Module,Operation,IP,Detail");
        assertThat(csv).contains("Export target");
        assertThat(csv).doesNotContain("Should be filtered");
    }

    @Test
    void cleanupOldOperationLogsSoftDeletesAndKeepsAuditTrail() {
        OperationLog oldLog = new OperationLog("auditor", "Risk", "Review", "Old target", "127.0.0.1");
        operationLogRepository.saveAndFlush(oldLog);
        OperationLog freshLog = new OperationLog("auditor", "Risk", "Review", "Fresh target", "127.0.0.1");
        operationLogRepository.saveAndFlush(freshLog);
        jdbcTemplate.update("update operation_log set create_time = ?, update_time = ? where id = ?",
                LocalDateTime.now().minusDays(200), LocalDateTime.now().minusDays(200), oldLog.getId());
        jdbcTemplate.update("update operation_log set create_time = ?, update_time = ? where id = ?",
                LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(10), freshLog.getId());

        int removed = operationLogQueryService.cleanupOlderThan(180);

        assertThat(removed).isEqualTo(1);
        assertThat(operationLogRepository.findById(oldLog.getId()).orElseThrow().isDeleted()).isTrue();
        assertThat(operationLogRepository.findById(freshLog.getId()).orElseThrow().isDeleted()).isFalse();
        assertThat(operationLogQueryService.exportCsv("Old target", "Risk", null)).doesNotContain("Old target");
        assertThat(operationLogQueryService.exportCsv("Cleanup logs", "Operation logs", null)).contains("Retention days: 180");
    }

    @Test
    void cleanupOldOperationLogsRejectsUnsafeRetentionWindow() {
        assertThatThrownBy(() -> operationLogQueryService.cleanupOlderThan(7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 30 and 3650");
    }

    @Test
    void csvImportGuardRejectsNonCsvContentType() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "books.csv",
                "application/pdf",
                "ISBN,Title\n9781000000110,Security".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> csvImportGuard.validate(upload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("content type");
    }

    @Test
    void csvImportGuardRejectsEmptyAndTooLargeRowSets() {
        assertThatThrownBy(() -> csvImportGuard.validateRows(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one data row");
        assertThatThrownBy(() -> csvImportGuard.validateRows(5_002))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5000");
    }

    @Test
    void bootstrapAndDemoSeedsRequireExplicitPasswords() {
        DataInitializer initializer = new DataInitializer();
        MockEnvironment emptyEnvironment = new MockEnvironment();

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                initializer, "seedBootstrapAdmin", null, null, emptyEnvironment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LMS_BOOTSTRAP_ADMIN_PASSWORD");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(
                initializer, "seedUsers", null, null, emptyEnvironment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LMS_DEMO_PASSWORD");
    }

    @Test
    void readerPortalMutationsPreventDuplicateSubmit() throws NoSuchMethodException {
        List<java.lang.reflect.Method> mutationMethods = List.of(
                ReaderPortalController.class.getDeclaredMethod("renew", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                ReaderPortalController.class.getDeclaredMethod("cancelReservation", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                ReaderPortalController.class.getDeclaredMethod("reserveBook", Long.class, String.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                ReaderPortalController.class.getDeclaredMethod("payFine", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                ReaderPortalController.class.getDeclaredMethod("markNotificationRead", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class));

        assertThat(mutationMethods)
                .allSatisfy(method -> assertThat(method.isAnnotationPresent(PreventDuplicateSubmit.class)).isTrue());
    }

    @Test
    void borrowMutationsPreventDuplicateSubmit() throws NoSuchMethodException {
        List<java.lang.reflect.Method> mutationMethods = List.of(
                BorrowController.class.getDeclaredMethod("borrow", Long.class, Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                BorrowController.class.getDeclaredMethod("returnBook", Long.class, boolean.class, boolean.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                BorrowController.class.getDeclaredMethod("renew", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                BorrowController.class.getDeclaredMethod("overdueScan", org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                BorrowController.class.getDeclaredMethod("checkout", String.class, String.class),
                BorrowController.class.getDeclaredMethod("batchReturn", BorrowController.ReturnBatchRequest.class),
                BorrowController.class.getDeclaredMethod("renewByScan", String.class),
                BorrowController.class.getDeclaredMethod("renewById", Long.class));

        assertThat(mutationMethods)
                .allSatisfy(method -> assertThat(method.isAnnotationPresent(PreventDuplicateSubmit.class)).isTrue());
    }

    @Test
    void trashRestoreMutationsPreventDuplicateSubmit() throws NoSuchMethodException {
        List<java.lang.reflect.Method> mutationMethods = List.of(
                BookController.class.getDeclaredMethod("restore", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class),
                ReaderController.class.getDeclaredMethod("restore", Long.class, org.springframework.web.servlet.mvc.support.RedirectAttributes.class));

        assertThat(mutationMethods)
                .allSatisfy(method -> assertThat(method.isAnnotationPresent(PreventDuplicateSubmit.class)).isTrue());
    }

    @Test
    @WithMockUser(authorities = "NOTIFICATION_VIEW")
    void duplicateSubmitAllowsDifferentBusinessParameters() throws Exception {
        HttpSession session = mockMvc.perform(post("/notifications/batch")
                        .with(csrf())
                        .param("ids", "1")
                        .param("action", "read"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/notifications/batch")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .with(csrf())
                        .param("ids", "2")
                        .param("action", "read"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(authorities = "NOTIFICATION_VIEW")
    void duplicateSubmitBlocksSameBusinessParameters() throws Exception {
        HttpSession session = mockMvc.perform(post("/notifications/batch")
                        .with(csrf())
                        .param("ids", "3")
                        .param("action", "read"))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/notifications/batch")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .with(csrf())
                        .param("ids", "3")
                        .param("action", "read"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    @WithMockUser(authorities = "BORROW_MANAGE")
    void duplicateSubmitReturnsJsonForAjaxApi() throws Exception {
        String payload = "{\"items\":[{\"recordId\":9001,\"damaged\":false,\"lost\":false}]}";
        HttpSession session = mockMvc.perform(post("/borrow/api/returns/batch")
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        mockMvc.perform(post("/borrow/api/returns/batch")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
    }

    @Test
    @WithMockUser(authorities = {"FINE_VIEW", "FINE_PAY"})
    void fineActionReturnsFlashErrorWhenRecordUnavailable() throws Exception {
        mockMvc.perform(post("/fines/999999/pay").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/fines"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    }

    @Test
    @WithMockUser(authorities = "RESERVATION_MANAGE")
    void reservationCancelReturnsFlashErrorWhenRecordUnavailable() throws Exception {
        mockMvc.perform(post("/reservations/999999/cancel").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/reservations"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("error"));
    }

    private Book book(String isbn, int totalQuantity, int availableQuantity) {
        Book book = new Book();
        book.setIsbn(isbn);
        book.setTitle("Config Driven Handbook");
        book.setAuthor("Test Author");
        book.setPublisher("Test Publisher");
        book.setTotalQuantity(totalQuantity);
        book.setAvailableQuantity(availableQuantity);
        book.setPrice(new BigDecimal("50.00"));
        book.setLocation("Config Lab A-01");
        return book;
    }

    private Reader reader(String readerNo, String email, String identityNo) {
        Reader reader = new Reader();
        reader.setReaderNo(readerNo);
        reader.setName("Config Reader");
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
