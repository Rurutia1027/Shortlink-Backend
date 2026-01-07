/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ucd.shortlink.project.prometheus.service;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.exporter.HTTPServer;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.ucd.shortlink.project.configs.PrometheusMetricTestConfig;
import org.ucd.shortlink.project.prometheus.dto.PrometheusQueryReqDTO;
import org.ucd.shortlink.project.prometheus.dto.PrometheusRespDTO;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;


@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PrometheusMetricTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PrometheusCustomMetricIntegrationTest {
    private static final DockerImageName PROMETHEUS_IMAGE = DockerImageName.parse("prom" +
            "/prometheus:latest");

    private static Network network;

    @Container
    private static GenericContainer<?> prometheusContainer;

    @Autowired
    private PrometheusService prometheusService;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("prometheus.url", () ->
                String.format("http://%s:%d",
                        prometheusContainer.getHost(),
                        prometheusContainer.getMappedPort(9090))
        );
    }

    private static HTTPServer metricsServer;

    // -- define two custom prometheus metric items
    private static final Counter requests = Counter.build()
            .name("shortlink_project_requests_total")
            .help("Total requests for shortlink project")
            .labelNames("job")
            .register();

    private static final Histogram requestLatency = Histogram.build()
            .name("shortlink_request_latency_seconds_test")
            .help("Request latency in seconds")
            .labelNames("job")
            .register();

    static {
        network = Network.newNetwork();
        Path prometheusConfig = Paths.get("src/test/resources/prometheus-metric-test.yml").toAbsolutePath();
        prometheusContainer = new GenericContainer<>(PROMETHEUS_IMAGE)
                .withNetwork(network)
                .withFileSystemBind(prometheusConfig.toString(), "/etc/prometheus/prometheus.yml")
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/-/ready").forStatusCode(200));
        prometheusContainer.start();

        try {
            metricsServer = new HTTPServer("0.0.0.0", 9100, true);
        } catch (Exception ex) {
        }

        // -- here we modify the two prometheus metric items
        for (int i = 0; i < 10; i++) {
            requests.labels("shortlink-project").inc();
            try {
                Histogram.Timer timer =
                        requestLatency.labels("shortlink-project").startTimer();
                Thread.sleep(100 + (int) (Math.random() * 200));
                timer.observeDuration();
            } catch (Exception e) {
            }
        }
    }

    @Test
    public void initOk() {
        Assertions.assertNotNull(prometheusContainer);
        Assertions.assertNotNull(prometheusService);
        Assertions.assertNotNull(prometheusService.getPrometheusClient());
        Assertions.assertNotNull(metricsServer);
    }


    // @Test
    @SneakyThrows
    void testMetricExposedToPrometheus() {
        Instant now = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC);
        PrometheusQueryReqDTO req = PrometheusQueryReqDTO.builder()
                .metricName("shortlink_project_requests_total_test")
                .startDate(formatter.format(now.minusSeconds(60)))
                .endDate(formatter.format(now.plusSeconds(60)))
                .step("5s")
                .build();
        PrometheusRespDTO resp = null;
        int retries = 10;
        while (retries-- > 0) {
            resp = prometheusService.queryPrometheusMetric(req);
            if (resp != null && "success".equals(resp.getStatus()) &&
                    resp.getData() != null && !resp.getData().getResult().isEmpty()) {
                break;
            }
            Thread.sleep(5000);
        }

        Assertions.assertNotNull(resp);
        Assertions.assertTrue(!resp.getData().getResult().isEmpty());
    }
}
