package org.tus.common.domain.persistence.container;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.tus.common.domain.persistence.PersistenceService;
import org.tus.common.domain.persistence.config.PersistenceTestContainerConfig;
import org.tus.common.domain.persistence.entity.TestPersistedEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


@Testcontainers
@SpringJUnitConfig(classes = PersistenceTestContainerConfig.class)
@Transactional
public class PersistenceServicePostgresTest {
    @Autowired
    private PersistenceService persistenceService;

    @BeforeEach
    void initOK() {
        assertNotNull(persistenceService);
    }

    @Test
    void testSaveAndFind() {
        TestPersistedEntity entity = new TestPersistedEntity();
        String name = UUID.randomUUID().toString();
        entity.setName(name);
        TestPersistedEntity saved = persistenceService.save(entity);

        TestPersistedEntity found =
                persistenceService.findObjectById(TestPersistedEntity.class, saved.getId());
        assertNotNull(found);
        assertEquals(entity.getName(), found.getName());

        TestPersistedEntity delete = persistenceService.delete(saved);
        assertNotNull(delete.getId());
        TestPersistedEntity queryAfterDelete =
                persistenceService.findObjectById(TestPersistedEntity.class, delete.getId());
        assertNull(queryAfterDelete);
    }
}
