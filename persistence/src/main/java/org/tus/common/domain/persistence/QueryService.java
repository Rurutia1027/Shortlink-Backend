package org.tus.common.domain.persistence;

import org.hibernate.HibernateException;
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

    // ==================

    /**
     * Inserts or Update the object. If it's new, creates a new instance.
     * Otherwise, updates the object.
     *
     * @param item the object to persist
     * @param <T>  the type of {@code item}
     * @return the saved object
     */
    <T extends SimplePersistedObject> T save(T item) throws HibernateException;

    /**
     * Insert the object
     *
     * @param item         the object to persist
     * @param saveOrUpdate if false, inserts one objects, if exists, throws HibernateException.
     *                     If true, check if objects exists, if exists, related object will
     *                     be updated.
     * @param <T>          the type of {@code item}
     * @return the saved object
     */
    <T> T save(T item, boolean saveOrUpdate) throws HibernateException;


    /**
     * Permanently and irrevocably deletes an item from the database.
     *
     * @param item the item to delete
     * @param <T>  the type of {@code item}
     * @return the deleted item
     * @throws HibernateException if error happens
     */
    <T> T delete(T item) throws HibernateException;

    /**
     * Delete the objects returned from the query from the database.
     *
     * @param hql    The query that finds objects to delete.
     * @param params The replacement parameter values.
     * @return The number of objects deleted.
     */
    int delete(String hql, Object... params);

    /**
     * Delete all of the objects in the list from the database.
     *
     * @param objects The list of objects to delete.
     */
    <T> void deleteAll(List<T> objects);



    /**
     * Inserts the objects if they are new, otherwise udpates the objects.
     *
     * @param itemList The list of objects to persist.
     * @return The list of passed in objects after Hibernate has set the IDs.
     */
    <T extends SimplePersistedObject> List<T> saveAll(List<T> itemList);

    /**
     * Inserts the object if it's new, otherwise updates the object
     *
     * @param item the object to persist
     * @param <T>  the type of {@code item}
     * @return the saved object
     */
    <T> T save(T item) throws HibernateException;

    /**
     * Permanently and irrevocably deletes an item from the database
     *
     * @param item the item to delete
     * @param <T>  the type of {@code item}
     * @return the deleted item
     */
    <T extends SimplePersistedObject> T delete(T item);

    <T extends SimplePersistedObject> List<T> mergeAll(List<T> itemList) throws HibernateException;

    /**
     * Execute an SQL query with ? replacement parameters.
     *
     * @param sql    An SQL query to run.
     * @param params The replacement parameter values.
     * @return A List of Maps with lowercase column names as the keys.
     */
    @SuppressWarnings("rawtypes")
    List sqlQuery(String sql, Object... params);

    /**
     * Execute an SQL query with ? replacement parameters.
     *
     * @param sql    An SQL query to run.
     * @param limit  The maximum number of rows to retrieve.
     * @param params The replacement parameter values.
     * @return A List of Maps with lowercase column names as the keys.
     */
    @SuppressWarnings("rawtypes")
    List sqlQueryLimit(String sql, int limit, Object... params);

    /**
     * Execute an SQL query with ? replacement parameters.
     *
     * @param sql    An SQL query to run.
     * @param params The replacement parameter values.
     * @return A List of Object[]s with containing the values from the
     * select clause in order.
     */
    List<Object[]> sqlQueryArray(String sql, Object... params);

    /**
     * Execute an SQL update with ? replacement parameters.
     *
     * @param sql    An SQL update to run.
     * @param params The replacement parameter values.
     * @return The number of rows affected.
     */
    int sqlUpdate(String sql, Object... params);

    /**
     * Finds an object with the given name.
     *
     * @param clazz the data type of the object to search for
     * @param name  the name or unique identifier of the object
     * @param <T>   The data type of the object
     * @return the fully loaded object that corresponds to the given name, or {@code null} if no such object can be
     * found.
     */
    <T extends UniqueNamedArtifact>
    T findObjectByName(Class<T> clazz, String name);

    <T extends SimplePersistedObject>
    T findSimpleObjectById(Class<T> clazz,
                           String objId, String typeName);

    <T extends SimplePersistedObject> T findSimpleObjectById(Class<T> clazz, String objId);

    /**
     * Finds an object with the given name.
     *
     * @param clazz the data type of the object to search for
     * @param name  the name or unique identifier of the object
     * @param post  the query post-processor to run on the object (may be {@code null})
     * @param <T>   The data type of the object
     * @return the fully loaded object that corresponds to the given name or unique identifier, or {@code null} if no
     * such object can be found.
     */
    <T extends UniqueNamedArtifact> T
    findObjectByName(Class<T> clazz, String name, QueryPostProcessor post);

    /**
     * Finds an object with the given unique identifier.
     *
     * @param clazz the data type of the object to search for
     * @param id    the unique identifier of the object
     * @param <T>   The data type of the object
     * @return the fully loaded object that corresponds to the given unique identifier, or {@code null} if no
     * such object can be found.
     */
    <T extends PersistedObject> T findObjectById(Class<T> clazz, String id);

    /**
     * Finds an object with the given unique identifier.
     *
     * @param clazz the data type of the object to search for
     * @param id    the unique identifier of the object
     * @param post  the query post-processor to run on the object (may be {@code null})
     * @param <T>   The data type of the object
     * @return the fully loaded object that corresponds to the given unique identifier, or {@code null} if no
     * such object can be found.
     */
    <T extends PersistedObject> T findObjectById(Class<T> clazz, String id, QueryPostProcessor post);

    /**
     * Finds an object with the given name or unique identifier.
     *
     * @param clazz    the data type of the object to search for
     * @param idOrName the name or unique identifier of the object
     * @param <T>      The data type of the object
     * @return the fully loaded object that corresponds to the given name or unique identifier, or {@code null} if no
     * such object can be found.
     */
    <T extends PersistedObject> T findObjectByIdOrName(Class<T> clazz, String idOrName);

    /**
     * Finds an object with the given name or unique identifier.
     *
     * @param clazz  the data type of the object to search for
     * @param idName the name or unique identifier of the object
     * @param post   the query post-processor to run on the object (may be {@code null})
     * @param <T>    The data type of the object
     * @return the fully loaded object that corresponds to the given name or unique identifier, or {@code null} if no
     * such object can be found.
     */
    <T extends PersistedObject> T findObjectByIdOrName(Class<T> clazz, String idName, QueryPostProcessor post);

    /**
     * Execute an hql query by anding the provided parameters.
     *
     * @param tClass is the class of the object
     * @param params is a {@link java.util.Map} of {@link java.lang.String} keys and {@link java.lang.String} values
     * @return {@link List} of objects found
     */
    <T extends PersistedObject> List<T> findObjectsByAndingParams(Class<T> tClass, Map<String, String> params);

    /**
     * Execute an HQL query for a single object.
     *
     * @param hql HQL query
     * @return object from the query
     */
    Object querySingle(String hql);

    /**
     * Executes an HQL query for a single object.
     *
     * @param hql             the HQL query text
     * @param namedParameters the parameters of the query
     * @return the result of the query
     */
    Object querySingle(String hql, Map<String, Object> namedParameters);

    String queryByJdbc(String sql, Map<Integer, String> namedParameters, int i);

    /**
     * Executes an INSERT, UPDATE, or DELETE statement
     *
     * @param hql             hql statement to be executed
     * @param namedParameters parameters of the statement
     * @return the number of rows effects by the query
     */
    int executeQuery(String hql, Map<String, Object>
            namedParameters);

    /**
     * Executes an HQL query for a single object.
     *
     * @param hql             the HQL query text
     * @param namedParameters the parameters of the query
     * @param post            the post processor that handles the result of the query
     * @return the result of the query
     */
    Object querySingle(String hql, Map<String, Object> namedParameters, QueryPostProcessor post);

    /**
     * Returns a object if found, or creates the object and return it.
     */
    <T extends NamedArtifact> T findOrSave(String hql,
                                           Map<String, Object> namedParameters,
                                           T item);
}
