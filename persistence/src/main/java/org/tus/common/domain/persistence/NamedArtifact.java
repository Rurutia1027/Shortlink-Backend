package org.tus.common.domain.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.SneakyThrows;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.tus.shortlink.base.common.convention.exception.ServiceException;
import org.tus.shortlink.base.tookit.StringUtils;

/**
 * Abstract class holding values common to classes that will be persisted into a data store.
 */

@MappedSuperclass
public class NamedArtifact extends PersistedObject {
    /**
     * Name column identifier
     */
    public static final String NameColumn = "NAME";

    /**
     * Name attribute name
     */
    public static final String NameAttribute = "name";

    /**
     * Display Name column identifier
     */
    public static final String DisplayNameColumn = "DISPLAY_NAME";
    ;

    /**
     * Display Name attribute name
     */
    public static final String DisplayNameAttribute = "displayName";

    /**
     * The name
     */
    private String name;

    /**
     * The display name
     */
    private String displayName;

    /**
     * Creates a new instance of NamedArtifact
     */
    public NamedArtifact() {
        super();
    }


    /**
     * Creates a new instance of NamedArtifact as a copy of the given NamedArtifact
     *
     * @param rhs the NamedArtifact to copy
     */
    public NamedArtifact(NamedArtifact rhs) {
        super(rhs);
        this.name = rhs.name;
        this.displayName = rhs.displayName;
    }

    /**
     * Gets the name, a secondary human-readable identifier that is unique among active objects.
     *
     * @return the name
     */
    @JsonProperty(NameAttribute)
    @Column(name = NamedArtifact.NameColumn, length = 1024)
    @JdbcTypeCode(SqlTypes.NVARCHAR)
    @Size(max = 1024)
    public String getName() {
        return name;
    }

    /**
     * Sets the name
     *
     * @param name the name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Determines whether the name has been set to a valid value.
     *
     * @return {@code true} if the name is not null; {@code false} otherwise
     */
    public boolean hasName() {
        return StringUtils.hasText(this.name);
    }

    /**
     * Gets the display name. The display name is used to show the object's name as the user would expect to read it.
     *
     * @return the display name
     */
    @JsonProperty(DisplayNameAttribute)
    @Column(name = NamedArtifact.DisplayNameColumn, length = 1024)
    @JdbcTypeCode(SqlTypes.NVARCHAR)
    @NotEmpty
    @Size(max = 1024)
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Sets the display name
     *
     * @param displayName the display name
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Sets the display name attribute and nothing else. Used for Hibernate only.
     * Creating an object from these sources should not affect the object name or
     * modification time.
     *
     * @param displayName the display name
     */
    @JsonProperty(DisplayNameAttribute)
    @Column(name = NamedArtifact.DisplayNameColumn, length = 1024)
    public void setDisplayNameOnly(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Determines whether the display name is set to a valid value
     *
     * @return {@code true} if the display name is set to a valid value, {@code false} otherwise
     */
    public boolean hasDisplayName() {
        return (StringUtils.hasText(getDisplayName()));
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NamedArtifact)) return false;

        NamedArtifact that = (NamedArtifact) o;

        return (this.displayName != null ? displayName.equals(that.displayName) : that.displayName == null);
    }

    @Override
    public int hashCode() {
        return displayName != null ? displayName.hashCode() : 0;
    }

    /**
     * Get entity tag for the NamedArtifact, used to cache looking up the entire user if nothing has changed
     * Includes the superclass entity tag.
     *
     * @return entity tag
     */
    @Transient
    @Override
    public String getEntityTag() {
        String entityBase = StringUtils.join(" ", hasDisplayName() ? displayName : "",
                hasName() ?
                        name : " ", super.getEntityTag());
        return StringUtils.digest(entityBase);
    }

    /**
     * Check if {@link NamedArtifact} object contain valid name or displayName.
     *
     * <ul>
     * <li>if name is empty and displayName is empty, Return false directly;</li>
     * <li>if name is empty and displayName is not empty. Set name as displayName and check if both name and displayName
     * is valid</li>
     * <li>if name is not empty and displayName is empty. Set name as displayName and check if both name and displayName
     * is valid</li>
     * <li>if name is not empty and displayName is not empty. Check if both name and displayName is valid</li>
     * </ul>
     */
    @SneakyThrows
    public void checkAndUpdateName() {
        boolean hasName = StringUtils.hasText(this.name), hasDisplayName = StringUtils.hasText(this.displayName);
        if (hasName) {
            if (!hasDisplayName) {
                setDisplayNameOnly(name);
            }
        } else {
            if (hasDisplayName) {
                setName(displayName);
            } else {
                throw new ServiceException("Name and display name cannot be empty simultaneously");
            }
        }
        if (!StringUtils.isNameValid(name)) {
            throw new ServiceException("Invalid name= " + name);
        }
    }
}