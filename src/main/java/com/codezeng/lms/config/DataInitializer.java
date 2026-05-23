package com.codezeng.lms.config;

import com.codezeng.lms.domain.Book;
import com.codezeng.lms.domain.BookCategory;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.SystemConfig;
import com.codezeng.lms.domain.User;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.domain.enums.UserRole;
import com.codezeng.lms.repository.BookCategoryRepository;
import com.codezeng.lms.repository.BookRepository;
import com.codezeng.lms.repository.ReaderRepository;
import com.codezeng.lms.repository.SystemConfigRepository;
import com.codezeng.lms.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedData(UserRepository userRepository,
                               BookCategoryRepository categoryRepository,
                               BookRepository bookRepository,
                               ReaderRepository readerRepository,
                               SystemConfigRepository systemConfigRepository,
                               PasswordEncoder passwordEncoder) {
        return args -> {
            seedUsers(userRepository, passwordEncoder);
            seedBooks(categoryRepository, bookRepository);
            seedReaders(readerRepository);
            seedConfigs(systemConfigRepository);
        };
    }

    private void seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        if (userRepository.existsByUsername("admin")) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setNickname("系统管理员");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setRole(UserRole.SUPER_ADMIN);
        admin.setStatus(AccountStatus.NORMAL);
        userRepository.save(admin);

        User librarian = new User();
        librarian.setUsername("librarian");
        librarian.setEmail("librarian@example.com");
        librarian.setNickname("图书管理员");
        librarian.setPassword(passwordEncoder.encode("librarian123"));
        librarian.setRole(UserRole.LIBRARIAN);
        librarian.setStatus(AccountStatus.NORMAL);
        userRepository.save(librarian);
    }

    private void seedBooks(BookCategoryRepository categoryRepository, BookRepository bookRepository) {
        if (categoryRepository.count() == 0) {
            BookCategory literature = new BookCategory();
            literature.setName("文学");
            categoryRepository.save(literature);

            BookCategory technology = new BookCategory();
            technology.setName("计算机");
            categoryRepository.save(technology);

            BookCategory history = new BookCategory();
            history.setName("历史");
            categoryRepository.save(history);
        }
        if (bookRepository.count() > 0) {
            return;
        }
        BookCategory technology = categoryRepository.findByDeletedFalseOrderByNameAsc().stream()
                .filter(category -> "计算机".equals(category.getName()))
                .findFirst()
                .orElse(null);

        Book cleanCode = new Book();
        cleanCode.setTitle("代码整洁之道");
        cleanCode.setIsbn("9787115216878");
        cleanCode.setAuthor("Robert C. Martin");
        cleanCode.setPublisher("人民邮电出版社");
        cleanCode.setCategory(technology);
        cleanCode.setTotalQuantity(5);
        cleanCode.setAvailableQuantity(5);
        cleanCode.setPublishDate(LocalDate.of(2010, 1, 1));
        cleanCode.setPrice(new BigDecimal("59.00"));
        cleanCode.setLocation("A-01-03");
        bookRepository.save(cleanCode);

        Book spring = new Book();
        spring.setTitle("Spring Boot 实战");
        spring.setIsbn("9787115487520");
        spring.setAuthor("Craig Walls");
        spring.setPublisher("人民邮电出版社");
        spring.setCategory(technology);
        spring.setTotalQuantity(3);
        spring.setAvailableQuantity(3);
        spring.setPrice(new BigDecimal("79.00"));
        spring.setLocation("A-02-01");
        bookRepository.save(spring);
    }

    private void seedReaders(ReaderRepository readerRepository) {
        if (readerRepository.count() > 0) {
            return;
        }
        Reader reader = new Reader();
        reader.setReaderNo("R202605230001");
        reader.setName("张三");
        reader.setGender("男");
        reader.setPhone("13800000000");
        reader.setEmail("zhangsan@example.com");
        reader.setIdentityNo("S20260001");
        reader.setReaderType(ReaderType.STUDENT);
        reader.setMemberLevel(MemberLevel.NORMAL);
        reader.setStatus(AccountStatus.NORMAL);
        reader.setRegisteredAt(LocalDateTime.now());
        readerRepository.save(reader);
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
}
