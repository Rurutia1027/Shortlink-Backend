package org.tus.common.domain.dao;

/**
 * Identifies different types of joins
 */
public enum JoinEnum {
    /** A left outer join */
    LEFT_OUTER("LEFT JOIN"),
    /** A right outer join */
    RIGHT_OUTER("RIGHT JOIN"),
    /** An inner join */
    INNER("INNER JOIN"),
    /** A full join */
    FULL("FULL JOIN");

    /** The HQL representation of the join operation */
    private String hql;

    /**
     * Creates a new instance of JoinEnum
     *
     * @param hql the HQL representation of the join operation
     */
    JoinEnum(String hql) {
        this.hql = hql;
    }

    public String getHql() {
        return this.hql;
    }
}
