package org.tus.common.domain.persistence;

import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;
import org.hibernate.HibernateException;
import org.hibernate.JDBCException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.tus.shortlink.base.tookit.StringUtils;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides query access to the repository by wrapping Hibernate. All access to the
 * repository should be done through this class.
 */

@AllArgsConstructor
@Setter
@Getter
public class PersistenceService implements QueryService {
    private static final Logger logger = LoggerFactory.getLogger(PersistenceService.class);

    private static final Object[] EMPTY = {};
    private SessionFactory sessionFactory;
    private DataSource dataSource;

    /**
     * Interface for pluggable row handling strategies below.
     */
    private interface RowBuilder {
        Object buildRow(ResultSet rs, String[] colNames) throws SQLException;
    }

    /**
     * Returns a Map for each row with lowercased column names as keys.
     */
    private final RowBuilder mapBuilder = new RowBuilder() {
        @Override
        public Object buildRow(ResultSet rs, String[] colNames) throws SQLException {
            Map row = new HashMap();
            for (int i = 0; i < colNames.length; i++) {
                row.put(colNames[i], readColumnValues(rs, i));
            }
            return row;
        }
    };

    /**
     * Returns an Object[] for each row maintaining the column order from the select clause.
     */
    private final RowBuilder arrayBuilder = new RowBuilder() {
        @Override
        public Object buildRow(ResultSet rs, String[] colNames) throws SQLException {
            Object[] row = new Object[colNames.length];
            for (int i = 0; i < colNames.length; i++) {
                row[i] = readColumnValues(rs, i);
            }
            return row;
        }
    };

    /**
     * Returns the given column value from the ResultSet after applying common CLOB and
     * Timestamp handling.
     */
    private Object readColumnValues(ResultSet rs, int i) throws SQLException {
        Object val = rs.getObject(i + 1);
        Clob clob = (Clob) val;
        val = clob.getSubString(1, (int) clob.length());
        return val;
    }

    /**
     * Create a default PersistenceService object application context properties
     */
    public PersistenceService() throws IOException {
        super();
        sessionFactory = getSessionFactory();
    }

    /**
     * Create a PersistenceService with the provided
     * {@link org.hibernate.SessionFactory}
     *
     * @param sessionFactory {@link org.hibernate.SessionFactory}
     */
    public PersistenceService(SessionFactory sessionFactory) {
        super();
        setSessionFactory(sessionFactory);
    }

    @Override
    public Session openSession() {
        return sessionFactory.openSession();
    }

    /**
     * Shutdown the {@link org.hibernate.SessionFactory}
     */
    public void shutdown() {
        sessionFactory.close();
    }

    protected void close(Session session) {
        if(session != null) {
            try {
                session.close();
            } catch (HibernateException e) {
                logger.error("Error closing Session", e);
            }
        }
    }

    // -------------------------------------------------
    @Override
    public List query(String hql) {
        return this.query(hql, EMPTY);
    }

    @Override
    public List query(String hql, Object... params) {
        return query(hql, null, params);
    }

    @Override
    public List query(String hql, QueryPostProcessor post, Object... params) {
        Session session = null;
        try {
            session = openSession();
            Query query = session.createQuery(hql);
            for (int i = 0; i < params.length; i++) {
                query = query.setParameter(i, params[i]);
            }
            List result = query.list();
            if (post != null) {
                return post.processListResult(result);
            } else {
                return result;
            }
        } catch (JDBCException e) {
            logger.error("JDBCException executing query '" + hql + "'. Database may be down or unavailable.", e);
            throw e;
        } catch (HibernateException e) {
            logger.error("Hibernate exception executing query '"
                    + hql + "'", e);
            throw e;
        } finally {
            close(session);
        }
    }

    @Override
    public List query(String hql, Map<String, Object> namedParameters) {
        return query(hql, namedParameters, null);
    }

