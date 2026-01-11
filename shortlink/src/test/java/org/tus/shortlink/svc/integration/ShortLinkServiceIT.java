package org.tus.shortlink.svc.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.svc.config.ShortlinkPersistenceTestConfig;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringJUnitConfig(classes = {ShortlinkPersistenceTestConfig.class})
@Transactional
public class ShortLinkServiceIT {
//    @Autowired
//    private ShortLinkService shortLinkService;

    @Autowired
    private QueryService queryService;

    private ShortLinkCreateReqDTO createReqDTO;

    @BeforeEach
    void setup() {
        // create a common create request
        createReqDTO = ShortLinkCreateReqDTO.builder()
                .originUrl("https://www.example.com/page1")
                .domain("http://short.tus.org")
                .gid("group1")
                .build();
    }

    @Test
    void initOk() {
        assertNotNull(queryService);
    }
}
