package org.tus.common.domain.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.tus.common.domain.persistence.NamedArtifact;

@Entity
@Table(name = "test_name_artifact_entity")
public class TestNamedArtifactEntity extends NamedArtifact {
    // optional additional fields
}