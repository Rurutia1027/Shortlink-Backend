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
