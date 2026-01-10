package org.tus.common.domain.persistence;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import org.hibernate.annotations.GenericGenerator;

@MappedSuperclass
public class SimplePersistedObject {
    /**
     * Id column identifier
     */
    public static final String UNIQUE_ID_COLUMN_NAME = "UUID";

    public static final String COLUMN_TYPE_NSTRING = "string";

    public static final String COLUMN_TYPE_TEXT = "text";

    public static final String COLUMN_TYPE_BINARY = "binary";

    /**
     * The unique identifier
     */
    private String id;

    /**
     * Create a new instance of SimplePersistedObject
     */
    public SimplePersistedObject() {
    }

    /**
     * Creates a new instance of SimplePersistedObject as a copy of an existing
     * SimplePersistedObject
     *
     * @param rhs the SimplePersistedObject to copy
     */
    public SimplePersistedObject(SimplePersistedObject rhs) {
        this.id = rhs.id;
    }

    /**
     * Gets the unique identifier of this object
     *
     * @return the unique identifier of this object, or {@code null} if it has not yet been
     * persisted to a data store
     */
    @GeneratedValue(generator = "system-uuid")
    @GenericGenerator(name = "system-uuid", strategy = "uuid")
    @Column(name = UNIQUE_ID_COLUMN_NAME, unique = true, nullable = false)
    @Id
    public String getId() {
        return this.id;
    }

    /**
     * Sets the unique identifier of this object
     *
     * @param value the unique identifier
     */
    public void setId(String value) {
        this.id = value;
    }

    /**
     * Determine whether the unique identifier has been set
     *
     * @return {@code true} if the object has valid unique identifier;
     * {@code false} otherwise.
     */
    @Transient
    @JsonIgnore
    protected String getHashableId() {
        return (this.id != null ? this.id : " ");
    }

    /**
     * Performs object-specific actions when saving
     */
    protected void onSave() {
    }

    /**
     * Initialize members through Hibernate to avoid LazyEvaluationException errors.
     */
    public void initialize() {
    }
}
