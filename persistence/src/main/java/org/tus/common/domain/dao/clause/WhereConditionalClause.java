package org.tus.common.domain.dao.clause;

import org.tus.common.domain.dao.condition.WhereCondition;

/**
 * Represents the types of operators and structural tokens
 * that can be used in a WHERE clause within an HQL query.
 *
 * <p>This enum covers three categories:</p>
 * <ul>
 *   <li><b>Comparison operators:</b> EQUALS, NOT_EQUAL, GREATER_THAN, LESS_THAN, GREATER_EQUAL_THAN, LESS_EQUAL_THAN, LIKE, IN, IN_NO_PARENS</li>
 *   <li><b>Logical operators:</b> AND, OR, NOT</li>
 *   <li><b>Structural tokens:</b> OPEN_SCOPE, CLOSE_SCOPE (for parentheses grouping)</li>
 *   <li><b>Special constructs:</b> NULL, NOT_NULL, MAP, SUB_QUERY</li>
 * </ul>
 *
 * <p>These values are used by {@link WhereCondition} to define the semantics of each conditional expression.
 * When generating HQL, each operator is mapped to the corresponding HQL string.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WhereCondition condition = new WhereCondition("u.status", WhereConditionalClause.EQUALS, "ACTIVE");
 * WhereCondition subQueryCondition = new WhereCondition("select 1 from Order o where o.userId = u.id", WhereConditionalClause.SUB_QUERY);
 * }</pre>
 */
public enum WhereConditionalClause {
    // Comparison operators
    EQUALS,
    NOT_EQUAL,
    GREATER_THAN,
    LESS_THAN,
    GREATER_EQUAL_THAN,
    LESS_EQUAL_THAN,
    LIKE,
    IN,
    /**
     * Useful for other types of 'IN', e.g., elements / index querying
     */
    IN_NO_PARENS,

    // Logical operators
    AND,
    OR,
    NOT,

    // Structural tokens for grouping
    OPEN_SCOPE,
    CLOSE_SCOPE,

    // Special constructs
    NULL,
    NOT_NULL,
    MAP,
    SUB_QUERY
}