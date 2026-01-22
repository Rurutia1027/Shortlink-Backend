package org.tus.shortlink.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.tus.common.domain.persistence.PersistedObject;

/**
 * GroupUnique entity mapped to database via JPA/Hibernate
 * Represents a unique constraint table for group identifiers
 */
@Entity
@Table(name = "t_group_unique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupUnique extends PersistedObject {

    /**
     * Group identifier (unique)
     */
    @Column(nullable = false, length = 32, unique = true)
    private String gid;
}
