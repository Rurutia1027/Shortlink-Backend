package org.tus.common.domain.dao.type;

/**
 * Represents the type of a JOIN operation in HQL.
 *
 * <p>Each enumerand corresponds to a standard join type and provides
 * its HQL string representation used during query assembly.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * JoinClause join = new JoinClause(JoinType.LEFT_OUTER, "User", "Order");
 * String hqlJoin = join.getJoinType().getHql(); // "LEFT JOIN"
 * }</pre>
 */
public enum JoinType {

    /** A left outer join */
    LEFT_OUTER("LEFT JOIN"),

    /** A right outer join */
    RIGHT_OUTER("RIGHT JOIN"),

    /** An inner join */
    INNER("INNER JOIN"),

    /** A full join */
    FULL("FULL JOIN");

    /** The HQL representation of the join operation */
    private final String hql;

    /**
     * Creates a new instance of JoinType
     *
     * @param hql the HQL representation of the join operation
     */
    JoinType(String hql) {
        this.hql = hql;
    }

    /**
     * Returns the HQL string representation of this join type.
     *
     * @return the HQL string
     */
    public String getHql() {
        return this.hql;
    }
}