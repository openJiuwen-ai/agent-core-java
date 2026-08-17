/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.monitor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import examples.gitcode_feature_evolver.FeatureEvolvingConfig;
import examples.gitcode_feature_evolver.job.FeatureJobStore;
import examples.gitcode_feature_evolver.polling.FeaturePollingCoordinator;
import examples.gitcode_feature_evolver.polling.FeaturePollingStatusSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Serves the loopback-only, read-only monitor snapshot API. */
public final class FeatureMonitorApiHandler implements HttpHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureMonitorApiHandler.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final FeatureEvolvingConfig config;
    private final FeatureJobStore store;
    private final FeaturePollingCoordinator polling;

    /**
     * Create a monitor API handler over the service-owned state.
     *
     * @param config current service configuration
     * @param store repository-scoped durable store
     * @param polling polling coordinator
     */
    public FeatureMonitorApiHandler(FeatureEvolvingConfig config, FeatureJobStore store,
                                    FeaturePollingCoordinator polling) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.polling = Objects.requireNonNull(polling, "polling must not be null");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"/api/monitor".equals(exchange.getRequestURI().getPath())) {
            write(exchange, 404, "application/json; charset=utf-8",
                    "{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            write(exchange, 405, "application/json; charset=utf-8",
                    "{\"error\":\"method_not_allowed\"}".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try {
            FeaturePollingStatusSnapshot status = config.triggerMode().usesPolling()
                    ? polling.status() : null;
            byte[] body = mapper.writeValueAsBytes(
                    FeatureMonitorSnapshot.capture(config, store, status));
            write(exchange, 200, "application/json; charset=utf-8", body);
        } catch (RuntimeException ex) {
            LOGGER.error("Unable to build Feature Evolver monitor snapshot", ex);
            write(exchange, 500, "application/json; charset=utf-8",
                    "{\"error\":\"snapshot_unavailable\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    static void write(HttpExchange exchange, int status, String contentType,
                      byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Security-Policy",
                "default-src 'self'; style-src 'self'; script-src 'self'; "
                        + "connect-src 'self'; img-src 'none'; object-src 'none'; "
                        + "base-uri 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
