package org.tus.common.domain.model;

import jakarta.persistence.MappedSuperclass;

@MappedSuperclass
public class UniqueNamedArtifact extends NamedArtifact {
    /**
     * Create a new instance of UniqueNamedArtifact
     */
    public UniqueNamedArtifact() {
        super();
    }

    /**
     * Creates a new instance of UniqueNamedArtifact as a copy of the given
     * UniqueNamedArtifact
     *
     * @param rhs the UniqueNamedArtifact to copy
     */
    public UniqueNamedArtifact(UniqueNamedArtifact rhs) {
        super(rhs);
    }

    /**
     * Determine whether the given group name or unique identifier matches this group's name
     * or unique identifier.
     *
     * @param id     the group name or unique identifier to test
     * @param {@code true} if {@code id} matches this group's name or unique identifier;
     *               {@code false} otherwise
     */
    public boolean matches(String id) {
        return (id.equals(getId()) || id.equals(getName()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        UniqueNamedArtifact that = (UniqueNamedArtifact) o;

        return getId() != null ? getId().equals(that.getId()) : that.getId() == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (getId() != null ? getId().hashCode() : 0);
        return result;
    }
}

