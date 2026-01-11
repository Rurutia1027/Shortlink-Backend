package org.tus.common.domain.dao.condition;

import lombok.Getter;
import lombok.Setter;
import org.tus.common.domain.dao.clause.WhereConditionalClause;

/**
 * Represents a single conditional expression in an HQL WHERE clause.
 *
 * <p>This class encapsulates the elements of a conditional expression
 * used by the HQL query builder, including:</p>
 * <ul>
 *     <li>The field or property being compared</li>
 *     <li>The value or map value used in the comparison</li>
 *     <li>An optional subquery for EXISTS or IN expressions</li>
 *     <li>The comparison or logical operator</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * WhereCondition condition = new WhereCondition("u.status", WhereConditionalClause.EQUALS, "ACTIVE");
 * WhereCondition existsCondition = new WhereCondition("select 1 from Order o where o.userId = u.id", WhereConditionalClause.SUB_QUERY);
 * }</pre>
 *
 * <p>This class is a lightweight data holder used internally by the HQL query builder.</p>
 */
@Getter
@Setter
public final class WhereCondition {

    /**
     * The column or property name the condition applies to
     */
    private String field;

    /** The value for the comparison */
    private String value;

    /** Optional secondary value (e.g., for map expressions) */
    private String mapValue;

    /** Subquery string for EXISTS / IN conditions */
    private String subQuery;

    /** The operator used in this condition */
    private WhereConditionalClause operator;

    // --- Constructors remain unchanged ---
}