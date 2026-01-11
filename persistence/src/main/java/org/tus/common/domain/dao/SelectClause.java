package org.tus.common.domain.dao;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
public enum SelectClause {
    SELECT,
    SELECT_MAP(true),
    UPDATE,
    INSERT,
    DELETE,
    AVERAGE(true),
    COUNT(true),
    MAX(true),
    MIN(true);

    private boolean fn = false;

    /**
     * @return Whether the selection clause is a function
     * that takes an operator field. E.g. "count(entity)" versus
     * "select entity".
     * Definitions taken from
     * https://docs.jboss.org/hibernate/orm/3.3/reference/en-US/html/queryhql.html.
     */
    public boolean isFn() {
        return fn;
    }
}
