package org.tus.common.domain.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;

/**
 * Object mapper that handles hibernate lazy initialization.
 */
public class HibernateAwareObjectMapper extends ObjectMapper {

    public HibernateAwareObjectMapper() {
        super();
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Hibernate6Module hibernate6Module = new Hibernate6Module();
        hibernate6Module.disable(Hibernate6Module.Feature.USE_TRANSIENT_ANNOTATION);

        registerModule(hibernate6Module);
    }
}
