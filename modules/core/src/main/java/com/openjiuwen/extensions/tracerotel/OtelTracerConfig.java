/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.tracerotel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable configuration for the OTel tracer extension.
 *
 * Independent from {@code ObservabilityConfig} — {@code tracerotel} lives in
 * extensions and should not depend on {@code agent_teams}.</p>
 *
 * <p>Mirrors Python's {@code openjiuwen.extensions.tracer_otel.config.OtelTracerConfig}
 * ({@code @dataclass(frozen=True)}).</p>
 *
 * @since 0.1.7
 */
public final class OtelTracerConfig {
    private static final double SAMPLE_RATE_MIN = 0.0;
    private static final double SAMPLE_RATE_MAX = 1.0;

    /** OTel tracer name. Default {@code "openjiuwen.tracer.otel"}. */
    private final String tracerName;

    /** Exporter type: {@code "otlp"} or {@code "console"}. */
    private final String exporterType;

    /** OTLP export endpoint. Not required when {@code exporterType="console"}. */
    private final String exporterEndpoint;

    /** OTLP transport protocol: {@code "grpc"} or {@code "http"}. */
    private final String protocol;

    /** Custom request headers for the OTLP exporter (e.g. authentication). */
    private final Map<String, String> headers;

    /** {@code service.name} in the OTel Resource. Default {@code "openjiuwen"}. */
    private final String serviceName;

    /** {@code service.version} in the OTel Resource ({@code null} → "unknown"). */
    private final String serviceVersion;

    /** Sampling probability in [0.0, 1.0]. Default 1.0 (full). */
    private final double sampleRate;

    /** {@code BatchSpanProcessor} export interval in milliseconds. Default 5000. */
    private final int scheduleDelayMillis;

    /** {@code BatchSpanProcessor} export timeout in milliseconds. Default 30000. */
    private final int exportTimeoutMs;

    /** Maximum span count per {@code BatchSpanProcessor} batch. Default 512. */
    private final int maxExportBatchSize;

    /** Master redaction switch. When {@code true}, prompts/completions are SHA-256 hashed. */
    private final boolean isRedactionEnabled;

    /**
     * Fine-grained prompt redaction override.
     * {@code null} falls back to {@code redactionEnabled}; {@code true}/{@code false} forces the value.
     */
    private final Boolean shouldRedactPrompts;

    /**
     * Fine-grained completion redaction override.
     * {@code null} falls back to {@code redactionEnabled}; {@code true}/{@code false} forces the value.
     */
    private final Boolean shouldRedactCompletions;

    /** Truncation cap for string attribute values. Default 4096. */
    private final int maxAttrLength;

