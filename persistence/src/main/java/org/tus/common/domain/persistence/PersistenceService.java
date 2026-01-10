package org.tus.common.domain.persistence;

import lombok.RequiredArgsConstructor;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.tus.common.domain.model.NamedArtifact;
import org.tus.common.domain.model.PersistedObject;
import org.tus.common.domain.model.SimplePersistedObject;
import org.tus.common.domain.model.UniqueNamedArtifact;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * Provides query access to the repository by wrapping Hibernate. All access to the
 * repository should be done through this class.
 */

@RequiredArgsConstructor
public class PersistenceService implements QueryService {
    private static final Object[] EMPTY = {};
    private final  SessionFactory sessionFactory;
    private final DataSource dataSource;

    @Override
    public Session openSession() {
        return null;
    }

    @Override
    public List query(String hql) {
        return List.of();
    }

    @Override
    public List query(String hql, Object... params) {
        return List.of();
    }

    @Override
    public List query(String hql, QueryPostProcessor post, Object... params) {
        return List.of();
    }

    @Override
    public List query(String hql, Map<String, Object> namedParams) {
        return List.of();
    }

    @Override
    public List query(String hql, Map<String, Object> namedParams, QueryPostProcessor post) {
        return List.of();
    }

    @Override
    public List pagedQuery(String hql, Map<String, Object> namedParameters, Integer pageStart, Integer pageSize) {
        return List.of();
    }

    @Override
    public List pagedQuery(String hql, Map<String, Object> namedParameters, Integer pageStart, Integer pageSize, QueryPostProcessor post) {
        return List.of();
    }

    @Override
    public <T extends SimplePersistedObject> T save(T item) {
        return null;
    }

    @Override
    public <T> T save(T item, boolean saveOrUpdate) throws HibernateException {
        return null;
    }

    @Override
    public <T> T delete(T item) throws HibernateException {
        return null;
    }

    @Override
    public <T extends SimplePersistedObject> List<T> saveAll(List<T> itemList) {
        return List.of();
    }

    @Override
    public <T> T save(T item) throws HibernateException {
        return null;
    }

    @Override
    public <T extends SimplePersistedObject> T delete(T item) {
        return null;
    }

    @Override
    public <T extends SimplePersistedObject> List<T> mergeAll(List<T> itemList) throws HibernateException {
        return List.of();
    }

    @Override
    public List sqlQuery(String sql, Object... params) {
        return List.of();
    }

    @Override
    public List sqlQueryLimit(String sql, int limit, Object... params) {
        return List.of();
    }

    @Override
    public List<Object[]> sqlQueryArray(String sql, Object... params) {
        return List.of();
    }

    @Override
    public int sqlUpdate(String sql, Object... params) {
        return 0;
    }

    @Override
    public <T extends UniqueNamedArtifact> T findObjectByName(Class<T> clazz, String name) {
        return null;
    }

    @Override
    public <T extends SimplePersistedObject> T findSimpleObjectById(Class<T> clazz, String objId, String typeName) {
        return null;
    }

    @Override
    public <T extends SimplePersistedObject> T findSimpleObjectById(Class<T> clazz, String objId) {
        return null;
    }

    @Override
    public <T extends UniqueNamedArtifact> T findObjectByName(Class<T> clazz, String name, QueryPostProcessor post) {
        return null;
    }

    @Override
    public <T extends PersistedObject> T findObjectById(Class<T> clazz, String id) {
        return null;
    }

    @Override
    public <T extends PersistedObject> T findObjectById(Class<T> clazz, String id, QueryPostProcessor post) {
        return null;
    }

    @Override
    public <T extends PersistedObject> T findObjectByIdOrName(Class<T> clazz, String idOrName) {
        return null;
    }

    @Override
    public <T extends PersistedObject> T findObjectByIdOrName(Class<T> clazz, String idName, QueryPostProcessor post) {
        return null;
    }

    @Override
    public <T extends PersistedObject> List<T> findObjectsByAndingParams(Class<T> tClass, Map<String, String> params) {
        return List.of();
    }

    @Override
    public Object querySingle(String hql) {
        return null;
    }

    @Override
    public Object querySingle(String hql, Map<String, Object> namedParameters) {
        return null;
    }

    @Override
    public String queryByJdbc(String hql, Map<Integer, String> namedParameters, int i) {
        return "";
    }

    @Override
    public int executeQuery(String hql, Map<String, Object> namedParameters) {
        return 0;
    }

    @Override
    public Object querySingle(String hql, Map<String, Object> namedParameters, QueryPostProcessor post) {
        return null;
    }

    @Override
    public <T extends NamedArtifact> T findOrSave(String hql, Map<String, Object> namedParameters, T item) {
        return null;
    }
}
