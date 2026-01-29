package org.tus.shortlink.svc.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tus.common.domain.persistence.QueryService;
import org.tus.shortlink.base.dto.biz.ShortLinkStatsRecordDTO;
import org.tus.shortlink.svc.entity.ShortLink;
import org.tus.shortlink.svc.service.ShortLinkStatsEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ShortLinkServiceImpl.restoreUrl (event build + publish + redirect).
 */
@ExtendWith(MockitoExtension.class)
class ShortLinkServiceImplRestoreUrlTest {

    private static final String DEFAULT_DOMAIN = "shortlink.tus";
    private static final String SHORT_URI = "abc123";
    private static final String FULL_SHORT_URL = DEFAULT_DOMAIN + "/" + SHORT_URI;
    private static final String ORIGIN_URL = "https://example.com/target";
    private static final String GID = "group1";

    @Mock
    private QueryService queryService;

    @Mock
    private ShortLinkStatsEventPublisher shortLinkStatsEventPublisher;

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private HttpServletResponse httpResponse;

    @Captor
    private ArgumentCaptor<ShortLinkStatsRecordDTO> eventCaptor;

    private ShortLinkServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShortLinkServiceImpl(queryService, shortLinkStatsEventPublisher);
        ReflectionTestUtils.setField(service, "createShortLinkDefaultDomain", DEFAULT_DOMAIN);
    }

    @Test
    void restoreUrl_publishesEventAndRedirects() throws Exception {
        ShortLink shortLink = ShortLink.builder()
                .fullShortUrl(FULL_SHORT_URL)
                .originUrl(ORIGIN_URL)
                .gid(GID)
                .delTime(0L)
                .build();
        when(httpRequest.getRemoteAddr()).thenReturn("192.168.1.1");
        when(httpRequest.getHeader("Referer")).thenReturn("https://google.com");
        when(httpRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(queryService.query(any(), anyMap())).thenReturn(List.of(shortLink));

        service.restoreUrl(SHORT_URI, httpRequest, httpResponse);

        verify(shortLinkStatsEventPublisher).publish(eventCaptor.capture());
        ShortLinkStatsRecordDTO event = eventCaptor.getValue();
        assertThat(event.getGid()).isEqualTo(GID);
        assertThat(event.getFullShortUrl()).isEqualTo(FULL_SHORT_URL);
        assertThat(event.getRemoteAddr()).isEqualTo("192.168.1.1");
        assertThat(event.getReferrer()).isEqualTo("https://google.com");
        assertThat(event.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(event.getOs()).isEqualTo("Unknown");
        assertThat(event.getKeys()).isNotNull();

        verify(httpResponse).sendRedirect(ORIGIN_URL);
    }

    @Test
    void restoreUrl_usesXForwardedForWhenPresent() throws Exception {
        ShortLink shortLink = ShortLink.builder()
                .fullShortUrl(FULL_SHORT_URL)
                .originUrl(ORIGIN_URL)
                .gid(GID)
                .delTime(0L)
                .build();
        when(httpRequest.getHeader("Referer")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn(null);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 70.41.3.18");
        when(queryService.query(any(), anyMap())).thenReturn(List.of(shortLink));

        service.restoreUrl(SHORT_URI, httpRequest, httpResponse);

        verify(shortLinkStatsEventPublisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getRemoteAddr()).isEqualTo("203.0.113.50");
        verify(httpResponse).sendRedirect(ORIGIN_URL);
    }

    @Test
    void restoreUrl_addsHttpsWhenOriginUrlHasNoScheme() throws Exception {
        ShortLink shortLink = ShortLink.builder()
                .fullShortUrl(FULL_SHORT_URL)
                .originUrl("example.com/path")
                .gid(GID)
                .delTime(0L)
                .build();
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("Referer")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn(null);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(queryService.query(any(), anyMap())).thenReturn(List.of(shortLink));

        service.restoreUrl(SHORT_URI, httpRequest, httpResponse);

        verify(httpResponse).sendRedirect("https://example.com/path");
        verify(shortLinkStatsEventPublisher).publish(any());
    }

    @Test
    void restoreUrl_sends404WhenLinkNotFound() throws Exception {
        when(queryService.query(any(), anyMap())).thenReturn(Collections.emptyList());

        service.restoreUrl(SHORT_URI, httpRequest, httpResponse);

        verify(shortLinkStatsEventPublisher, never()).publish(any());
        verify(httpResponse).sendError(eq(HttpServletResponse.SC_NOT_FOUND), eq("Short link not found"));
    }

    @Test
    void restoreUrl_sends500WhenOriginUrlMissing() throws Exception {
        ShortLink shortLink = ShortLink.builder()
                .fullShortUrl(FULL_SHORT_URL)
                .originUrl(null)
                .gid(GID)
                .delTime(0L)
                .build();
        when(httpRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpRequest.getHeader("Referer")).thenReturn(null);
        when(httpRequest.getHeader("User-Agent")).thenReturn(null);
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(queryService.query(any(), anyMap())).thenReturn(List.of(shortLink));

        service.restoreUrl(SHORT_URI, httpRequest, httpResponse);

        verify(shortLinkStatsEventPublisher).publish(any());
        verify(httpResponse).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), eq("Original URL missing"));
    }
}
