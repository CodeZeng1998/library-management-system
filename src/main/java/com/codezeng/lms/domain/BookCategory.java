package com.codezeng.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "book_category")
public class BookCategory extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @ManyToOne
    private BookCategory parent;

    @Column(length = 200)
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BookCategory getParent() {
        return parent;
    }

    public void setParent(BookCategory parent) {
        this.parent = parent;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
