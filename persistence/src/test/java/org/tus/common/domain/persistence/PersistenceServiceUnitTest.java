package org.tus.common.domain.persistence;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tus.common.domain.persistence.entity.TestPersistedEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PersistenceServiceUnitTest {

    private SessionFactory sessionFactory;
    private PersistenceService persistenceService;

    @BeforeEach
    void setup() {
        sessionFactory = new MetadataSources(
                new StandardServiceRegistryBuilder()
                        .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                        .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                        .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                        .applySetting("hibernate.connection.url", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                        .applySetting("hibernate.current_session_context_class", "thread")
                        .build()
        )
                .addAnnotatedClass(TestPersistedEntity.class)
                .buildMetadata()
                .buildSessionFactory();

        persistenceService = new PersistenceService(sessionFactory);
    }

    @Test
    void testSaveAndFind() {
        TestPersistedEntity entity = new TestPersistedEntity();
        entity.setName("TestEntity");

        entity = persistenceService.save(entity);
        assertNotNull(entity.getId());

        TestPersistedEntity found = persistenceService.findObjectById(TestPersistedEntity.class,
                entity.getId());
        assertNotNull(found);
        assertEquals("TestEntity", found.getName());

        found.setName("UpdatedName");
        found = persistenceService.save(found);

        TestPersistedEntity updated = persistenceService.findObjectById(TestPersistedEntity.class,
                found.getId());
        assertEquals("UpdatedName", updated.getName());
    }
}
