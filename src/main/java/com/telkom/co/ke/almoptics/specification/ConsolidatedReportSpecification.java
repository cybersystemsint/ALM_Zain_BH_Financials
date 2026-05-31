package com.telkom.co.ke.almoptics.specification;

import com.telkom.co.ke.almoptics.dto.FilterRequest;
import com.telkom.co.ke.almoptics.dto.FilterRequest.Filter;
import com.telkom.co.ke.almoptics.dto.FilterRequest.Filter.FilterOperator;
import com.telkom.co.ke.almoptics.entities.ConsolidatedFinancialReport;
import org.springframework.data.jpa.domain.Specification;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsolidatedReportSpecification {

    private ConsolidatedReportSpecification() {}

    public static Specification<ConsolidatedFinancialReport> build(FilterRequest request) {
        return (root, query, cb) -> {

            List<Predicate> predicates = new ArrayList<>();

            if (request == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }

            if (request.getFilters() != null) {
                for (Filter filter : request.getFilters()) {
                    if (filter == null || !isNotBlank(filter.getColumn())) {
                        continue;
                    }

                    Predicate predicate = buildColumnPredicate(root, cb, filter);
                    if (predicate != null) {
                        predicates.add(predicate);
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Predicate buildColumnPredicate(
            Root<ConsolidatedFinancialReport> root,
            CriteriaBuilder cb,
            Filter filter) {

        String column = filter.getColumn();
        String value = filter.getValue();

        FilterOperator op = filter.getOperator() != null
                ? filter.getOperator()
                : FilterOperator.CONTAINS;

        if (op == FilterOperator.IS_EMPTY) {
            return cb.or(
                    cb.isNull(root.get(column)),
                    cb.equal(cb.trim(root.get(column).as(String.class)), "")
            );
        }

        if (op == FilterOperator.IS_NOT_EMPTY) {
            return cb.and(
                    cb.isNotNull(root.get(column)),
                    cb.notEqual(cb.trim(root.get(column).as(String.class)), "")
            );
        }

        if (!isNotBlank(value)) {
            return null;
        }

        return switch (op) {
            case EQUALS ->
                    cb.equal(cb.lower(root.get(column).as(String.class)),
                            value.trim().toLowerCase());

            case STARTS_WITH ->
                    cb.like(cb.lower(root.get(column).as(String.class)),
                            value.trim().toLowerCase() + "%");

            case ENDS_WITH ->
                    cb.like(cb.lower(root.get(column).as(String.class)),
                            "%" + value.trim().toLowerCase());

            case IS_ANY_OF -> {
                List<String> tokens = Arrays.stream(value.split(","))
                        .map(String::trim)
                        .filter(t -> !t.isEmpty())
                        .map(String::toLowerCase)
                        .toList();

                if (tokens.isEmpty()) {
                    yield null;
                }

                CriteriaBuilder.In<String> inClause =
                        cb.in(cb.lower(root.get(column).as(String.class)));
                tokens.forEach(inClause::value);
                yield inClause;
            }

            default ->
                    cb.like(cb.lower(root.get(column).as(String.class)),
                            "%" + value.trim().toLowerCase() + "%");
        };
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}