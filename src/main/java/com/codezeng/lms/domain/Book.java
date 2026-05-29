package com.codezeng.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "book_info", indexes = {
        @Index(name = "idx_book_deleted_isbn", columnList = "deleted,isbn"),
        @Index(name = "idx_book_deleted_location", columnList = "deleted,location"),
        @Index(name = "idx_book_deleted_borrow_count", columnList = "deleted,borrow_count")
})
public class Book extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String title;

    @Column(unique = true, nullable = false, length = 32)
    private String isbn;

    @Column(nullable = false, length = 128)
    private String author;

    @Column(nullable = false, length = 128)
    private String publisher;

    @ManyToOne
    private BookCategory category;

    @ManyToMany
    @JoinTable(name = "book_tag_rel",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<BookTag> tags = new LinkedHashSet<>();

    @Column(nullable = false)
    private int totalQuantity;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(length = 128)
    private String subtitle;

    @Column(length = 128)
    private String translator;

    private LocalDate publishDate;

    private BigDecimal price;

    private Integer pages;

    @Column(length = 64)
    private String binding;

    @Column(length = 2000)
    private String summary;

    private String coverUrl;

    @Column(length = 64)
    private String location;

    @Column(nullable = false)
    private long borrowCount = 0;

    @Column(nullable = false)
    private boolean referenceOnly = false;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public BookCategory getCategory() {
        return category;
    }

    public void setCategory(BookCategory category) {
        this.category = category;
    }

    public Set<BookTag> getTags() {
        return tags;
    }

    public void setTags(Set<BookTag> tags) {
        this.tags = tags == null ? new LinkedHashSet<>() : tags;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(int availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getTranslator() {
        return translator;
    }

    public void setTranslator(String translator) {
        this.translator = translator;
    }

    public LocalDate getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(LocalDate publishDate) {
        this.publishDate = publishDate;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getPages() {
        return pages;
    }

    public void setPages(Integer pages) {
        this.pages = pages;
    }

    public String getBinding() {
        return binding;
    }

    public void setBinding(String binding) {
        this.binding = binding;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getBorrowCount() {
        return borrowCount;
    }

    public void setBorrowCount(long borrowCount) {
        this.borrowCount = borrowCount;
    }

    public boolean isReferenceOnly() {
        return referenceOnly;
    }

    public void setReferenceOnly(boolean referenceOnly) {
        this.referenceOnly = referenceOnly;
    }
}
