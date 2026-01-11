package org.tus.common.domain.dao.type;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Represents the type of query operation or selection clause in HQL.
 *
 * <p>This enum includes standard DML operations (SELECT, UPDATE, INSERT, DELETE)
 * as well as aggregate or function-based selections (COUNT, MAX, MIN, AVERAGE).</p>
 *
 * <p>The {@code fn} flag indicates whether the clause is a function that takes
 * an argument, e.g., "count(entity)" versus a plain "select entity".</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * QueryClauseType clause = QueryClauseType.COUNT;
 * if (clause.isFn()) {
 *     String hql = clause.name().toLowerCase() + "(entity)";
 * } else {
 *     String hql = clause.name().toLowerCase() + " entity";
 * }
 * }</pre>
 *
 * <p>Definitions based on HQL documentation:
 * https://docs.jboss.org/hibernate/orm/3.3/reference/en-US/html/queryhql.html
 * </p>
 */
@AllArgsConstructor
@NoArgsConstructor
public enum QueryClauseType {

    // Standard selection / DML clauses
    SELECT,
    SELECT_MAP(true),
    UPDATE,
    INSERT,
    DELETE,

    // Aggregate functions
    AVERAGE(true),
    COUNT(true),
    MAX(true),
    MIN(true);

    /**
     * Indicates whether this clause is a function taking a field (e.g., COUNT(entity))
     */
    private boolean fn = false;

    /**
     * @return true if this clause is a function (takes a field argument)
     */
    public boolean isFn() {
        return fn;
    }
}
