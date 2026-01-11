package org.tus.common.domain.persistence.integration;

import org.hibernate.SessionFactory;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.persistence.PersistenceService;
import org.tus.common.domain.persistence.entity.TestPersistedEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Since we care more about the integration usage of {@link PersistenceService} and
 * {@link org.tus.common.domain.dao.HqlQueryBuilder}
 *
 * So, this integration test cases gonna be deprecated and delete soon, to avoid setup two
 * test containers of postgres cause conflict result in test result error and resource
 * consuming issues in ci pipeline.
 */
@Deprecated
@Ignore
@SpringJUnitConfig(classes = org.tus.common.domain.persistence.config.PersistenceConfig.class)
@Transactional
public class PersistenceServiceIntegrationTest {
    @Autowired
    private PersistenceService persistenceService;

    @Autowired
    private SessionFactory sessionFactory;

    @BeforeEach
    void setup() {
        assertNotNull(persistenceService, "PersistenceService should be autowired correctly");
        assertNotNull(sessionFactory, "SessionFactory should be autowired correctly");
    }

    @Test
    void testSaveAndFind() {
        // -- save entity --
        TestPersistedEntity entity = new TestPersistedEntity();
        entity.setName("SpringH2PersistenceITTest");
        TestPersistedEntity savedEntity = persistenceService.save(entity);

        // -- query entity --
        TestPersistedEntity found =
                persistenceService.findObjectById(TestPersistedEntity.class,
                        savedEntity.getId());
        assertNotNull(found);
        assertEquals(entity.getName(), found.getName());
    }
}
