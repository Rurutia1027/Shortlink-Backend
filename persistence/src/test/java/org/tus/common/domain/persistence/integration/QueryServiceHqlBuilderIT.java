package org.tus.common.domain.persistence.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.persistence.QueryService;
import org.tus.common.domain.persistence.integration.config.PersistenceTestContainerDBConfig;
import org.tus.common.domain.persistence.integration.entity.TestPersistedEntity;
import org.tus.shortlink.base.tookit.StringUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringJUnitConfig(classes = PersistenceTestContainerDBConfig.class)
@Transactional
public class QueryServiceHqlBuilderIT {
    @Autowired
    private QueryService queryService;
    private String entityName = String.format("TestPersistedEntity-" + UUID.randomUUID());

    @BeforeEach
    void createTableViaEntity() {
        // -- save entity --
        TestPersistedEntity entity = new TestPersistedEntity();
        entity.setName(entityName);
        TestPersistedEntity savedEntity = queryService.save(entity);

        // saved entity id number should be created by the server side it cannot be null or
        // blank; saved item located in test container setup PG db table: test_persisted_entity
        // test container setup PG DB will be destroyed when test finish
        assertTrue(StringUtils.hasText(savedEntity.getId()));
        assertEquals(entity.getName(), savedEntity.getName());
    }

    @Test
    void initOk() {
        assertNotNull(queryService);
        TestPersistedEntity queryEntity =
                queryService.findObjectByIdOrName(TestPersistedEntity.class, entityName);
        assertNotNull(queryEntity);
        assertNotNull(queryEntity.getId());
        assertEquals(entityName, queryEntity.getName());
    }
}
