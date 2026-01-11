package org.tus.common.domain.dao;

import lombok.Getter;
import lombok.Setter;
import org.tus.common.domain.dao.clause.JoinClause;
import org.tus.common.domain.dao.condition.WhereCondition;
import org.tus.common.domain.dao.op.WithOperator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles an HQL query from individual components such as
 * FROM clauses, WHERE conditions, and ORDER BY specifications.
 *
 * <p>This builder allows components to be added in any order.
 * When the query is constructed, the builder organizes them
 * correctly according to HQL syntax.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * HqlQueryAssembler builder = new HqlQueryAssembler();
 * builder.from("User", "u")
 *        .where(new WhereCondition("u.active", WhereOperator.EQ, true))
 *        .orderBy("u.createdAt", "desc");
 * String hql = builder.build();
 * }</pre>
 *
 * <p>Associations:
 * <ul>
 *   <li>{@link WhereCondition} — represents a WHERE clause condition</li>
 *   <li>{@link JoinClause} — represents a JOIN clause</li>
 *   <li>{@link WithOperator} — operators used in JOIN/ON conditions</li>
 * </ul>
 * </p>
 */
@Setter
@Getter
public class HqlQueryAssembler {
    private Map<String, String> fromMap = new LinkedHashMap<>();
    // private List<Join> joins = new ArrayList<>();
    private List<WhereCondition> conditions = new ArrayList<>();
    private Map<String, String> orderBy = new LinkedHashMap<>();
}
