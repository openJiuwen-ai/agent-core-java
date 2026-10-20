/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

/**
 * Immutable facts about one model call failure, used as the single input of
 * {@link ModelErrorClassifier}. Facts come from either the HTTP layer (status
 * code, sanitized error body, parsed error object, Retry-After headers) or the
 * exception layer (raw throwable, thread interrupt state), plus call shape
 * metadata (streaming or not, whether the first effective segment arrived).
 * Boxed fields may be null when the corresponding fact is unavailable.
 *
 * @since 2026-10-19
 */
public final class ModelErrorFacts {
    private final Integer httpStatus;
    private final String errorBody;
    private final String errorCode;
    private final String errorType;
    private final String errorMessage;
    private final Throwable throwable;
    private final boolean isStream;
    private final Boolean isFirstSegmentReceived;
    private final Double retryAfterSeconds;

    private ModelErrorFacts(Builder builder) {
        this.httpStatus = builder.httpStatus;
        this.errorBody = builder.errorBody == null ? "" : builder.errorBody;
        this.errorCode = builder.errorCode;
        this.errorType = builder.errorType;
        this.errorMessage = builder.errorMessage;
        this.throwable = builder.throwable;
        this.isStream = builder.isStream;
        this.isFirstSegmentReceived = builder.isFirstSegmentReceived;
        this.retryAfterSeconds = builder.retryAfterSeconds;
    }

    /**
     * Builds facts from a bare throwable (no HTTP context available).
     *
     * @param throwable failure thrown by the call, must not be null
     * @param isStream true for streaming calls
     * @return facts carrying only the throwable and the call shape
     */
    public static ModelErrorFacts ofThrowable(Throwable throwable, boolean isStream) {
        return new Builder().throwable(throwable).stream(isStream).build();
    }

    /**
     * HTTP status code, or null when the failure happened before any response.
     *
     * @return status code or null
     */
    public Integer httpStatus() {
        return httpStatus;
    }

    /**
     * Sanitized error body, never null but possibly empty.
     *
     * @return sanitized error body text
     */
    public String errorBody() {
        return errorBody;
    }

    /**
     * Provider error code parsed from the error body, or null.
     *
     * @return provider error code or null
     */
    public String errorCode() {
        return errorCode;
    }

    /**
     * Provider error type parsed from the error body, or null.
     *
     * @return provider error type or null
     */
    public String errorType() {
        return errorType;
    }

    /**
     * Provider error message parsed from the error body, or null.
     *
     * @return provider error message or null
     */
    public String errorMessage() {
        return errorMessage;
    }

    /**
     * Original throwable, or null when the failure is response shaped.
     *
     * @return original throwable or null
     */
    public Throwable throwable() {
        return throwable;
    }

    /**
     * Whether the failed call was streaming.
     *
     * @return true for streaming calls
     */
    public boolean isStream() {
        return isStream;
    }

    /**
     * Whether the first effective data segment had arrived before the failure,
     * or null for non-streaming calls and pre-iteration failures.
     *
     * @return first effective segment flag or null
     */
    public Boolean firstSegmentReceived() {
        return isFirstSegmentReceived;
    }

    /**
     * Server suggested retry delay parsed from Retry-After headers, or null.
     *
     * @return suggested delay in seconds or null
     */
    public Double retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /**
     * Builder for {@link ModelErrorFacts}.
     *
     * @since 2026-10-19
     */
    public static final class Builder {
        private Integer httpStatus;
        private String errorBody;
        private String errorCode;
        private String errorType;
        private String errorMessage;
        private Throwable throwable;
        private boolean isStream;
        private Boolean isFirstSegmentReceived;
        private Double retryAfterSeconds;

        private Builder() {
        }

        /**
         * Starts a builder with all facts absent.
         *
         * @return empty builder
         */
        public static Builder create() {
            return new Builder();
        }

        /**
         * Sets the HTTP status code.
         *
         * @param status status code, null when no response arrived
         * @return this builder
         */
        public Builder httpStatus(Integer status) {
            this.httpStatus = status;
            return this;
        }

        /**
         * Sets the sanitized error body.
         *
         * @param body sanitized error body text
         * @return this builder
         */
        public Builder errorBody(String body) {
            this.errorBody = body;
            return this;
        }

        /**
         * Sets the provider error code parsed from the error body.
         *
         * @param code provider error code
         * @return this builder
         */
        public Builder errorCode(String code) {
            this.errorCode = code;
            return this;
        }

        /**
         * Sets the provider error type parsed from the error body.
         *
         * @param type provider error type
         * @return this builder
         */
        public Builder errorType(String type) {
            this.errorType = type;
            return this;
        }

        /**
         * Sets the provider error message parsed from the error body.
         *
         * @param message provider error message
         * @return this builder
         */
        public Builder errorMessage(String message) {
            this.errorMessage = message;
            return this;
        }

        /**
         * Sets the original throwable.
         *
         * @param error original throwable
         * @return this builder
         */
        public Builder throwable(Throwable error) {
            this.throwable = error;
            return this;
        }

        /**
         * Sets the call shape.
         *
         * @param isStream true for streaming calls
         * @return this builder
         */
        public Builder stream(boolean isStream) {
            this.isStream = isStream;
            return this;
        }

        /**
         * Sets the first effective segment flag.
         *
         * @param isReceived true when the first effective segment had arrived
         * @return this builder
         */
        public Builder firstSegmentReceived(Boolean isReceived) {
            this.isFirstSegmentReceived = isReceived;
            return this;
        }

        /**
         * Sets the server suggested retry delay.
         *
         * @param seconds delay in seconds parsed from Retry-After headers
         * @return this builder
         */
        public Builder retryAfterSeconds(Double seconds) {
            this.retryAfterSeconds = seconds;
            return this;
        }

        /**
         * Builds the immutable facts.
         *
         * @return immutable facts instance
         */
        public ModelErrorFacts build() {
            return new ModelErrorFacts(this);
        }
    }
}
