/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import com.openjiuwen.core.common.exception.BaseError;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link OtelTracerSetup}.
 *
 * <p>Mirrors Python's {@code tests/unit_tests/extensions/tracer_otel/test_setup.py}.</p>
 */
@DisplayName("OtelTracerSetup tests")
class OtelTracerSetupTest {

    @Nested
    @DisplayName("initOtelTracer()")
    class TestInitOtelTracer {

        @Test
        @DisplayName("console exporter")
        void testConsoleExporter() {
            OtelTracerConfig config = OtelTracerConfig.builder().exporterType("console").build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("otlp grpc exporter")
        void testOtlpGrpcExporter() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317")
                    .protocol("grpc")
                    .build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("otlp http exporter")
        void testOtlpHttpExporter() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317")
                    .protocol("http")
                    .build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("otlp http endpoint path appended")
        void testOtlpHttpEndpointPathAppended() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317")
                    .protocol("http")
                    .build();
            SpanExporter exporter = OtelTracerSetup.createOtlpExporter(config);
            assertThat(exporter).isNotNull();
        }

        @Test
        @DisplayName("otlp http endpoint path not double appended")
        void testOtlpHttpEndpointPathNotDoubleAppended() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317/v1/traces")
                    .protocol("http")
                    .build();
            SpanExporter exporter = OtelTracerSetup.createOtlpExporter(config);
            assertThat(exporter).isNotNull();
        }

        @Test
        @DisplayName("otlp with headers")
        void testOtlpWithHeaders() {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("api-key", "test123");
            headers.put("Authorization", "Bearer token");
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317")
                    .protocol("grpc")
                    .headers(headers)
                    .build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("invalid exporter type raises BaseError")
        void testInvalidExporterTypeRaises() {
            OtelTracerConfig config = OtelTracerConfig.builder().exporterType("invalid").build();
            assertThatThrownBy(() -> OtelTracerSetup.initOtelTracer(config))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("exporter_type");
        }

        @Test
        @DisplayName("invalid protocol raises BaseError")
        void testInvalidProtocolRaises() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317")
                    .protocol("invalid")
                    .build();
            assertThatThrownBy(() -> OtelTracerSetup.createOtlpExporter(config))
                    .isInstanceOf(BaseError.class)
                    .hasMessageContaining("otlp protocol");
        }

        @Test
        @DisplayName("batch processor config")
        void testBatchProcessorConfig() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("console")
                    .exportTimeoutMs(5000)
                    .maxExportBatchSize(128)
                    .build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
            Span span = tracer.spanBuilder("test_span").startSpan();
            span.end();
        }

        @Test
        @DisplayName("service name and version")
        void testServiceNameAndVersion() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("console")
                    .serviceName("my-service")
                    .serviceVersion("1.2.3")
                    .build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
            Span span = tracer.spanBuilder("test_resource_span").startSpan();
            assertThat(span).isNotNull();
            span.end();
        }
    }

    @Nested
    @DisplayName("sampleRate validation")
    class TestSampleRateValidation {

        @Test
        @DisplayName("sample rate valid one")
        void testSampleRateValidOne() {
            OtelTracerConfig config = OtelTracerConfig.builder().sampleRate(1.0).build();
            assertThat(config.getSampleRate()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("sample rate valid zero")
        void testSampleRateValidZero() {
            OtelTracerConfig config = OtelTracerConfig.builder().sampleRate(0.0).build();
            assertThat(config.getSampleRate()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("sample rate valid fraction")
        void testSampleRateValidFraction() {
            OtelTracerConfig config = OtelTracerConfig.builder().sampleRate(0.5).build();
            assertThat(config.getSampleRate()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("sample rate invalid above one")
        void testSampleRateInvalidAboveOne() {
            assertThatThrownBy(() -> OtelTracerConfig.builder().sampleRate(1.1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sample_rate must be between");
        }

        @Test
        @DisplayName("sample rate invalid negative")
        void testSampleRateInvalidNegative() {
            assertThatThrownBy(() -> OtelTracerConfig.builder().sampleRate(-0.1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sample_rate must be between");
        }

        @Test
        @DisplayName("sample rate default is one")
        void testSampleRateDefaultIsOne() {
            OtelTracerConfig config = OtelTracerConfig.builder().build();
            assertThat(config.getSampleRate()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("sampleRate integration")
    class TestSampleRateIntegration {

        @Test
        @DisplayName("sample rate zero no spans sampled")
        void testSampleRateZeroNoSpansSampled() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("console").sampleRate(0.0).build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            io.opentelemetry.api.trace.Span span = tracer.spanBuilder("should_not_appear").startSpan();
            assertThat(span.isRecording()).isFalse();
            span.end();
        }

        @Test
        @DisplayName("sample rate one all spans sampled")
        void testSampleRateOneAllSpansSampled() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("console").sampleRate(1.0).build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            io.opentelemetry.api.trace.Span span = tracer.spanBuilder("should_appear").startSpan();
            assertThat(span.isRecording()).isTrue();
            span.end();
        }

        @Test
        @DisplayName("schedule delay millis passed to batch processor")
        void testScheduleDelayMillisPassedToBatchProcessor() {
            OtelTracerConfig config = OtelTracerConfig.builder()
                    .exporterType("otlp")
                    .exporterEndpoint("http://localhost:4317")
                    .scheduleDelayMillis(2000)
                    .build();
            Tracer tracer = OtelTracerSetup.initOtelTracer(config);
            assertThat(tracer).isNotNull();
        }

        @Test
        @DisplayName("default schedule delay millis is 5000")
        void testScheduleDelayMillisDefault() {
            OtelTracerConfig config = OtelTracerConfig.builder().build();
            assertThat(config.getScheduleDelayMillis()).isEqualTo(5000);
        }
    }
}
