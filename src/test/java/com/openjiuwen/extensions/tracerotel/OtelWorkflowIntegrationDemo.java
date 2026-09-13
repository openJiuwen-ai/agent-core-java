/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowChunk;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.session.tracer.TracerHandlerRegistry;

import io.opentelemetry.api.trace.Tracer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OTel + Workflow integration demo.
 *
 * <p>Run with:
 * <pre>{@code
 * mvn test-compile -q -DskipTests
 * $cp = Get-Content target/cp.txt -Raw
 * java -cp "target/classes;target/test-classes;$cp" com.openjiuwen.extensions.tracerotel.OtelWorkflowIntegrationDemo
 * }</pre>
 */
public final class OtelWorkflowIntegrationDemo {

    private static final Logger LOG = LoggerFactory.getLogger(OtelWorkflowIntegrationDemo.class);

    private OtelWorkflowIntegrationDemo() {
    }

    /**
     * Entry point.
     *
     * @param args command-line arguments (unused)
     * @throws InterruptedException if the flush sleep is interrupted
     * @throws IOException if Jaeger connectivity check fails
     */
    public static void main(String[] args) throws InterruptedException, IOException {
        LOG.info("=== OTel + Workflow integration demo started ===");

        // 1. Configure and initialize OTel Tracer (console export for debugging)
        OtelTracerConfig config = OtelTracerConfig.builder()
                .exporterType("console")
                .serviceName("my_agent_service")
                .isRedactionEnabled(false)
                .scheduleDelayMillis(500)
                .build();
        Tracer otelTracer = OtelTracerSetup.initOtelTracer(config);
        LOG.info("[setup] OTel Tracer initialized (console export)");

        // Also verify OTLP/HTTP export to Jaeger if it is running
        boolean jaegerAvailable = isJaegerAvailable();
        OtelTracerConfig otlpConfig = null;
        Tracer otlpTracer = null;
        if (jaegerAvailable) {
            otlpConfig = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4318")
                    .protocol("http")
                    .serviceName("my_agent_service")
                    .isRedactionEnabled(false)
                    .scheduleDelayMillis(500)
                    .build();
            otlpTracer = OtelTracerSetup.initOtelTracer(otlpConfig);
            LOG.info("[setup] OTLP Tracer initialized (OTLP/HTTP -> http://localhost:4318)");
        }

        // 2. Register Agent / Workflow handlers (custom names, must not collide with built-in names)
        TracerHandlerRegistry.registerHandler("otel_agent", new OtelAgentHandler(otelTracer, config));
        TracerHandlerRegistry.registerHandler("otel_workflow", new OtelWorkflowHandler(otelTracer, config));
        LOG.info("[setup] Handlers registered: otel_agent, otel_workflow (console)");

        if (jaegerAvailable) {
            TracerHandlerRegistry.registerHandler("otel_agent_otlp", new OtelAgentHandler(otlpTracer, otlpConfig));
            TracerHandlerRegistry.registerHandler("otel_workflow_otlp", new OtelWorkflowHandler(otlpTracer, otlpConfig));
            LOG.info("[setup] Handlers registered: otel_agent_otlp, otel_workflow_otlp (OTLP/HTTP)");
        }

        try {
            // 3. Build and run a simple Workflow; events during execution are exported as OTel spans
            Workflow flow = new Workflow();
            flow.setStartComp("start", new Start(),
                    Map.of("cmd", "${cmd}"), null);
            flow.setEndComp("end", new End(),
                    Map.of("result", "${start.cmd}"), null);
            flow.addConnection("start", "end");

            String sessionId = UUID.randomUUID().toString().replace("-", "");

            // Use stream() with TRACE mode to create the Tracer (consistent with framework semantics).
            // invoke() passes [OUTPUT] without TRACE, so no Tracer is created.
            WorkflowSessionApi session = WorkflowSessions.createWorkflowSession(sessionId);
            Iterator<WorkflowChunk> chunkIterator = flow.stream(
                    Map.of("cmd", "hello"), session, null,
                    List.of(StreamMode.TRACE, StreamMode.OUTPUT));

            // Consume stream and collect output chunks
            List<Object> outputChunks = new ArrayList<>();
            int traceChunkCount = 0;
            while (chunkIterator.hasNext()) {
                WorkflowChunk chunk = chunkIterator.next();
                if (chunk instanceof OutputSchema outputSchema) {
                    outputChunks.add(outputSchema.getPayload());
                } else {
                    traceChunkCount++;
                }
            }

            LOG.info("[result] Workflow completed");
            LOG.info("  session_id     = {}", sessionId);
            LOG.info("  output chunks  = {}", outputChunks.size());
            LOG.info("  trace chunks   = {}", traceChunkCount);
            LOG.info("  result         = {}", outputChunks);

            // Wait for BatchSpanProcessor to flush
            LOG.info("[flush] Waiting for span flush...");
            Thread.sleep(2000L);
        } finally {
            // Clean up registered handlers to avoid affecting subsequent tests
            TracerHandlerRegistry.unregisterHandler("otel_agent");
            TracerHandlerRegistry.unregisterHandler("otel_workflow");
            if (jaegerAvailable) {
                TracerHandlerRegistry.unregisterHandler("otel_agent_otlp");
                TracerHandlerRegistry.unregisterHandler("otel_workflow_otlp");
            }
        }

        LOG.info("=== Integration demo finished ===");
        if (jaegerAvailable) {
            LOG.info("View traces in Jaeger UI: http://localhost:16686 (service: my_agent_service)");
        }
    }

    /**
     * Check whether Jaeger is running on localhost:16686.
     *
     * @return true if Jaeger API responds with HTTP 200
     */
    private static boolean isJaegerAvailable() {
        try {
            HttpURLConnection conn = (HttpURLConnection)
                    new URL("http://localhost:16686/api/services").openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (IOException e) {
            LOG.debug("Jaeger not available: {}", e.getMessage());
            return false;
        }
    }
}
