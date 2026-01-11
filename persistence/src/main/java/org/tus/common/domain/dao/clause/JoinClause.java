package org.tus.common.domain.dao.clause;

import lombok.Getter;
import lombok.Setter;
import org.tus.common.domain.dao.op.WithOperator;
import org.tus.common.domain.dao.type.JoinType;
import org.tus.shortlink.base.tookit.StringUtils;

/**
 * Represents a single JOIN clause in an HQL query, optionally including a
 * {@code WITH} condition and {@code FETCH} semantics.
 *
 * <p>This class encapsulates all the elements needed to construct a JOIN
 * statement in HQL, including:</p>
 * <ul>
 *     <li>The type of join (INNER, LEFT, RIGHT, FULL)</li>
 *     <li>The left-hand table (fromTable) and right-hand table (toTable)</li>
 *     <li>An optional FETCH keyword to eagerly load associated entities</li>
 *     <li>An optional WITH condition for join filtering</li>
 * </ul>
 *
 * <p>This class is used internally by the HQL query builder to assemble
 * JOIN statements consistently.</p>
 */
@Getter
@Setter
public class JoinClause {

    /**
     * The type of join (INNER, LEFT, RIGHT, FULL)
     */
    private JoinType joinType;

    /**
     * The left-hand side table of the join
     */
    private String fromTable;

    /**
     * The right-hand side table of the join
     */
    private String toTable;

    /**
     * Whether to include the FETCH keyword
     */
    private boolean fetch;

    /** The left-hand side of a WITH expression, if any */
    private String withLeft;

    /** Operator used in the WITH expression, if any */
    private WithOperator withOp;

    /** The right-hand side of a WITH expression, if any */
    private String withRight;

    /**
     * Creates a new instance of Join
     *
     * @param joinType the join type
     * @param fromTable the table that is the left side of the join operation
     * @param toTable the table that is the right side of the join operation
     */
    public JoinClause(JoinType joinType, String fromTable, String toTable) {
        this(joinType, fromTable, toTable, false);
    }

    /**
     * Creates a new instance of Join
     *
     * @param joinType the join type
     * @param fromTable the table that is the left side of the join operation
     * @param toTable the table that is the right side of the join operation
     * @param fetch a flag that indicates whether to force fetch the right side table
     */
    public JoinClause(JoinType joinType, String fromTable, String toTable, boolean fetch) {
        this(joinType, fromTable, toTable, fetch, null, null, null);
    }

    /**
     * Creates a join that includes a with condition
     *
     * @param joinType the join type
     * @param fromTable the table on the left side of the join operation
     * @param toTable the table on the right side of the join operation
     * @param withLeft the table on the left side of the with condition
     * @param withOp the with operator
     * @param withRight the value on the right side of the with condition
     */
    public JoinClause(JoinType joinType, String fromTable, String toTable,
                String withLeft, WithOperator withOp, String withRight) {
        this(joinType, fromTable, toTable, false, withLeft, withOp, withRight);
    }

    /**
     * Creates a join that includes a with condition
     *
     * @param joinType the join type
     * @param fromTable the table on the left side of the join operation
     * @param toTable the table on the right side of the join operation
     * @param fetch a flag that indicates whether to force fetch the right side table
     * @param withLeft the table on the left side of the with condition
     * @param withOp the with operator
     * @param withRight the value on the right side of the with condition
     */
    public JoinClause(JoinType joinType, String fromTable, String toTable, boolean fetch,
                      String withLeft, WithOperator withOp, String withRight) {
        this.joinType = joinType;
        this.fromTable = fromTable;
        this.toTable = toTable;
        this.fetch = fetch;
        this.withLeft = withLeft;
        this.withOp = withOp;
        this.withRight = withRight;
    }
    /**
     * Determines whether this join has a valid with condition
     *
     * @return {@code true} if this join has a valid with condition
     * (left side, operator, and right side);
     * {@code false} otherwise
     */
    public boolean isWith() {
        return (StringUtils.hasText(this.withLeft) && this.withOp != null
                && StringUtils.hasText(this.withRight));
    }

    /**
     * Appends the HQL representation of this join operation to the given
     * StringBuilder.
     *
     * @param builder the StringBuilder where the HQL query is being accumulated
     */
    public void append(StringBuilder builder) {
        builder.append(" ").append(this.joinType.getHql()).append(" ");
        if(isFetch()) {
            builder.append("FETCH ");
        }
        builder.append(this.fromTable).append(" ").append(this.toTable);
        if(isWith()) {
            builder.append(" WITH ").append(this.withLeft).append(" ").append(this.withOp.getHql()).
                    append(" ").append(this.withRight);
        }
    }
}