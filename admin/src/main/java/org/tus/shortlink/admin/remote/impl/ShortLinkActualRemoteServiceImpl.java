package org.tus.shortlink.admin.remote.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.tus.common.domain.model.PageResponse;
import org.tus.shortlink.admin.remote.ShortLinkActualRemoteService;
import org.tus.shortlink.base.common.convention.errorcode.BaseErrorCode;
import org.tus.shortlink.base.common.convention.result.Result;
import org.tus.shortlink.base.common.convention.result.Results;
import org.tus.shortlink.base.dto.req.RecycleBinRecoverReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinRemoveReqDTO;
import org.tus.shortlink.base.dto.req.RecycleBinSaveReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkBatchCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkCreateReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkPageReqDTO;
import org.tus.shortlink.base.dto.req.ShortLinkUpdateReqDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkBatchCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkCreateRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkGroupCountQueryRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkPageRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsAccessRecordRespDTO;
import org.tus.shortlink.base.dto.resp.ShortLinkStatsRespDTO;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Implementation of ShortLinkActualRemoteService using WebClient
 * Communicates with shortlink service via HTTP calls through Istio service mesh
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShortLinkActualRemoteServiceImpl implements ShortLinkActualRemoteService {

    private final WebClient shortLinkWebClient;

    /**
     * Generic method to handle HTTP calls with error handling and retry logic
     */
    private <T> Result<T> executeRequest(Mono<Result<T>> requestMono, String operation) {
        try {
            Result<T> result = requestMono
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(100))
                            .filter(throwable -> throwable instanceof WebClientResponseException
                                    && ((WebClientResponseException) throwable).getStatusCode().is5xxServerError()))
                    .block();

            if (result == null) {
                log.error("Remote service call [{}] returned null", operation);
                return new Result<T>()
                        .setCode(BaseErrorCode.REMOTE_ERROR.code())
                        .setMessage("Remote service returned null response");
            }

            return result;
        } catch (WebClientResponseException e) {
            log.error("Remote service call [{}] failed with status: {}, body: {}",
                    operation, e.getStatusCode(), e.getResponseBodyAsString(), e);

            // Try to parse error response
            try {
                String responseBody = e.getResponseBodyAsString();
                if (responseBody != null && !responseBody.isEmpty()) {
                    Result<T> errorResult = JSON.parseObject(responseBody, new TypeReference<Result<T>>() {
                    });
                    if (errorResult != null) {
                        return errorResult;
                    }
                }
            } catch (Exception parseException) {
                log.warn("Failed to parse error response", parseException);
            }

            return new Result<T>()
                    .setCode(String.valueOf(e.getStatusCode().value()))
                    .setMessage("Remote service error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during remote service call [{}]", operation, e);
            return new Result<T>()
                    .setCode(BaseErrorCode.REMOTE_ERROR.code())
                    .setMessage("Unexpected error: " + e.getMessage());
        }
    }

    @Override
    public Result<ShortLinkCreateRespDTO> createShortLink(ShortLinkCreateReqDTO requestParam) {
        log.debug("Calling shortlink service to create short link: {}", requestParam);

        Mono<Result<ShortLinkCreateRespDTO>> request = shortLinkWebClient.post()
                .uri("/api/shortlink/v1/links/create")
                .bodyValue(requestParam)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<ShortLinkCreateRespDTO>>() {
                });

        return executeRequest(request, "createShortLink");
    }

    @Override
    public Result<ShortLinkBatchCreateRespDTO> batchCreateShortLink(ShortLinkBatchCreateReqDTO requestParam) {
        log.debug("Calling shortlink service to batch create short links");

        Mono<Result<ShortLinkBatchCreateRespDTO>> request = shortLinkWebClient.post()
                .uri("/api/shortlink/v1/links/create/batch")
                .bodyValue(requestParam)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<ShortLinkBatchCreateRespDTO>>() {
                });

        return executeRequest(request, "batchCreateShortLink");
    }

    @Override
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        log.debug("Calling shortlink service to update short link: {}", requestParam);

        Mono<Result<Void>> request = shortLinkWebClient.put()
                .uri("/api/shortlink/v1/links/update")
                .bodyValue(requestParam)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Void>>() {
                });

        Result<Void> result = executeRequest(request, "updateShortLink");
        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to update short link: " + result.getMessage());
        }
    }

    @Override
    public Result<PageResponse<ShortLinkPageRespDTO>> pageShortLink(String gid, String orderTag, Integer current, Integer size) {
        log.debug("Calling shortlink service to page query short links: gid={}, orderTag={}, current={}, size={}",
                gid, orderTag, current, size);

        ShortLinkPageReqDTO requestParam = ShortLinkPageReqDTO.builder()
                .gid(gid)
                .orderTag(orderTag)
                .pageNo(current)
                .pageSize(size)
                .build();

        Mono<Result<PageResponse<ShortLinkPageRespDTO>>> request = shortLinkWebClient.post()
                .uri("/api/shortlink/v1/links/page")
                .bodyValue(requestParam)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<PageResponse<ShortLinkPageRespDTO>>>() {
                });

        return executeRequest(request, "pageShortLink");
    }

    @Override
    public Result<List<ShortLinkGroupCountQueryRespDTO>> listGroupShortLinkCount(List<String> requestParam) {
        log.debug("Calling shortlink service to list group short link counts: {}", requestParam);

        Mono<Result<List<ShortLinkGroupCountQueryRespDTO>>> request = shortLinkWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/shortlink/v1/links/gcount")
                        .queryParam("groupIds", requestParam)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<List<ShortLinkGroupCountQueryRespDTO>>>() {
                });

        return executeRequest(request, "listGroupShortLinkCount");
    }

    @Override
    public Result<String> getTitleByUrl(String url) {
        log.debug("Calling shortlink service to get title by URL: {}", url);

        // The shortlink service returns Result<UrlTitleRespDTO>, but we need Result<String>
        // Using Map to handle the response structure
        Mono<Result<Map<String, Object>>> request = shortLinkWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/shortlink/v1/title")
                        .queryParam("url", url)
                        .build())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {
                });

        Result<Map<String, Object>> result = executeRequest(request, "getTitleByUrl");

        if (result.isSuccess() && result.getData() != null) {
            Map<String, Object> data = result.getData();
            String title = data.get("title") != null ? data.get("title").toString() : null;
            return Results.success(title);
        } else {
            return new Result<String>()
                    .setCode(result.getCode())
                    .setMessage(result.getMessage());
        }
    }

    @Override
    public void saveRecycleBin(RecycleBinSaveReqDTO requestParam) {
        log.debug("Calling shortlink service to save to recycle bin: {}", requestParam);

        Mono<Result<Void>> request = shortLinkWebClient.post()
                .uri("/api/shortlink/v1/trash/save")
                .bodyValue(requestParam)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Void>>() {
                });

        Result<Void> result = executeRequest(request, "saveRecycleBin");
        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to save to recycle bin: " + result.getMessage());
        }
    }

    @Override
    public Result<PageResponse<ShortLinkPageRespDTO>> pageRecycleBinShortLink(List<String> gidList, Integer current, Integer size) {
        log.debug("Calling shortlink service to page query recycle bin: gidList={}, current={}, size={}",
                gidList, current, size);

        // Build query parameters for GET request
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/shortlink/v1/trash/list");
        if (gidList != null && !gidList.isEmpty()) {
            uriBuilder.queryParam("gidList", gidList);
        }
        if (current != null) {
            uriBuilder.queryParam("pageNum", current);
        }
        if (size != null) {
            uriBuilder.queryParam("pageSize", size);
        }

        Mono<Result<PageResponse<ShortLinkPageRespDTO>>> request = shortLinkWebClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<PageResponse<ShortLinkPageRespDTO>>>() {
                });

        return executeRequest(request, "pageRecycleBinShortLink");
    }

    @Override
    public void recoverRecycleBin(RecycleBinRecoverReqDTO requestParam) {
        log.debug("Calling shortlink service to recover from recycle bin: {}", requestParam);

        Mono<Result<Void>> request = shortLinkWebClient.put()
                .uri("/api/shortlink/v1/trash/recover")
                .bodyValue(requestParam)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Void>>() {
                });

        Result<Void> result = executeRequest(request, "recoverRecycleBin");
        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to recover from recycle bin: " + result.getMessage());
        }
    }

    @Override
    public void removeRecycleBin(RecycleBinRemoveReqDTO requestParam) {
        log.debug("Calling shortlink service to remove from recycle bin: {}", requestParam);

        Mono<Result<Void>> request = shortLinkWebClient.method(HttpMethod.DELETE)
                .uri("/api/shortlink/v1/trash/remove")
                .body(BodyInserters.fromValue(requestParam))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Void>>() {
                });

        Result<Void> result = executeRequest(request, "removeRecycleBin");
        if (!result.isSuccess()) {
            throw new RuntimeException("Failed to remove from recycle bin: " + result.getMessage());
        }
    }

    @Override
    public Result<ShortLinkStatsRespDTO> oneShortLinkStats(String fullShortUrl, String gid, Integer enableStatus,
                                                           String startDate, String endDate) {
        log.debug("Calling shortlink service to get single short link stats: fullShortUrl={}, gid={}, startDate={}, endDate={}",
                fullShortUrl, gid, startDate, endDate);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/shortlink/v1/stats");
        if (fullShortUrl != null) {
            uriBuilder.queryParam("fullShortUrl", fullShortUrl);
        }
        if (gid != null) {
            uriBuilder.queryParam("gid", gid);
        }
        if (enableStatus != null) {
            uriBuilder.queryParam("enableStatus", enableStatus);
        }
        if (startDate != null) {
            uriBuilder.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uriBuilder.queryParam("endDate", endDate);
        }

        Mono<Result<ShortLinkStatsRespDTO>> request = shortLinkWebClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<ShortLinkStatsRespDTO>>() {
                });

        return executeRequest(request, "oneShortLinkStats");
    }

    @Override
    public Result<ShortLinkStatsRespDTO> groupShortLinkStats(String gid, String startDate, String endDate) {
        log.debug("Calling shortlink service to get group short link stats: gid={}, startDate={}, endDate={}",
                gid, startDate, endDate);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/shortlink/v1/stats/group");
        if (gid != null) {
            uriBuilder.queryParam("gid", gid);
        }
        if (startDate != null) {
            uriBuilder.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uriBuilder.queryParam("endDate", endDate);
        }

        Mono<Result<ShortLinkStatsRespDTO>> request = shortLinkWebClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<ShortLinkStatsRespDTO>>() {
                });

        return executeRequest(request, "groupShortLinkStats");
    }

    @Override
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> shortLinkStatsAccessRecord(
            String fullShortUrl, String gid, String startDate, String endDate,
            Integer enableStatus, Integer current, Integer size) {
        log.debug("Calling shortlink service to get short link stats access record: fullShortUrl={}, gid={}",
                fullShortUrl, gid);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/shortlink/v1/stats/access");
        if (fullShortUrl != null) {
            uriBuilder.queryParam("fullShortUrl", fullShortUrl);
        }
        if (gid != null) {
            uriBuilder.queryParam("gid", gid);
        }
        if (startDate != null) {
            uriBuilder.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uriBuilder.queryParam("endDate", endDate);
        }
        if (enableStatus != null) {
            uriBuilder.queryParam("enableStatus", enableStatus);
        }
        if (current != null) {
            uriBuilder.queryParam("current", current);
        }
        if (size != null) {
            uriBuilder.queryParam("size", size);
        }

        Mono<Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>>> request = shortLinkWebClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>>>() {
                });

        return executeRequest(request, "shortLinkStatsAccessRecord");
    }

    @Override
    public Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>> groupShortLinkStatsAccessRecord(
            String gid, String startDate, String endDate, Integer current, Integer size) {
        log.debug("Calling shortlink service to get group short link stats access record: gid={}", gid);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath("/api/shortlink/v1/stats/group/access");
        if (gid != null) {
            uriBuilder.queryParam("gid", gid);
        }
        if (startDate != null) {
            uriBuilder.queryParam("startDate", startDate);
        }
        if (endDate != null) {
            uriBuilder.queryParam("endDate", endDate);
        }
        if (current != null) {
            uriBuilder.queryParam("current", current);
        }
        if (size != null) {
            uriBuilder.queryParam("size", size);
        }

        Mono<Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>>> request = shortLinkWebClient.get()
                .uri(uriBuilder.build().toUriString())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<PageResponse<ShortLinkStatsAccessRecordRespDTO>>>() {
                });

        return executeRequest(request, "groupShortLinkStatsAccessRecord");
    }
}
