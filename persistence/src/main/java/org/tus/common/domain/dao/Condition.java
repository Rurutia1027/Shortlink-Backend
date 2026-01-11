package org.tus.common.domain.dao;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a single conditional expression in an HQL WHERE clause.
 * This is a lightweight data holder used by the HQL query builder.
 */
@Getter
@Setter
public final class Condition {
    private String field;
    private String value;
    private String mapValue;
    private String subQuery;
    private WhereConditionalClause operator;

    public Condition(String field, WhereConditionalClause operator, String value) {
        this.field = field;
        this.value = value;
        this.operator = operator;
    }

    public Condition(String field, WhereConditionalClause operator, String value, String mapValue) {
        this.field = field;
        this.value = value;
        this.mapValue = mapValue;
        this.operator = operator;
    }

    public Condition(String subQuery, WhereConditionalClause operator) {
        this.operator = operator;
        this.subQuery = subQuery;
    }
}
