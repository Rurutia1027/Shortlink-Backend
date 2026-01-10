package org.tus.common.domain.persistence;

import org.hibernate.Session;

import java.util.List;
import java.util.Map;

/**
 * QueryService implementations give clients access to query querying and updating the
 * database using HQL or SQL.
 */
public interface QueryService {
    /**
     * Returns a Hibernate Session connected to the repository.
     * The caller is responsible for properly closing this session.
     */
    Session openSession();

    /**
     * Executes the Hibernate Query Language query and returns the results.
     */
    List query(String hql);

    /**
     * Executes the given query, using the variable arguments list to bind positional
     * parameters.
     *
     * @param hql    the Hibernate query language query containing positional parameters
     * @param params the query parameters, in order.
     * @return the result set for the query
     */
    List query(String hql, Object... params);

    /**
     * Executes the given query, using the variable arguments list to bind positional
     * parameters.
     *
     * @param hql    the Hibernate query language query containing positional parameters
     * @param post   the query post-processor
     * @param params the query parameters, in order.
     * @return the result set for the query
     */
    List query(String hql, QueryPostProcessor post, Object... params);

    /**
     * Executes the given query, using the associative array to bind named parameters.
     *
     * @param hql         the Hibernate query language query containing named parameters
     * @param namedParams the associative array of named parameters
     * @return the result set for the query
     */
    List query(String hql, Map<String, Object> namedParams);

    /**
     * Executes the given query, using the associative array to bind named parameters.
     *
     * @param hql         the Hibernate query language query containing named parameters
     * @param namedParams the associative array of named parameters
     * @param post        the query post-processor
     * @return the result set for the query
     */
    List query(String hql, Map<String, Object> namedParams, QueryPostProcessor post);

    /**
     * Executes the given query, using the associative array to bind named parameters.
     *
     * @param hql             the Hibernate query language query containing named parameters
     * @param pageStart       the start point for the page
     * @param pageSize        the number of elements to be returned from the page
     * @param namedParameters the associative array of named parameters.
     * @return the result set for the query
     */
    List pagedQuery(String hql, Map<String, Object> namedParameters, Integer pageStart,
                    Integer pageSize);

    /**
     * Executes the given query, using the associative array to bind named parameters.
     *
     * @param hql             the Hibernate query language query containing named parameters
     * @param pageStart       the start point for the page
     * @param pageSize        the number of elements to be returned from the page
     * @param namedParameters the associative array of named parameters.
     * @param post            the query post-processor. May be {@code null} if no post-processing is required.
     * @return the result set
     */
    List pagedQuery(String hql, Map<String, Object> namedParameters,
                    Integer pageStart, Integer pageSize,
                    QueryPostProcessor post);
}
