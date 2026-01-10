package org.tus.common.domain.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.tus.common.domain.persistence.UniqueNamedArtifact;

@Entity
@Table(name = "test_sub_unique_name_artifact_entity")
public class TestSubUniqueNamedArtifactEntity extends UniqueNamedArtifact {
    // optional additional fields
}