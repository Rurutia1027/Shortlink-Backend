package org.tus.shortlink.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tus.common.domain.persistence.UniqueNamedArtifact;

/**
 * Group entity mapped to database via JPA/Hibernate
 * Represents a short link group for organizing short links
 */
@Entity
@Table(name = "t_group")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group extends UniqueNamedArtifact {

    /**
     * Group identifier
     */
    @Column(nullable = false, length = 32)
    private String gid;

    /**
     * Username who created the group
     */
    @Column(nullable = false, length = 256)
    private String username;

    /**
     * Group sort order
     */
    @Column(nullable = false)
    private Integer sortOrder;
}
