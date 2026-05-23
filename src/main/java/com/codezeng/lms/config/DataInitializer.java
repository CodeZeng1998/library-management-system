package com.codezeng.lms.config;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.domain.BorrowRecord;
import com.codezeng.lms.domain.FineRecord;
import com.codezeng.lms.domain.LocalizedText;
import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.ReservationRecord;
import com.codezeng.lms.domain.SystemConfig;
import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.BorrowStatus;
import com.codezeng.lms.domain.enums.FineStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.NotificationChannel;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.domain.enums.ReservationStatus;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.BorrowRecordRepository;
import com.codezeng.lms.repository.FineRecordRepository;
import com.codezeng.lms.repository.LocalizedTextRepository;
import com.codezeng.lms.repository.NotificationRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.ReservationRecordRepository;
import com.codezeng.lms.repository.SystemConfigRepository;
import com.codezeng.lms.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedData(UserRepository userRepository,
                               BookCategoryRepository categoryRepository,
                               BookRepository bookRepository,
                               ReaderRepository readerRepository,
                               BorrowRecordRepository borrowRecordRepository,
                               ReservationRecordRepository reservationRecordRepository,
                               NotificationRepository notificationRepository,
                               FineRecordRepository fineRecordRepository,
                               LocalizedTextRepository localizedTextRepository,
                               SystemConfigRepository systemConfigRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            seedUsers(userRepository, passwordEncoder);
            seedBooks(categoryRepository, bookRepository, localizedTextRepository);
            seedReaders(readerRepository);
            seedBorrowRecords(bookRepository, readerRepository, borrowRecordRepository);
            seedReservations(bookRepository, readerRepository, reservationRecordRepository);
            seedNotifications(readerRepository, notificationRepository);
            seedFines(readerRepository, borrowRecordRepository, fineRecordRepository);
            seedConfigs(systemConfigRepository);
        };
    }

    private void seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        createUser(userRepository, passwordEncoder, "admin", "admin@example.com", "系统管理员", "13800000001", UserRole.SUPER_ADMIN, AccountStatus.NORMAL, "admin123");
        createUser(userRepository, passwordEncoder, "librarian", "librarian@example.com", "图书管理员", "13800000002", UserRole.LIBRARIAN, AccountStatus.NORMAL, "librarian123");
        createUser(userRepository, passwordEncoder, "assistant1", "assistant1@example.com", "流通台一号", "13800000003", UserRole.LIBRARIAN, AccountStatus.NORMAL, "assistant123");
        createUser(userRepository, passwordEncoder, "librarian2", "librarian2@example.com", "少儿馆馆员", "13800000004", UserRole.LIBRARIAN, AccountStatus.NORMAL, "librarian123");
        createUser(userRepository, passwordEncoder, "reader_demo", "reader.demo@example.com", "读者体验账号", "13800000005", UserRole.READER, AccountStatus.NORMAL, "reader123");
        createUser(userRepository, passwordEncoder, "disabled_user", "disabled@example.com", "停用账号样例", "13800000006", UserRole.READER, AccountStatus.DISABLED, "disabled123");
    }

    private void seedBooks(BookCategoryRepository categoryRepository,
                           BookRepository bookRepository,
                           LocalizedTextRepository localizedTextRepository) {
        BookCategory literature = createCategory(categoryRepository, "文学");
        BookCategory technology = createCategory(categoryRepository, "计算机");
        BookCategory history = createCategory(categoryRepository, "历史");
        BookCategory economy = createCategory(categoryRepository, "经济管理");
        BookCategory children = createCategory(categoryRepository, "少儿读物");
        BookCategory science = createCategory(categoryRepository, "自然科学");
        seedCategoryTranslations(localizedTextRepository, literature, "文學", "Literature");
        seedCategoryTranslations(localizedTextRepository, technology, "電腦", "Computer Science");
        seedCategoryTranslations(localizedTextRepository, history, "歷史", "History");
        seedCategoryTranslations(localizedTextRepository, economy, "經濟管理", "Economics & Management");
        seedCategoryTranslations(localizedTextRepository, children, "兒童讀物", "Children's Books");
        seedCategoryTranslations(localizedTextRepository, science, "自然科學", "Natural Sciences");

        createBook(bookRepository, "代码整洁之道", "9787115216878", "Robert C. Martin", "人民邮电出版社", technology, 5, 5, LocalDate.of(2010, 1, 1), "59.00", "总馆 A-01-03", 168);
        createBook(bookRepository, "Spring Boot 实战", "9787115487520", "Craig Walls", "人民邮电出版社", technology, 3, 3, LocalDate.of(2018, 9, 1), "79.00", "总馆 A-02-01", 132);
        createBook(bookRepository, "深入理解计算机系统", "9787111544937", "Randal E. Bryant", "机械工业出版社", technology, 2, 0, LocalDate.of(2021, 7, 1), "139.00", "总馆 A-02-05", 96);
        createBook(bookRepository, "算法导论", "9787111407010", "Thomas H. Cormen", "机械工业出版社", technology, 4, 2, LocalDate.of(2020, 8, 1), "128.00", "总馆 A-03-02", 88);
        createBook(bookRepository, "Python编程：从入门到实践", "9787115546081", "Eric Matthes", "人民邮电出版社", technology, 6, 4, LocalDate.of(2020, 10, 1), "89.00", "分馆A B-01-08", 121);
        createBook(bookRepository, "活着", "9787506365437", "余华", "作家出版社", literature, 5, 3, LocalDate.of(2012, 8, 1), "39.00", "总馆 C-01-01", 210);
        createBook(bookRepository, "百年孤独", "9787544253994", "加西亚·马尔克斯", "南海出版公司", literature, 4, 1, LocalDate.of(2017, 8, 1), "55.00", "总馆 C-02-09", 176);
        createBook(bookRepository, "三体", "9787536692930", "刘慈欣", "重庆出版社", literature, 8, 5, LocalDate.of(2022, 5, 1), "45.00", "分馆A C-03-06", 305);
        createBook(bookRepository, "万历十五年", "9787108009821", "黄仁宇", "生活·读书·新知三联书店", history, 3, 1, LocalDate.of(2019, 3, 1), "32.00", "总馆 D-01-02", 74);
        createBook(bookRepository, "枪炮、病菌与钢铁", "9787532772322", "贾雷德·戴蒙德", "上海译文出版社", history, 2, 0, LocalDate.of(2022, 1, 1), "69.00", "总馆 D-02-04", 64);
        createBook(bookRepository, "原则", "9787508684031", "Ray Dalio", "中信出版社", economy, 3, 2, LocalDate.of(2018, 1, 1), "98.00", "分馆B E-01-01", 58);
        createBook(bookRepository, "从0到1", "9787508649719", "Peter Thiel", "中信出版社", economy, 4, 3, LocalDate.of(2015, 1, 1), "45.00", "分馆B E-01-03", 91);
        createBook(bookRepository, "神奇校车：水的故事", "9787221091727", "Joanna Cole", "贵州人民出版社", children, 5, 4, LocalDate.of(2021, 6, 1), "18.00", "少儿馆 F-01-04", 43);
        createBook(bookRepository, "时间简史", "9787535732309", "Stephen Hawking", "湖南科学技术出版社", science, 4, 2, LocalDate.of(2018, 4, 1), "45.00", "总馆 G-01-01", 80);
    }

    private void seedReaders(ReaderRepository readerRepository) {
        createReader(readerRepository, "R202605230001", "张三", "男", "13800001001", "zhangsan@example.com", "S20260001", ReaderType.STUDENT, MemberLevel.NORMAL, AccountStatus.NORMAL, "100.00");
        createReader(readerRepository, "R202605230002", "李四", "女", "13800001002", "lisi@example.com", "S20260002", ReaderType.STUDENT, MemberLevel.VIP, AccountStatus.NORMAL, "150.00");
        createReader(readerRepository, "R202605230003", "王老师", "男", "13800001003", "wang.teacher@example.com", "T20260003", ReaderType.TEACHER, MemberLevel.SVIP, AccountStatus.NORMAL, "300.00");
        createReader(readerRepository, "R202605230004", "赵敏", "女", "13800001004", "zhaomin@example.com", "S20260004", ReaderType.STUDENT, MemberLevel.NORMAL, AccountStatus.NORMAL, "80.00");
        createReader(readerRepository, "R202605230005", "陈晨", "男", "13800001005", "chenchen@example.com", "P20260005", ReaderType.PUBLIC, MemberLevel.NORMAL, AccountStatus.NORMAL, "120.00");
        createReader(readerRepository, "R202605230006", "周晓", "女", "13800001006", "zhouxiao@example.com", "S20260006", ReaderType.STUDENT, MemberLevel.VIP, AccountStatus.FROZEN, "60.00");
        createReader(readerRepository, "R202605230007", "孙航", "男", "13800001007", "sunhang@example.com", "T20260007", ReaderType.TEACHER, MemberLevel.SVIP, AccountStatus.NORMAL, "300.00");
        createReader(readerRepository, "R202605230008", "吴桐", "女", "13800001008", "wutong@example.com", "S20260008", ReaderType.STUDENT, MemberLevel.NORMAL, AccountStatus.PENDING, "50.00");
    }

    private void seedBorrowRecords(BookRepository bookRepository,
                                   ReaderRepository readerRepository,
                                   BorrowRecordRepository borrowRecordRepository) {
        if (borrowRecordRepository.count() > 0) {
            return;
        }
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787115216878", "R202605230001", LocalDate.now().minusDays(4), LocalDate.now().plusDays(26), null, BorrowStatus.BORROWED, "0.00", 0);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787115487520", "R202605230002", LocalDate.now().minusDays(18), LocalDate.now().plusDays(42), null, BorrowStatus.BORROWED, "0.00", 1);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787506365437", "R202605230004", LocalDate.now().minusDays(45), LocalDate.now().minusDays(15), null, BorrowStatus.OVERDUE, "1.50", 0);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787536692930", "R202605230003", LocalDate.now().minusDays(12), LocalDate.now().plusDays(78), null, BorrowStatus.BORROWED, "0.00", 0);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787111407010", "R202605230005", LocalDate.now().minusDays(70), LocalDate.now().minusDays(40), LocalDate.now().minusDays(38), BorrowStatus.RETURNED, "0.20", 0);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787544253994", "R202605230002", LocalDate.now().minusDays(20), LocalDate.now().plusDays(40), null, BorrowStatus.BORROWED, "0.00", 0);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787535732309", "R202605230007", LocalDate.now().minusDays(90), LocalDate.now().minusDays(1), LocalDate.now(), BorrowStatus.DAMAGED, "22.50", 0);
        createBorrow(bookRepository, readerRepository, borrowRecordRepository, "9787508684031", "R202605230001", LocalDate.now().minusDays(110), LocalDate.now().minusDays(80), LocalDate.now().minusDays(75), BorrowStatus.LOST, "204.00", 0);
    }

    private void seedReservations(BookRepository bookRepository,
                                  ReaderRepository readerRepository,
                                  ReservationRecordRepository reservationRecordRepository) {
        if (reservationRecordRepository.count() > 0) {
            return;
        }
        createReservation(bookRepository, readerRepository, reservationRecordRepository, "9787111544937", "R202605230001", LocalDateTime.now().minusDays(2), LocalDateTime.now().plusDays(1), ReservationStatus.WAITING);
        createReservation(bookRepository, readerRepository, reservationRecordRepository, "9787111544937", "R202605230002", LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(2), ReservationStatus.WAITING);
        createReservation(bookRepository, readerRepository, reservationRecordRepository, "9787532772322", "R202605230004", LocalDateTime.now().minusHours(8), LocalDateTime.now().plusHours(40), ReservationStatus.NOTIFIED);
        createReservation(bookRepository, readerRepository, reservationRecordRepository, "9787115216878", "R202605230005", LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(7), ReservationStatus.EXPIRED);
        createReservation(bookRepository, readerRepository, reservationRecordRepository, "9787221091727", "R202605230008", LocalDateTime.now().minusDays(3), LocalDateTime.now().plusDays(1), ReservationStatus.CANCELLED);
    }

    private void seedNotifications(ReaderRepository readerRepository,
                                   NotificationRepository notificationRepository) {
        if (notificationRepository.count() > 0) {
            return;
        }
        createNotification(readerRepository, notificationRepository, "R202605230001", "借阅成功提醒", "您借阅的《代码整洁之道》应于近期归还，请留意到期时间。", NotificationStatus.UNREAD, LocalDateTime.now().minusHours(3), null);
        createNotification(readerRepository, notificationRepository, "R202605230002", "续借成功", "《Spring Boot 实战》已续借成功，新的应还日期已更新。", NotificationStatus.READ, LocalDateTime.now().minusDays(1), LocalDateTime.now().minusHours(20));
        createNotification(readerRepository, notificationRepository, "R202605230004", "逾期提醒", "您借阅的《活着》已逾期，请尽快归还并处理罚款。", NotificationStatus.UNREAD, LocalDateTime.now().minusHours(10), null);
        createNotification(readerRepository, notificationRepository, "R202605230004", "预约图书已到馆", "您预约的《枪炮、病菌与钢铁》已到馆，请在48小时内到馆取书。", NotificationStatus.UNREAD, LocalDateTime.now().minusHours(4), null);
        createNotification(readerRepository, notificationRepository, "R202605230005", "预约已过期", "您预约的《代码整洁之道》已过期，如仍需借阅请重新预约。", NotificationStatus.READ, LocalDateTime.now().minusDays(4), LocalDateTime.now().minusDays(3));
    }

    private void seedFines(ReaderRepository readerRepository,
                           BorrowRecordRepository borrowRecordRepository,
                           FineRecordRepository fineRecordRepository) {
        if (fineRecordRepository.count() > 0) {
            return;
        }
        Map<String, BorrowRecord> records = borrowRecordRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        record -> record.getBook().getIsbn() + "|" + record.getReader().getReaderNo() + "|" + record.getStatus().name(),
                        record -> record,
                        (first, second) -> first));
        createFine(readerRepository, fineRecordRepository, "R202605230004", records.get("9787506365437|R202605230004|OVERDUE"), "逾期归还", "1.50", FineStatus.UNPAID, null);
        createFine(readerRepository, fineRecordRepository, "R202605230007", records.get("9787535732309|R202605230007|DAMAGED"), "图书损坏", "22.50", FineStatus.UNPAID, null);
        createFine(readerRepository, fineRecordRepository, "R202605230001", records.get("9787508684031|R202605230001|LOST"), "图书丢失", "204.00", FineStatus.PAID, LocalDateTime.now().minusDays(70));
        createFine(readerRepository, fineRecordRepository, "R202605230005", records.get("9787111407010|R202605230005|RETURNED"), "逾期归还", "0.20", FineStatus.WAIVED, LocalDateTime.now().minusDays(37));
    }

    private void seedConfigs(SystemConfigRepository systemConfigRepository) {
        createConfig(systemConfigRepository, "borrow.normal.max_books", "3", "普通会员借阅上限", "普通会员最多可借图书数量");
        createConfig(systemConfigRepository, "fine.overdue.per_day", "0.10", "逾期日罚款", "每本书每天逾期罚款金额");
        createConfig(systemConfigRepository, "reservation.max_queue", "5", "单书预约人数上限", "每本书最多预约人数");
    }

    private void createConfig(SystemConfigRepository repository, String key, String value, String displayName, String description) {
        if (repository.findByConfigKey(key).isPresent()) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDisplayName(displayName);
        config.setDescription(description);
        repository.save(config);
    }

    private void createUser(UserRepository repository,
                            PasswordEncoder passwordEncoder,
                            String username,
                            String email,
                            String nickname,
                            String phone,
                            UserRole role,
                            AccountStatus status,
                            String rawPassword) {
        if (repository.existsByUsername(username)) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus(status);
        user.setLastLoginTime(LocalDateTime.now().minusDays(1));
        repository.save(user);
    }

    private BookCategory createCategory(BookCategoryRepository repository, String name) {
        return repository.findByNameAndDeletedFalse(name).orElseGet(() -> {
            BookCategory category = new BookCategory();
            category.setName(name);
            return repository.save(category);
        });
    }

    private void seedCategoryTranslations(LocalizedTextRepository repository,
                                          BookCategory category,
                                          String traditionalChinese,
                                          String english) {
        createLocalizedText(repository, "book_category", category.getId(), "name", "zh_CN", category.getName());
        createLocalizedText(repository, "book_category", category.getId(), "name", "zh_TW", traditionalChinese);
        createLocalizedText(repository, "book_category", category.getId(), "name", "en", english);
    }

    private void createLocalizedText(LocalizedTextRepository repository,
                                     String entityType,
                                     Long entityId,
                                     String fieldKey,
                                     String localeTag,
                                     String text) {
        if (repository.existsByEntityTypeAndEntityIdAndFieldKeyAndLocaleTag(entityType, entityId, fieldKey, localeTag)) {
            return;
        }
        LocalizedText localizedText = new LocalizedText();
        localizedText.setEntityType(entityType);
        localizedText.setEntityId(entityId);
        localizedText.setFieldKey(fieldKey);
        localizedText.setLocaleTag(localeTag);
        localizedText.setText(text);
        repository.save(localizedText);
    }

    private void createBook(BookRepository repository,
                            String title,
                            String isbn,
                            String author,
                            String publisher,
                            BookCategory category,
                            int totalQuantity,
                            int availableQuantity,
                            LocalDate publishDate,
                            String price,
                            String location,
                            long borrowCount) {
        if (repository.findByIsbnAndDeletedFalse(isbn).isPresent()) {
            return;
        }
        Book book = new Book();
        book.setTitle(title);
        book.setIsbn(isbn);
        book.setAuthor(author);
        book.setPublisher(publisher);
        book.setCategory(category);
        book.setTotalQuantity(totalQuantity);
        book.setAvailableQuantity(availableQuantity);
        book.setPublishDate(publishDate);
        book.setPrice(new BigDecimal(price));
        book.setLocation(location);
        book.setBorrowCount(borrowCount);
        book.setSummary("样例馆藏数据，用于演示检索、借阅、预约与罚款流程。");
        repository.save(book);
    }

    private void createReader(ReaderRepository repository,
                              String readerNo,
                              String name,
                              String gender,
                              String phone,
                              String email,
                              String identityNo,
                              ReaderType readerType,
                              MemberLevel memberLevel,
                              AccountStatus status,
                              String depositAmount) {
        if (repository.findByReaderNoAndDeletedFalse(readerNo).isPresent()) {
            return;
        }
        Reader reader = new Reader();
        reader.setReaderNo(readerNo);
        reader.setName(name);
        reader.setGender(gender);
        reader.setPhone(phone);
        reader.setEmail(email);
        reader.setIdentityNo(identityNo);
        reader.setReaderType(readerType);
        reader.setMemberLevel(memberLevel);
        reader.setStatus(status);
        reader.setDepositAmount(new BigDecimal(depositAmount));
        reader.setRegisteredAt(LocalDateTime.now().minusDays(30));
        repository.save(reader);
    }

    private void createBorrow(BookRepository bookRepository,
                              ReaderRepository readerRepository,
                              BorrowRecordRepository borrowRecordRepository,
                              String isbn,
                              String readerNo,
                              LocalDate borrowDate,
                              LocalDate dueDate,
                              LocalDate returnDate,
                              BorrowStatus status,
                              String fineAmount,
                              int renewCount) {
        Book book = bookRepository.findByIsbnAndDeletedFalse(isbn).orElseThrow();
        Reader reader = readerRepository.findByReaderNoAndDeletedFalse(readerNo).orElseThrow();
        BorrowRecord record = new BorrowRecord();
        record.setBook(book);
        record.setReader(reader);
        record.setBorrowDate(borrowDate);
        record.setDueDate(dueDate);
        record.setReturnDate(returnDate);
        record.setStatus(status);
        record.setFineAmount(new BigDecimal(fineAmount));
        record.setRenewCount(renewCount);
        borrowRecordRepository.save(record);
    }

    private void createReservation(BookRepository bookRepository,
                                   ReaderRepository readerRepository,
                                   ReservationRecordRepository reservationRecordRepository,
                                   String isbn,
                                   String readerNo,
                                   LocalDateTime reservedAt,
                                   LocalDateTime expiresAt,
                                   ReservationStatus status) {
        ReservationRecord record = new ReservationRecord();
        record.setBook(bookRepository.findByIsbnAndDeletedFalse(isbn).orElseThrow());
        record.setReader(readerRepository.findByReaderNoAndDeletedFalse(readerNo).orElseThrow());
        record.setReservedAt(reservedAt);
        record.setExpiresAt(expiresAt);
        record.setStatus(status);
        reservationRecordRepository.save(record);
    }

    private void createNotification(ReaderRepository readerRepository,
                                    NotificationRepository notificationRepository,
                                    String readerNo,
                                    String title,
                                    String content,
                                    NotificationStatus status,
                                    LocalDateTime sentAt,
                                    LocalDateTime readAt) {
        Notification notification = new Notification();
        notification.setReader(readerRepository.findByReaderNoAndDeletedFalse(readerNo).orElseThrow());
        notification.setTitle(title);
        notification.setContent(content);
        notification.setChannel(NotificationChannel.IN_APP);
        notification.setStatus(status);
        notification.setSentAt(sentAt);
        notification.setReadAt(readAt);
        notificationRepository.save(notification);
    }

    private void createFine(ReaderRepository readerRepository,
                            FineRecordRepository fineRecordRepository,
                            String readerNo,
                            BorrowRecord borrowRecord,
                            String reason,
                            String amount,
                            FineStatus status,
                            LocalDateTime paidAt) {
        FineRecord fine = new FineRecord();
        fine.setReader(readerRepository.findByReaderNoAndDeletedFalse(readerNo).orElseThrow());
        fine.setBorrowRecord(borrowRecord);
        fine.setReason(reason);
        fine.setAmount(new BigDecimal(amount));
        fine.setStatus(status);
        fine.setPaidAt(paidAt);
        fineRecordRepository.save(fine);
    }
}
