/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.gitcode_feature_evolver.monitor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Serves self-contained monitor assets from the application classpath. */
public final class FeatureMonitorAssetsHandler implements HttpHandler {
    private static final String RESOURCE_ROOT = "examples/gitcode_feature_evolver/monitor/";
    private final Map<String, Asset> assets;

    /** Load the fixed, trusted monitor assets during service construction. */
    public FeatureMonitorAssetsHandler() {
        this.assets = Map.of(
                "/monitor", asset("index.html", "text/html; charset=utf-8"),
                "/monitor/", asset("index.html", "text/html; charset=utf-8"),
                "/monitor/app.css", asset("app.css", "text/css; charset=utf-8"),
                "/monitor/app.js", asset("app.js", "text/javascript; charset=utf-8"));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            FeatureMonitorApiHandler.write(exchange, 405, "text/plain; charset=utf-8",
                    "Method not allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        Asset selected = assets.get(exchange.getRequestURI().getPath());
        if (selected == null) {
            FeatureMonitorApiHandler.write(exchange, 404, "text/plain; charset=utf-8",
                    "Not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        FeatureMonitorApiHandler.write(exchange, 200, selected.contentType(), selected.body());
    }

    private static Asset asset(String name, String contentType) {
        String resource = RESOURCE_ROOT + name;
        try (InputStream input = FeatureMonitorAssetsHandler.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing Feature monitor resource: " + name);
            }
            return new Asset(contentType, input.readAllBytes());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Feature monitor resource: " + name, ex);
        }
    }

    private record Asset(String contentType, byte[] body) {
        private Asset {
            body = body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }
}
