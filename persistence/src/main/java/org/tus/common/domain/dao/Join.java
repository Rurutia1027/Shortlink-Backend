package org.tus.common.domain.dao;


import lombok.Getter;
import lombok.Setter;
import org.tus.shortlink.base.tookit.StringUtils;

/**
 * Internal container for the Join operations of an HQL query.
 */
@Setter
@Getter
public class Join {
    private JoinEnum joinType;
    private String fromTable;
    private String toTable;
    private boolean fetch;
    private String withLeft;
    private WithOperator withOp;
    private String withRight;

    /**
     * Creates a new instance of Join
     *
     * @param joinType the join type
     * @param fromTable the table that is the left side of the join operation
     * @param toTable the table that is the right side of the join operation
     */
    public Join(JoinEnum joinType, String fromTable, String toTable) {
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
    public Join(JoinEnum joinType, String fromTable, String toTable, boolean fetch) {
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
    public Join(JoinEnum joinType, String fromTable, String toTable,
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
    public Join(JoinEnum joinType, String fromTable, String toTable, boolean fetch, String withLeft, WithOperator withOp, String withRight) {
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