    private OtelTracerConfig(Builder b) {
        this.tracerName = b.tracerName;
        this.exporterType = b.exporterType;
        this.exporterEndpoint = b.exporterEndpoint;
        this.protocol = b.protocol;
        this.headers = b.headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(b.headers));
        this.serviceName = b.serviceName;
        this.serviceVersion = b.serviceVersion;
        this.sampleRate = b.sampleRate;
        this.scheduleDelayMillis = b.scheduleDelayMillis;
        this.exportTimeoutMs = b.exportTimeoutMs;
        this.maxExportBatchSize = b.maxExportBatchSize;
        this.isRedactionEnabled = b.isRedactionEnabled;
        this.shouldRedactPrompts = b.shouldRedactPrompts;
        this.shouldRedactCompletions = b.shouldRedactCompletions;
        this.maxAttrLength = b.maxAttrLength;
    }

    /**
     * Create a new builder.
     *
     * @return a fresh builder with defaults populated
     * @since 0.1.7
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getTracerName() {
        return tracerName;
    }

    public String getExporterType() {
        return exporterType;
    }

    public String getExporterEndpoint() {
        return exporterEndpoint;
    }

    public String getProtocol() {
        return protocol;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getServiceVersion() {
        return serviceVersion;
    }

    public double getSampleRate() {
        return sampleRate;
    }

    public int getScheduleDelayMillis() {
        return scheduleDelayMillis;
    }

    public int getExportTimeoutMs() {
        return exportTimeoutMs;
    }

    public int getMaxExportBatchSize() {
        return maxExportBatchSize;
    }

    public boolean isRedactionEnabled() {
        return isRedactionEnabled;
    }

    public Boolean getShouldRedactPrompts() {
        return shouldRedactPrompts;
    }

    public Boolean getShouldRedactCompletions() {
        return shouldRedactCompletions;
    }

    public int getMaxAttrLength() {
        return maxAttrLength;
    }

    /**
     * Builder for {@link OtelTracerConfig} with the same defaults as the Python dataclass.
     *
     * <p>{@link #build()} validates that {@code sampleRate} is within {@code [0.0, 1.0]},
     * raising {@link IllegalArgumentException} otherwise (mirrors Python's {@code ValueError}).</p>
     *
     * @since 0.1.7
     */
    public static final class Builder {
        private String tracerName = "openjiuwen.tracer.otel";
        private String exporterType = "otlp";
        private String exporterEndpoint = null;
        private String protocol = "grpc";
        private Map<String, String> headers = new LinkedHashMap<>();
        private String serviceName = "openjiuwen";
        private String serviceVersion = null;
        private double sampleRate = 1.0;
        private int scheduleDelayMillis = 5000;
        private int exportTimeoutMs = 30000;
        private int maxExportBatchSize = 512;
        private boolean isRedactionEnabled = true;
        private Boolean shouldRedactPrompts = null;
        private Boolean shouldRedactCompletions = null;
        private int maxAttrLength = 4096;

        private Builder() {
        }

        /**
         * Set the OTel tracer name.
         *
         * @param tracerName the OTel tracer name
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder tracerName(String tracerName) {
            this.tracerName = tracerName;
            return this;
        }

        /**
         * Set the exporter type.
         *
         * @param exporterType the exporter type: {@code "otlp"} or {@code "console"}
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder exporterType(String exporterType) {
            this.exporterType = exporterType;
            return this;
        }

        /**
         * Set the OTLP export endpoint.
         *
         * @param exporterEndpoint the OTLP export endpoint
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder exporterEndpoint(String exporterEndpoint) {
            this.exporterEndpoint = exporterEndpoint;
            return this;
        }

        /**
         * Set the OTLP transport protocol.
         *
         * @param protocol the OTLP transport protocol: {@code "grpc"} or {@code "http"}
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /**
         * Set the custom request headers for the OTLP exporter.
         *
         * @param headers the custom request headers for the OTLP exporter
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        /**
         * Set the {@code service.name} in the OTel Resource.
         *
         * @param serviceName the {@code service.name} in the OTel Resource
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        /**
         * Set the {@code service.version} in the OTel Resource.
         *
         * @param serviceVersion the {@code service.version} in the OTel Resource
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder serviceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
            return this;
        }

        /**
         * Set the sampling probability.
         *
         * @param sampleRate the sampling probability in [0.0, 1.0]
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder sampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        /**
         * Set the {@code BatchSpanProcessor} export interval.
         *
         * @param scheduleDelayMillis the {@code BatchSpanProcessor} export interval in milliseconds
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder scheduleDelayMillis(int scheduleDelayMillis) {
            this.scheduleDelayMillis = scheduleDelayMillis;
            return this;
        }

        /**
         * Set the {@code BatchSpanProcessor} export timeout.
         *
         * @param exportTimeoutMs the {@code BatchSpanProcessor} export timeout in milliseconds
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder exportTimeoutMs(int exportTimeoutMs) {
            this.exportTimeoutMs = exportTimeoutMs;
            return this;
        }

        /**
         * Set the maximum span count per {@code BatchSpanProcessor} batch.
         *
         * @param maxExportBatchSize the maximum span count per {@code BatchSpanProcessor} batch
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder maxExportBatchSize(int maxExportBatchSize) {
            this.maxExportBatchSize = maxExportBatchSize;
            return this;
        }

        /**
         * Set the master redaction switch.
         *
         * @param isRedactionEnabled the master redaction switch
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder isRedactionEnabled(boolean isRedactionEnabled) {
            this.isRedactionEnabled = isRedactionEnabled;
            return this;
        }

        /**
         * Set the fine-grained prompt redaction override.
         *
         * @param shouldRedactPrompts the fine-grained prompt redaction override
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder shouldRedactPrompts(Boolean shouldRedactPrompts) {
            this.shouldRedactPrompts = shouldRedactPrompts;
            return this;
        }

        /**
         * Set the fine-grained completion redaction override.
         *
         * @param shouldRedactCompletions the fine-grained completion redaction override
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder shouldRedactCompletions(Boolean shouldRedactCompletions) {
            this.shouldRedactCompletions = shouldRedactCompletions;
            return this;
        }

        /**
         * Set the truncation cap for string attribute values.
         *
         * @param maxAttrLength the truncation cap for string attribute values
         * @return this builder for chaining
         * @since 0.1.7
         */
        public Builder maxAttrLength(int maxAttrLength) {
            this.maxAttrLength = maxAttrLength;
            return this;
        }

        /**
         * Build the immutable config, validating {@code sampleRate}.
         *
         * @return a new {@link OtelTracerConfig}
         * @throws IllegalArgumentException if {@code sampleRate} is outside [0.0, 1.0]
         * @since 0.1.7
         */
        public OtelTracerConfig build() {
            if (sampleRate < SAMPLE_RATE_MIN || sampleRate > SAMPLE_RATE_MAX) {
                throw new IllegalArgumentException(
                        "sample_rate must be between " + SAMPLE_RATE_MIN + " and " + SAMPLE_RATE_MAX
                                + ", got " + sampleRate);
            }
            return new OtelTracerConfig(this);
        }
    }
}
