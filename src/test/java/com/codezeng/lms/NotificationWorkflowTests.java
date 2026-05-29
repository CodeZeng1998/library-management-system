package com.codezeng.lms;

import com.codezeng.lms.domain.Notification;
import com.codezeng.lms.domain.Reader;
import com.codezeng.lms.domain.enums.AccountStatus;
import com.codezeng.lms.domain.enums.MemberLevel;
import com.codezeng.lms.domain.enums.NotificationStatus;
import com.codezeng.lms.domain.enums.ReaderType;
import com.codezeng.lms.repository.NotificationRepository;
import com.codezeng.lms.service.NotificationService;
import com.codezeng.lms.service.ReaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationWorkflowTests {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ReaderService readerService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void batchMarkReadCountsOnlyUnreadUndeletedMessages() {
        Reader reader = readerService.save(reader("R-NOTIFY-0001", "notify.1@example.com", "NOTIFY-ID-0001"));
        Notification unread = notificationService.send(reader, "Unread", "Unread content");
        Notification alreadyRead = notificationService.send(reader, "Read", "Read content");
        notificationService.markRead(alreadyRead.getId());
        Notification deleted = notificationService.send(reader, "Deleted", "Deleted content");
        deleted.setDeleted(true);
        notificationRepository.save(deleted);

        int changed = notificationService.markRead(List.of(unread.getId(), alreadyRead.getId(), deleted.getId()));

        assertThat(changed).isEqualTo(1);
        assertThat(notificationRepository.findById(unread.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(notificationRepository.findById(deleted.getId()).orElseThrow().getStatus()).isEqualTo(NotificationStatus.UNREAD);
    }

    @Test
    void softDeleteIgnoresAlreadyDeletedMessages() {
        Reader reader = readerService.save(reader("R-NOTIFY-0002", "notify.2@example.com", "NOTIFY-ID-0002"));
        Notification active = notificationService.send(reader, "Active", "Active content");
        Notification deleted = notificationService.send(reader, "Deleted", "Deleted content");
        deleted.setDeleted(true);
        notificationRepository.save(deleted);

        int changed = notificationService.softDelete(List.of(active.getId(), deleted.getId()));

        assertThat(changed).isEqualTo(1);
        assertThat(notificationRepository.findById(active.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @WithMockUser(authorities = "NOTIFICATION_VIEW")
    void rejectsUnsupportedBatchAction() throws Exception {
        mockMvc.perform(post("/notifications/batch")
                        .with(csrf())
                        .param("ids", "1")
                        .param("action", "archive"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"))
                .andExpect(flash().attributeExists("error"));
    }

    private Reader reader(String readerNo, String email, String identityNo) {
        Reader reader = new Reader();
        reader.setReaderNo(readerNo);
        reader.setName("Notification Reader");
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