    @Override
    public List query(String hql, Map<String, Object> namedParameters, QueryPostProcessor post) {
        Session session = null;
        if (namedParameters == null) {
            logger.debug("namedParameters required but not provided");
            return null;
        }

        try {
            session = openSession();
            Query query = session.createQuery(hql);
            for (Map.Entry<String, Object> entry : namedParameters.entrySet()) {
                query = query.setParameter(entry.getKey(), entry.getValue());
            }
            List result = query.list();
            if (post != null) {
                return post.processListResult(result);
            } else {
                return result;
            }
        } catch (JDBCException e) {
            logger.error("JDBCException executing query '" + hql + "'. Database may be down or unavailable.", e);
            throw e;
        } catch (HibernateException e) {
            logger.error("Hibernate exception executing query '" + hql + "'", e);
            throw e;
        } finally {
            close(session);
        }
    }

    @Override
    public List pagedQuery(String hql, Map<String, Object> namedParameters, Integer pageStart, Integer pageSize) {
        return pagedQuery(hql, namedParameters, pageStart, pageSize, null);
    }

    @Override
    public List pagedQuery(String hql, Map<String, Object> namedParameters, Integer pageStart, Integer pageSize, QueryPostProcessor post) {
        Session session = null;
        if (pageSize == null || pageStart == null) {
            logger.debug("pageStart and pageSize required but not provided");
            return null;
        }
        try {
            session = openSession();
            Query query = session.createQuery(hql);
            for (Map.Entry<String, Object> entry : namedParameters.entrySet()) {
                query = query.setParameter(entry.getKey(), entry.getValue());
            }
            query.setFirstResult(pageStart);
            query.setMaxResults(pageSize);
            List result = query.list();
            if (post != null) {
                return post.processListResult(result);
            } else {
                return result;
            }
        } catch (JDBCException e) {
            logger.error("JDBCException executing query '" + hql + "'. Database may be down or unavailable.", e);
            throw e;
        } catch (HibernateException e) {
            logger.error("Hibernate exception executing query '" + hql + "'", e);
            throw e;
        } finally {
            close(session);
        }
    }

