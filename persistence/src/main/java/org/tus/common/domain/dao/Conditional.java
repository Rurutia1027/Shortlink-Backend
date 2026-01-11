package org.tus.common.domain.dao;

import lombok.Getter;
import lombok.Setter;

/**
 * Internal Container for the conditional elements of an HQL query.
 */
@Getter
@Setter
public final class Conditional {
    private String field;
    private String value;
    private String mapValue;
    private String subQuery;
    private WhereConditionalClause operator;

    public Conditional(String field, WhereConditionalClause operator, String value) {
        this.field = field;
        this.value = value;
        this.operator = operator;
    }

    public Conditional(String field, WhereConditionalClause operator, String value, String mapValue) {
        this.field = field;
        this.value = value;
        this.mapValue = mapValue;
        this.operator = operator;
    }

    public Conditional(String subQuery, WhereConditionalClause operator) {
        this.operator = operator;
        this.subQuery = subQuery;
    }
}
