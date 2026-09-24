package com.auctionhouse.dto;

import java.util.List;

// 分页结果的标准外壳。
// 不直接把 Spring Data 的 Page 丢给前端：它的 JSON 里塞着 sort/pageable/first/last 一大堆
// 用不上的字段，而且 Spring 官方提醒过这个结构不稳定，换个版本就可能变。
public class PageResponse<T> {

    private List<T> items;
    private int page;            // 当前第几页，从 0 开始
    private int totalPages;      // 一共几页
    private long totalElements;  // 一共几条

    public static <T> PageResponse<T> of(List<T> items, int page, int totalPages, long totalElements) {
        PageResponse<T> response = new PageResponse<T>();
        response.items = items;
        response.page = page;
        response.totalPages = totalPages;
        response.totalElements = totalElements;
        return response;
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public long getTotalElements() {
        return totalElements;
    }
}
