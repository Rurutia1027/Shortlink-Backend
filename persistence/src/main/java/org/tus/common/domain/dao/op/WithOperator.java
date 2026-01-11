package org.tus.common.domain.dao.op;

/**
 * Represents an operator used in a JOIN ... WITH clause in HQL.
 *
 * <p>This enum provides the HQL string representation of the operator,
 * which is used when assembling join conditions in the query builder.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * JoinClause join = new JoinClause(
 *     JoinType.LEFT_OUTER,
 *     "User", "Order",
 *     "u.id", WithOperator.EQUAL, "o.userId"
 * );
 * String hql = join.getWithOp().getHql(); // "="
 * }</pre>
 *
 * <p>Currently supported operators:</p>
 * <ul>
 *     <li>{@link #EQUAL} — "="</li>
 *     <li>{@link #NOT_EQUAL} — "<>"</li>
 * </ul>
 */
public enum WithOperator {

    /**
     * Equals operator
     */
    EQUAL("="),

    /** Not equals operator */
    NOT_EQUAL("<>");

    /** The HQL operator represented by this enumerand */
    private final String hql;

    /**
     * Creates a new instance of WithOperator
     *
     * @param hql the HQL string representation of the operator
     */
    WithOperator(String hql) {
        this.hql = hql;
    }

    /**
     * Returns the HQL string representation of this operator.
     *
     * @return the HQL operator string
     */
    public String getHql() {
        return this.hql;
    }
}