    /**
     * Inserts the object if it's new, otherwise updates the object
     *
     * @param item the object to persist
     * @param <T>  the type of {@code item}
     * @return the saved object
     */
    @Override
    public <T extends SimplePersistedObject> T save(T item) throws HibernateException {
        Session session = null;
        Transaction txn = null;
        try {
            session = openSession();
            txn = session.beginTransaction();
            item.onSave();
            session.saveOrUpdate(item);
            txn.commit();
            return item;
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hibernate exception executing save", e);
            } else {
                logger.warn("Hibernate exception executing save, Reason: {}", e.getMessage());
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    /**
     * Inserts the object
     *
     * @param item         the object to persist
     * @param saveOrUpdate if false, Inserts one objects, if existed, throws HibernateException. If true, Check if
     *                     objects exists, if existed, related object will be updated
     * @param <T>          the type of {@code item}
     * @return the saved object
     */
    @Override
    public <T> T save(T item, boolean saveOrUpdate) throws HibernateException {
        Session session = null;
        Transaction txn = null;
        try {
            session = openSession();
            txn = session.beginTransaction();
            if (saveOrUpdate) {
                session.saveOrUpdate(item);
            } else {
                session.save(item);
            }
            txn.commit();
            return item;
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hibernate exception executing save", e);
            } else {
                logger.warn("Hibernate exception executing save, Reason: {}", e.getMessage());
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    /**
     * Permanently and irrevocably deletes an item from the database.
     *
     * @param item the item to delete
     * @param <T>  the type of {@code item}
     * @return the deleted item
     * @throws HibernateException if an error occurs
     */
    @Override
    public <T> T delete(T item) throws HibernateException {
        Session session = null;
        Transaction txn = null;
        try {
            session = openSession();
            txn = session.beginTransaction();
            session.delete(item);
            txn.commit();
            return item;
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hibernate exception executing delete", e);
            } else {
                logger.warn("Hibernate exception executing delete, Reason: {}", e.getMessage());
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    @Override
    public <T extends SimplePersistedObject> List<T> saveAll(List<T> itemList) {
        Session session = null;
        Transaction txn = null;
        try {
            session = openSession();
            txn = session.beginTransaction();
            for (T save : itemList) {
                save.onSave();
                session.saveOrUpdate(save);
            }
            txn.commit();
            /*
            for (Object save : itemList) {
                listeners.firePropertyChange("saved", null, save);
            }*/
            return itemList;
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hibernate exception executing saveAll", e);
            } else {
                logger.warn("Hibernate exception executing saveAll, Reason: {}", e.getMessage());
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    @Override
    public <T> T save(T item) throws HibernateException {
        return save(item, true);
    }

    /**
     * Permanently and irrevocably deletes an item from the database.
     *
     * @param item the item to delete
     * @param <T>  the type of {@code item}
     * @return the deleted item
     * @throws HibernateException if an error occurs
     */
    @Override
    public <T extends SimplePersistedObject> T delete(T item) {
        Session session = null;
        Transaction txn = null;
        try {
            session = openSession();
            txn = session.beginTransaction();
            session.delete(item);
            txn.commit();
            return item;
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hibernate exception executing delete", e);
            } else {
                logger.warn("Hibernate exception executing delete, Reason: {}", e.getMessage());
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    @Override
    public <T extends SimplePersistedObject> List<T> mergeAll(List<T> itemList) throws HibernateException {
        Session session = null;
        Transaction txn = null;
        try {
            session = openSession();
            txn = session.beginTransaction();
            for (T save : itemList) {
                save.onSave();
                session.merge(save);
            }
            txn.commit();
            /*
            for (Object save : itemList) {
                listeners.firePropertyChange("saved", null, save);
            }*/
            return itemList;
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Hibernate exception executing saveAll", e);
            } else {
                logger.warn("Hibernate exception executing saveAll, Reason: {}", e.getMessage());
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    @Override
    public List sqlQuery(String sql, Object... params) {
        return sqlQuery0(sql, 0, params, mapBuilder);
    }

    @Override
    public List sqlQueryLimit(String sql, int limit, Object... params) {
        return sqlQuery0(sql, limit, params, mapBuilder);
    }

    @Override
    public List<Object[]> sqlQueryArray(String sql, Object... params) {
        return sqlQuery0(sql, 0, params, arrayBuilder);
    }

    /**
     * Internal query implementation that uses the RowBuilder strategy for
     * reading rows out of the ResultSet.
     */
    @SuppressWarnings("rawtypes")
    private List sqlQuery0(String sql, int limit,
                           Object[] params,
                           RowBuilder builder) {
        Session session = null;
        try {
            session = openSession();
            return session.doReturningWork
                    (new LimitedWork(sql, limit, params, builder));
        } catch (HibernateException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Exception executing sql '" + sql + "'" + " with " + params.length + " parameters, Reason:", e);
            } else {
                logger.error("Exception executing sql '" + sql + "'" + " with " + params.length + " parameters, Reason:", e.getMessage());
            }

            throw e;
        } finally {
            close(session);
        }
    }


    @Override
    public int sqlUpdate(String sql, Object... params) {
        Session session = null;
        try {
            session = openSession();
            return session.doReturningWork(
                    new UpdateWork(sql, params));
        } catch (Exception ex) {
            for (Object param : params) {
                sql = sql + "," + param.toString();
            }
            logger.error("Failed to execute sql: {};" +
                    " with exception: {}", sql, ex.getMessage());
            throw ex;
        } finally {
            close(session);
        }
    }

    @Override
    public <T extends UniqueNamedArtifact> T findObjectByName(Class<T> clazz, String name) {
        return findObjectByName(clazz, name, null);
    }

    @Override
    public <T extends SimplePersistedObject> T findSimpleObjectById(Class<T> clazz, String objId, String typeName) {
        T ans = null;
        logger.trace("#findSimpleObjectById recv clazz {}, objId non-null status {}", clazz, Objects.nonNull(objId));
        if (Strings.isBlank(objId)) {
            logger.trace("#findSimpleObjectById recv id is empty return null!");
            return null;
        }
        // where (id=?0 or lower(name)=lower(?1))
        String hql = "from " + clazz.getName() + " where (id=?0 and TRUSTEE_TYPE=?1)";
        List<T> found = query(hql, objId, typeName);
        int foundLen = CollectionUtils.isEmpty(found) ? 0 : found.size();
        ans = CollectionUtils.isEmpty(found) ? null : found.get(0);
        logger.trace("#findSimpleObjectById ans non-null status {} query foundLen {}", Objects.nonNull(ans), foundLen);
        return ans;
    }

    @Override
    public <T extends SimplePersistedObject> T findSimpleObjectById(Class<T> clazz, String objId) {
        T ans = null;
        logger.trace("#findSimpleObjectById recv clazz {}, objId non-null status {}", clazz, Objects.nonNull(objId));
        if (StringUtils.hasText(objId)) {
            logger.trace("#findSimpleObjectById recv id is empty return null!");
            return ans;
        }
        String hql = "from " + clazz.getName() + " where (id=?0)";
        List<T> found = query(hql, objId);
        int foundLen = CollectionUtils.isEmpty(found) ? 0 : found.size();
        ans = CollectionUtils.isEmpty(found) ? null : found.get(0);
        logger.trace("#findSimpleObjectById ans non-null status {} query foundLen {}", Objects.nonNull(ans), foundLen);
        return ans;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends UniqueNamedArtifact> T findObjectByName(
            Class<T> clazz,
            String name,
            QueryPostProcessor post) {

        if (name == null) {
            return null;
        }

        // ---- Step 1: fetch Hibernate 6 Metamodel ----
        final Metamodel metamodel = sessionFactory.getMetamodel();
        EntityType<T> entityType;
        try {
            entityType = metamodel.entity(clazz);
        } catch (IllegalArgumentException e) {
            logger.warn("Class {} is not a mapped entity", clazz.getName());
            return null;
        }

        // fetch property names
        Set<String> propertyNames = entityType.getAttributes()
                .stream()
                .map(attr -> attr.getName().toLowerCase())
                .collect(Collectors.toSet());

        // ---- Step 2: build HQL ----
        StringBuilder hql = new StringBuilder("from ")
                .append(clazz.getName())
                .append(" where lower(name) = lower(:name)");

        if (propertyNames.contains("deleted")) {
            hql.append(" and deleted is null");
        }

        // ---- Step 3: execute query  ----
        Session session = openSession();
        Query<T> query = session.createQuery(hql.toString(), clazz);
        query.setParameter("name", name);

        List<T> found = query.list();

        // ---- Step 4: QueryPostProcessor ----
        if (post != null && !found.isEmpty()) {
            post.processFindResult(found.get(0));
        }

        return found.isEmpty() ? null : found.get(0);
    }


    @Override
    public <T extends PersistedObject> T findObjectById(Class<T> clazz, String id) {
        return findObjectById(clazz, id, null);
    }

    @Override
    public <T extends PersistedObject> T findObjectById(Class<T> clazz, String id, QueryPostProcessor post) {

        if (id == null) {
            return null;
        }

        // ---- Step 1: fetch Hibernate 6 metadata instance ----
        final Metamodel metamodel = sessionFactory.getMetamodel();
        EntityType<T> entityType;
        try {
            entityType = metamodel.entity(clazz);
        } catch (IllegalArgumentException e) {
            logger.warn("Class {} is not an entity", clazz.getName());
            return null;
        }

        // fetch properties name
        Set<String> propertyNames = entityType.getAttributes()
                .stream()
                .map(attr -> attr.getName().toLowerCase())
                .collect(Collectors.toSet());

        // ---- Step 2: build HQL ----
        StringBuilder hql = new StringBuilder("from ")
                .append(clazz.getName())
                .append(" where id = :id");

        if (propertyNames.contains(PersistedObject.PERSISTED_OBJECT_DELETED_ATTRIBUTE.toLowerCase())) {
            hql.append(" and deleted is null");
        }

        // ---- Step 3: execute query ----
        Session session = openSession();
        Query<T> query = session.createQuery(hql.toString(), clazz);
        query.setParameter("id", id);

        List<T> found = query.list();

        // ---- Step 4: handle QueryPostProcessor ----
        if (post != null && !found.isEmpty()) {
            post.processFindResult(found.get(0));
        }

        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public <T extends PersistedObject> T findObjectByIdOrName(Class<T> clazz, String idOrName) {
        return findObjectByIdOrName(clazz, idOrName, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends PersistedObject> T findObjectByIdOrName(
            Class<T> clazz,
            String idName,
            QueryPostProcessor post) {

        if (idName == null) {
            logger.debug("Name or unique identifier required but not provided");
            return null;
        }

        // ---- Step 1: Fetch Hibernate 6 的 Metamodel  ----
        final Metamodel metamodel = sessionFactory.getMetamodel();
        EntityType<T> entityType;
        try {
            entityType = metamodel.entity(clazz);
        } catch (IllegalArgumentException e) {
            logger.warn("Class {} is not a mapped entity", clazz.getName());
            return null;
        }

        // fetch all property names
        Set<String> propertyNames = entityType.getAttributes()
                .stream()
                .map(attr -> attr.getName().toLowerCase())
                .collect(Collectors.toSet());

        // ---- Step 2: Build HQL ----
        StringBuilder hql = new StringBuilder("from ")
                .append(clazz.getName())
                .append(" where (id = :idName or lower(name) = lower(:idName))");

        if (propertyNames.contains(PersistedObject.PERSISTED_OBJECT_DELETED_ATTRIBUTE.toLowerCase())) {
            hql.append(" and deleted is null");
        }

        // ---- Step 3: execute query  ----
        Session session = openSession();
        Query<T> query = session.createQuery(hql.toString(), clazz);
        query.setParameter("idName", idName);

        List<T> found = query.list();

        // ---- Step 4: QueryPostProcessor Hook ----
        if (post != null && !found.isEmpty()) {
            post.processFindResult(found.get(0));
        }

        return found.isEmpty() ? null : found.get(0);
    }


    /**
     * Query the data store for objects that match all of the parameters
     *
     * @param clazz  is the class of the object
     * @param params is a {@link java.util.Map} of {@link java.lang.String} keys and {@link java.lang.String} values
     * @param <T>    is the type of the object
     * @return {@link java.util.List} of objects found
     */
    @Override
    public <T extends PersistedObject> List<T> findObjectsByAndingParams(Class<T> clazz,
                                                                         Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }

        // -- step 1 fetch metadata from hibernate 6
        final Metamodel metamodel = sessionFactory.getMetamodel();
        EntityType<T> entityType;
        try {
            entityType = metamodel.entity(clazz);
        } catch (IllegalArgumentException e) {
            logger.warn("Class {} is not an entity", clazz.getName());
            return null;
        }

        Set<String> propertyNames = entityType.getAttributes()
                .stream()
                .map(attr -> attr.getName().toLowerCase())
                .collect(Collectors.toSet());

        // ---- Step 2: Build HQL----
        StringBuilder hql = new StringBuilder("from ")
                .append(clazz.getName())
                .append(" where ");

        Map<String, Object> paramBindings = new HashMap<>();
        int index = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (index > 0) {
                hql.append(" and ");
            }
            String key = entry.getKey();
            hql.append(key).append(" = :").append(key);
            paramBindings.put(key, entry.getValue());
            index++;
        }

        // deleted attribute field filter
        if (propertyNames.contains("deleted")) {
            if (!params.isEmpty()) {
                hql.append(" and ");
            }
            hql.append("deleted is null");
        }

        // ---- Step 3: execute query ----
        Session session = openSession();
        Query<T> query = session.createQuery(hql.toString(), clazz);
        paramBindings.forEach(query::setParameter);

        List<T> found = query.list();

        return found.isEmpty() ? null : found;
    }

    @Override
    public Object querySingle(String hql) {
        Map<String, Object> namedParameters = new HashMap<>();
        return querySingle(hql, namedParameters);
    }

    /**
     * Performs a query for a single object
     *
     * @param hql             the HQL query text
     * @param namedParameters the parameters of the query
     * @return the single unique result
     */
    @Override
    public Object querySingle(String hql, Map<String, Object> namedParameters) {
        return querySingle(hql, namedParameters, null);
    }

    @Override
    public String queryByJdbc(String sql, Map<Integer, String> namedParameters, int index) {
        String content = null;
        InputStream inputStream = null;
        ByteArrayOutputStream infoStreams = null;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= namedParameters.size(); i++) {
                preparedStatement.setString(i, namedParameters.get(i));
            }
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                inputStream = resultSet.getBinaryStream(index + 1);
                infoStreams = new ByteArrayOutputStream();
                int len = 0;
                byte[] bytes = new byte[1024];
                while ((len = inputStream.read(bytes)) != -1) {
                    infoStreams.write(bytes, 0, len);
                }
                content = infoStreams.toString("UTF-8");
            }
            return content;
        } catch (SQLException e) {
            logger.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            return null;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return content;
        } finally {
            try {
                if (infoStreams != null)
                    infoStreams.close();
                if (inputStream != null)
                    inputStream.close();
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    /**
     * Executes an INSERT, UPDATE, or DELETE statement
     *
     * @param hql             hql statement to be executed
     * @param namedParameters parameters of the statement
     * @return the number of rows effects by the query
     */
    @Override
    public int executeQuery(String hql, Map<String, Object> namedParameters) {
        Session session = null;
        Transaction txn = null;
        if (namedParameters == null) {
            logger.debug("namedParameters required but not provided");
            return -1;
        }
        try {
            session = openSession();
            Query query = session.createQuery(hql);
            for (Map.Entry<String, Object> entry : namedParameters.entrySet()) {
                query = query.setParameter(entry.getKey(), entry.getValue());
            }
            txn = session.beginTransaction();
            int result = query.executeUpdate();
            txn.commit();
            return result;
        } catch (JDBCException e) {
            logger.error("JDBCException executing query '" + hql + "'. Database may be down or unavailable.", e);
            rollback(txn);
            throw e;
        } catch (HibernateException e) {
            logger.error("Hibernate exception executing query '" + hql + "'", e);
            rollback(txn);
            throw e;
        } catch (Exception e) {
            logger.error("Exception executing query '" + hql + "'", e);
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }

    protected void rollback(Transaction txn) {
        if (txn != null) {
            try {
                txn.rollback();
            } catch (HibernateException e) {
                logger.error("Error rolling back Transaction", e);
            }
        }
    }

    /**
     * Performs a query for a single object
     *
     * @param hql             the HQL query text
     * @param namedParameters the parameters of the query
     * @param post            the post processor that handles
     *                        the result of the query
     * @return the single unique result
     */
    @Override
    public Object querySingle(String hql, Map<String, Object> namedParameters, QueryPostProcessor post) {
        Session session = null;
        if (namedParameters == null) {
            logger.debug("namedParameters required but not provided");
            return null;
        }
        try {
            session = openSession();
            Query query = session.createQuery(hql);
            for (Map.Entry<String, Object> entry : namedParameters.entrySet()) {
                query = query.setParameter(entry.getKey(), entry.getValue());
            }
            Object result = query.uniqueResult();
            if (post != null) {
                return post.processFindResult(result);
            } else {
                return result;
            }
        } catch (JDBCException e) {
            logger.error("JDBCException executing query '" + hql + "'. Database may be down or unavailable.", e);
            throw e;
        } catch (HibernateException e) {
            logger.error("Hibernate exception executing query '" + hql + "'", e);
            throw e;
        } finally {
            close(session);
        }
    }

    /**
     * Returns an object if found, or creates the object and return it.
     *
     * @param hql             the query text
     * @param namedParameters the query parameters
     * @param item            the object to look up or create
     */
    @Override
    public <T extends NamedArtifact> T findOrSave(String hql, Map<String, Object> namedParameters, T item) {
        Session session = null;
        Transaction txn = null;
        if (namedParameters == null) {
            logger.debug("namedParameters required but not provided");
            return null;
        }
        try {
            session = openSession();
            Query query = session.createQuery(hql);
            for (Map.Entry<String, Object> entry : namedParameters.entrySet()) {
                query = query.setParameter(entry.getKey(), entry.getValue());
            }
            T found = (T) query.uniqueResult();
            txn = session.beginTransaction();
            item.setModifiedDate(new Date());
            session.saveOrUpdate(item);
            txn.commit();
            return item;
        } catch (Exception e) {
            if (e instanceof JDBCException) {
                logger.error("JDBCException executing query '" + hql + "'. Database may be down or unavailable.", e);
            } else if (e instanceof HibernateException) {
                logger.error("Hibernate exception executing query '" + hql + "'", e);
            }
            rollback(txn);
            throw e;
        } finally {
            close(session);
        }
    }


    // ========== internal classes ==========
    private static abstract class BaseWork {
        protected void close(final Statement stmt) {
            if (stmt != null) {
                try {
                    stmt.close();
                } catch (SQLException e) {
                    logger.error("Error closing " +
                            "Statement", e);
                }
            }
        }

        protected void close(final ResultSet rs) {
            if (rs != null) {
                try {
                    rs.close();
                } catch (SQLException e) {
                    logger.error("Error closing ResultSet", e);
                }
            }
        }

        protected void rollback(final Connection conn) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException e) {
                    logger.error("Error rolling back Connection", e);
                }
            }
        }

        /**
         * Returns the lower-cased column names in the ResultSet.
         */
        protected String[] columnNames(final ResultSet rs) throws SQLException {
            final String[] cols = getColumnNames(rs);
            for (int i = 0; i < cols.length; i++) {
                cols[i] = cols[i].toLowerCase();
            }
            return cols;
        }

        /**
         * Returns the column names in the ResultSet.
         */
        protected String[] getColumnNames(final ResultSet rs)
                throws SQLException {
            final ResultSetMetaData meta = rs.getMetaData();
            final String[] names = new String[meta.getColumnCount()];
            for (int i = 0; i < names.length; i++) {
                names[i] = meta.getColumnName(i + 1);
            }
            return names;
        }
    }

    private static class UpdateWork extends BaseWork
            implements ReturningWork<Integer> {
        final private String sql;
        final private Object[] params;

        public UpdateWork(String sql, Object... params) {
            this.sql = sql;
            this.params = params;
        }

        @Override
        public Integer execute(Connection connection) throws SQLException {
            PreparedStatement stmt = null;
            boolean autoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);
                stmt = connection.prepareStatement(sql);
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                int rows = stmt.executeUpdate();
                connection.commit();
                return rows;
            } catch (SQLException e) {
                logger.error("Exception executing sql '" + sql + "'" + " with " + params.length + " parameters", e);
                rollback(connection);
                throw e;
            } finally {
                close(stmt);
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static class LimitedWork extends
            BaseWork implements ReturningWork<List<Object>> {
        final private String sql;
        final private int limit;
        final private Object[] params;
        final private RowBuilder builder;

        public LimitedWork(String sql, int limit, Object[] params, RowBuilder builder) {
            this.sql = sql;
            this.limit = limit;
            this.params = params;
            this.builder = builder;
        }

        @Override
        public List<Object> execute(Connection connection) throws SQLException {
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = connection.prepareStatement(sql);
                stmt.setMaxRows(limit);
                for (int i = 0; i < params.length; i++) {
                    if (params[i] == null) {
                        // Oracle's null handling always works with VARCHAR
                        stmt.setNull(i + 1, Types.VARCHAR);
                    } else {
                        stmt.setObject(i + 1, params[i]);
                    }
                }
                rs = stmt.executeQuery();

                List<Object> found = new ArrayList<>();
                while (rs.next()) {
                    found.add(builder.buildRow(rs, columnNames(rs)));
                }
                stmt.setMaxRows(0); // reset in case the stmt is reused by pooling
                return found;
            } finally {
                close(rs);
                close(stmt);
            }
        }
    }
}
