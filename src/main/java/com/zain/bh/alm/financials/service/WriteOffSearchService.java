package com.zain.bh.alm.financials.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class WriteOffSearchService {

    private final WriteOffReportService writeOffReportService;

    public WriteOffSearchService(WriteOffReportService writeOffReportService) {
        this.writeOffReportService = writeOffReportService;
    }

    public Map<String, Object> searchWriteOffReports(int page, int size, String sort, String query) {
        Sort sortObj = Sort.unsorted();
        if (sort != null && !sort.isEmpty()) {
            String[] sortParts = sort.split(",");
            String field = sortParts[0];
            Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            sortObj = Sort.by(direction, field);
        }

        Pageable pageable = PageRequest.of(page, size, sortObj);
        Page<?> reportPage = query != null && !query.trim().isEmpty() ?
                writeOffReportService.search(query.trim(), pageable) :
                writeOffReportService.findAll(pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", reportPage.getContent());
        response.put("totalElements", reportPage.getTotalElements());
        response.put("totalPages", reportPage.getTotalPages());
        response.put("page", page);
        response.put("size", size);
        return response;
    }
}
