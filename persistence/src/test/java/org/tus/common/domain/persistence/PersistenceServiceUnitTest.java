package org.tus.common.domain.persistence;

import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tus.common.domain.persistence.entity.TestSubNamedArtifactEntity;
import org.tus.common.domain.persistence.entity.TestSubUniqueNamedArtifactEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
                .addAnnotatedClass(TestSubUniqueNamedArtifactEntity.class)
                .addAnnotatedClass(TestSubNamedArtifactEntity.class)
                .buildMetadata()
                .buildSessionFactory();

        persistenceService = new PersistenceService(sessionFactory);
    }

    @Test
    void test_UniqueNamedEntity_SaveAndFind() {
        TestSubUniqueNamedArtifactEntity entity = new TestSubUniqueNamedArtifactEntity();
        entity.setName("TestEntity");

        entity = persistenceService.save(entity);
        assertNotNull(entity.getId());

        TestSubUniqueNamedArtifactEntity found = persistenceService.findObjectById(TestSubUniqueNamedArtifactEntity.class,
                entity.getId());
        assertNotNull(found);
        assertEquals("TestEntity", found.getName());

        found.setName("UpdatedName");
        found = persistenceService.save(found);

        TestSubUniqueNamedArtifactEntity updated = persistenceService.findObjectById(TestSubUniqueNamedArtifactEntity.class,
                found.getId());
        assertEquals("UpdatedName", updated.getName());
        TestSubUniqueNamedArtifactEntity delete = persistenceService.delete(updated);
        assertNotNull(delete);

        TestSubUniqueNamedArtifactEntity query =
                persistenceService.findObjectById(TestSubUniqueNamedArtifactEntity.class,
                        delete.getId());
        assertNull(query);
    }

    @Test
    void test_NamedEntity_SaveAndFind() {
        TestSubNamedArtifactEntity entity = new TestSubNamedArtifactEntity();
        entity.setName("TestSubNamedArtifactEntity");

        entity = persistenceService.save(entity);
        assertNotNull(entity.getId());

        TestSubNamedArtifactEntity found = persistenceService.findObjectById(TestSubNamedArtifactEntity.class,
                entity.getId());
        assertNotNull(found);
        assertEquals("TestSubNamedArtifactEntity", found.getName());

        found.setName("UpdatedName");
        found = persistenceService.save(found);

        TestSubNamedArtifactEntity updated = persistenceService.findObjectById(TestSubNamedArtifactEntity.class,
                found.getId());
        assertEquals("UpdatedName", updated.getName());
        TestSubNamedArtifactEntity delete = persistenceService.delete(updated);
        assertNotNull(delete);

        TestSubNamedArtifactEntity query =
                persistenceService.findObjectById(TestSubNamedArtifactEntity.class,
                        delete.getId());
        assertNull(query);
    }

}
