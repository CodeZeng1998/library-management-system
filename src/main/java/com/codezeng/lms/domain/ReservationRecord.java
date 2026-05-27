package com.codezeng.lms.domain;

import com.codezeng.lms.domain.enums.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "reservation_record", indexes = {
        @Index(name = "idx_reservation_deleted_status_expires", columnList = "deleted,status,expires_at"),
        @Index(name = "idx_reservation_book_status_reserved", columnList = "book_id,status,reserved_at"),
        @Index(name = "idx_reservation_reader_deleted_reserved", columnList = "reader_id,deleted,reserved_at")
})
public class ReservationRecord extends BaseEntity {

    @ManyToOne(optional = false)
    private Book book;

    @ManyToOne(optional = false)
    private Reader reader;

    @Column(nullable = false)
    private LocalDateTime reservedAt;

    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ReservationStatus status = ReservationStatus.WAITING;

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Reader getReader() {
        return reader;
    }

    public void setReader(Reader reader) {
        this.reader = reader;
    }

    public LocalDateTime getReservedAt() {
        return reservedAt;
    }

    public void setReservedAt(LocalDateTime reservedAt) {
        this.reservedAt = reservedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
