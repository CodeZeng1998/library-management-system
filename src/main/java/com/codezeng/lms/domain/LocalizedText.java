package com.codezeng.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "localized_text")
public class LocalizedText extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 64)
    private String fieldKey;

    @Column(nullable = false, length = 32)
    private String localeTag;

    @Column(nullable = false, length = 500)
    private String text;

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getLocaleTag() {
        return localeTag;
    }

    public void setLocaleTag(String localeTag) {
        this.localeTag = localeTag;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
