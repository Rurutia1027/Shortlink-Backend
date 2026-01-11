package org.tus.common.domain.persistence.integration.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.tus.common.domain.persistence.UniqueNamedArtifact;

@Entity
@Table(name = "test_persisted_entity")
public class TestPersistedEntity extends UniqueNamedArtifact {
}
