package com.telkom.co.ke.almoptics.dto;

import java.util.List;

public class PageResult<T> {

    private List<T> data;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;

    public List<T> getdata() {
        return data;
    }

    public void setdata(List<T> data) {
        this.data = data;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }
}