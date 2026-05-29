package com.codezeng.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "book_tag", indexes = {
        @Index(name = "idx_book_tag_deleted_name", columnList = "deleted,name")
})
public class BookTag extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 200)
    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
