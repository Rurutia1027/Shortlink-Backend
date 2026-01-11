package org.tus.common.domain.persistence.integration;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.dao.HqlQueryBuilder;
import org.tus.common.domain.persistence.QueryService;
import org.tus.common.domain.persistence.integration.config.PersistenceTestContainerDBConfig;
import org.tus.common.domain.persistence.integration.entity.TestPersistedEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringJUnitConfig(classes = PersistenceTestContainerDBConfig.class)
@Transactional
public class QueryServiceHqlBuilderExtendedIT {

    @Autowired
    private QueryService queryService;

    private String entityName = "TestPersistedEntity-";

    @BeforeAll
    static void setupEntitiesOnce(@Autowired QueryService queryService) {
        // Query all existing entities using HQL
        String hql = "FROM TestPersistedEntity";
        List<TestPersistedEntity> existing = queryService.query(hql, Map.of());

        // Delete all existing rows if any
        if (!existing.isEmpty()) {
            TestPersistedEntity entity = new TestPersistedEntity();
            TestPersistedEntity deletedEntity = queryService.delete(entity);
            log.info("Entity ID " + deletedEntity.getId() + " is deleted" );
        }

        // Insert entities
        for (int i = 0; i < 5; i++) {
            TestPersistedEntity entity = new TestPersistedEntity();
            entity.setName("TestPersistedEntity-" + i);
            queryService.save(entity);
        }

        TestPersistedEntity deletedEntity = new TestPersistedEntity();
        deletedEntity.setName("TestPersistedEntity-deleted");
        deletedEntity.setDeleted("true");
        queryService.save(deletedEntity);
    }

    @Test
    void testQueryByExactName() {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(TestPersistedEntity.class, "e")
                .select("e")
                .eq("e.name", entityName + "2")
                .and()
                .isNull("e.deleted")
                .build();

        Map<String, Object> params = builder.getInjectionParameters();
        builder.clear();

        List<TestPersistedEntity> results = queryService.query(hql, params);

        assertEquals(1, results.size());
        assertEquals(entityName + "2", results.get(0).getName());
    }

    @Test
    void testQueryLikeAndLogicalGrouping() {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(TestPersistedEntity.class, "e")
                .select("e")
                .open()
                .like("e.name", entityName + "1")
                .or()
                .like("e.name", entityName + "3")
                .close()
                .and()
                .isNull("e.deleted")
                .build();

        List<TestPersistedEntity> results = queryService.query(hql, builder.getInjectionParameters());
        builder.clear();

        assertEquals(2, results.size());
        List<String> names = results.stream().map(TestPersistedEntity::getName).collect(Collectors.toList());
        assertTrue(names.contains(entityName + "1"));
        assertTrue(names.contains(entityName + "3"));
    }

    @Test
    void testQueryAllActiveEntities() {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(TestPersistedEntity.class, "e")
                .select("e")
                .isNull("e.deleted")
                .build();

        List<TestPersistedEntity> results = queryService.query(hql, builder.getInjectionParameters());
        builder.clear();

        assertEquals(5, results.size());
    }

    @Test
    void testQueryDeletedEntities() {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(TestPersistedEntity.class, "e")
                .select("e")
                .eq("e.deleted", "true")
                .build();

        List<TestPersistedEntity> results = queryService.query(hql, builder.getInjectionParameters());
        builder.clear();

        assertEquals(1, results.size());
        assertEquals(entityName + "deleted", results.get(0).getName());
    }

    @Test
    void testQueryWithOrdering() {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(TestPersistedEntity.class, "e")
                .select("e")
                .isNull("e.deleted")
                .orderBy("e.name", true) // ascending
                .build();

        List<TestPersistedEntity> results = queryService.query(hql, builder.getInjectionParameters());
        builder.clear();

        assertEquals(5, results.size());
        assertEquals(entityName + "0", results.get(0).getName());
        assertEquals(entityName + "4", results.get(4).getName());
    }

    @Test
    void testQueryWithMultipleConditions() {
        HqlQueryBuilder builder = new HqlQueryBuilder();
        String hql = builder.fromAs(TestPersistedEntity.class, "e")
                .select("e")
                .open()
                .eq("e.name", entityName + "0")
                .or()
                .eq("e.name", entityName + "1")
                .close()
                .and()
                .isNull("e.deleted")
                .build();

        List<TestPersistedEntity> results = queryService.query(hql, builder.getInjectionParameters());
        builder.clear();

        assertEquals(2, results.size());
        List<String> names = results.stream().map(TestPersistedEntity::getName).collect(Collectors.toList());
        assertTrue(names.contains(entityName + "0"));
        assertTrue(names.contains(entityName + "1"));
    }
}