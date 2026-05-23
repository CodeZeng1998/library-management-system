package com.codezeng.lms.service;

import java.util.ArrayList;
import java.util.List;

public class BookSearchCriteria {

    private String keyword;
    private String title;
    private String titleMatch = "contains";
    private String author;
    private String authorMatch = "contains";
    private List<Long> categoryIds = new ArrayList<>();
    private Integer publishYearFrom;
    private Integer publishYearTo;
    private boolean availableOnly;
    private String location;
    private String sort = "relevance";
    private String view = "list";

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitleMatch() {
        return titleMatch;
    }

    public void setTitleMatch(String titleMatch) {
        this.titleMatch = titleMatch;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getAuthorMatch() {
        return authorMatch;
    }

    public void setAuthorMatch(String authorMatch) {
        this.authorMatch = authorMatch;
    }

    public List<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds == null ? new ArrayList<>() : categoryIds;
    }

    public Integer getPublishYearFrom() {
        return publishYearFrom;
    }

    public void setPublishYearFrom(Integer publishYearFrom) {
        this.publishYearFrom = publishYearFrom;
    }

    public Integer getPublishYearTo() {
        return publishYearTo;
    }

    public void setPublishYearTo(Integer publishYearTo) {
        this.publishYearTo = publishYearTo;
    }

    public boolean isAvailableOnly() {
        return availableOnly;
    }

    public void setAvailableOnly(boolean availableOnly) {
        this.availableOnly = availableOnly;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }
}
