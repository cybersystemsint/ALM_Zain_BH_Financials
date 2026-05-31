package com.telkom.co.ke.almoptics.dto;
 
import java.util.List;
 

public class PageResult<T> {
 
    private List<T> data;
    private int     page;
    private int     size;
    private long    totalElements;
    private int     totalPages;
    private boolean first;
    private boolean last;
 
    public PageResult() {}
 
    public PageResult(List<T> data, int page, int size, long totalElements) {
        this.data          = data;
        this.page          = page;
        this.size          = size;
        this.totalElements = totalElements;
        this.totalPages    = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        this.first         = page == 0;
        this.last          = page >= this.totalPages - 1;
    }
 
    // ── Getters / Setters ───────────────────────────────────────────────────
 
    public List<T> getData()                   { return data; }
    public void    setData(List<T> data)       { this.data = data; }
 
    public int     getPage()                   { return page; }
    public void    setPage(int page)           { this.page = page; }
 
    public int     getSize()                   { return size; }
    public void    setSize(int size)           { this.size = size; }
 
    public long    getTotalElements()          { return totalElements; }
    public void    setTotalElements(long v)    { this.totalElements = v; }
 
    public int     getTotalPages()             { return totalPages; }
    public void    setTotalPages(int v)        { this.totalPages = v; }
 
    public boolean isFirst()                   { return first; }
    public void    setFirst(boolean first)     { this.first = first; }
 
    public boolean isLast()                    { return last; }
    public void    setLast(boolean last)       { this.last = last; }
}