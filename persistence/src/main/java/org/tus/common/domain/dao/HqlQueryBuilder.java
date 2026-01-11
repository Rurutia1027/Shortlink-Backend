package org.tus.common.domain.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder class for generating HQL queries.
 * Builder components can be called order independent and will be constructed correctly.
 * Examples can be found in the associated test classes.
 */
public class HqlQueryBuilder {
    private Map<String, String> fromMap = new LinkedHashMap<>();
    // private List<Join> joins = new ArrayList<>();
    private List<Condition> conditions = new ArrayList<>();
    private Map<String, String> orderBy = new LinkedHashMap<>();
}
