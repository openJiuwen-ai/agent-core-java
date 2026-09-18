/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.model_clients;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.foundation.llm.ModelRetryEvent;
import com.openjiuwen.core.foundation.llm.ModelRetryListener;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;

final class OpenAIRetryingHttpClient {

    private final int maxRetries;
    private final Sleeper sleeper;
    private final Clock clock;
    private final DoubleSupplier random;

    OpenAIRetryingHttpClient(int maxRetries) {
        this(maxRetries, delay -> TimeUnit.NANOSECONDS.sleep(delay.toNanos()), Clock.systemUTC(), Math::random);
    }

    OpenAIRetryingHttpClient(int maxRetries, Sleeper sleeper, Clock clock, DoubleSupplier random) {
        this.maxRetries = Math.max(0, maxRetries);
        this.sleeper = sleeper;
        this.clock = clock;
        this.random = random;
    }

    <T> HttpResponse<T> send(RetryableRequest<T> request) throws IOException, InterruptedException {
        return send(request, null);
    }

    <T> HttpResponse<T> send(RetryableRequest<T> request, ModelRetryListener listener)
            throws IOException, InterruptedException {
        int retryCount = 0;
        while (true) {
            HttpResponse<T> response;
            try {
                response = request.send(retryCount);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            } catch (IOException exception) {
                if (retryCount >= maxRetries) {
                    throw exception;
                }
                Duration delay = exponentialDelay(retryCount + 1);
                retryCount++;
                publishRetry(listener, new ModelRetryEvent(
                        retryCount,
                        maxRetries,
                        null,
                        exception.getClass().getName(),
                        delay,
                        "exponential_backoff"));
                sleep(delay);
                continue;
            }
            if (retryCount >= maxRetries || !shouldRetry(response)) {
                return response;
            }
            closeRetryBody(response.body());
            Optional<Duration> serverDelay = retryAfter(response);
            Duration delay = serverDelay.isPresent()
                    ? serverDelay.get()
                    : exponentialDelay(retryCount + 1);
            retryCount++;
            publishRetry(listener, new ModelRetryEvent(
                    retryCount,
                    maxRetries,
                    response.statusCode(),
                    null,
                    delay,
                    serverDelay.isPresent() ? "retry_after" : "exponential_backoff"));
            sleep(delay);
        }
    }

    private static void publishRetry(ModelRetryListener listener, ModelRetryEvent event) {
        Loggers.LLM.warning("OpenAI request will retry. {}", retryLogFields(event));
        if (listener == null) {
            return;
        }
        try {
            listener.onRetry(event);
        } catch (RuntimeException exception) {
            Loggers.LLM.warning("Model retry listener failed. {}", Map.of(
                    "retry_count", event.retryCount(),
                    "max_retries", event.maxRetries(),
                    "listener_exception_type", exception.getClass().getName()));
        }
    }

    private static Map<String, Object> retryLogFields(ModelRetryEvent event) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("retry_count", event.retryCount());
        fields.put("max_retries", event.maxRetries());
        if (event.statusCode() != null) {
            fields.put("status_code", event.statusCode());
        }
        if (event.exceptionType() != null) {
            fields.put("exception_type", event.exceptionType());
        }
        fields.put("delay_ms", event.delay().toMillis());
        fields.put("delay_source", event.delaySource());
        return fields;
    }

    private void sleep(Duration delay) throws InterruptedException {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private Optional<Duration> retryAfter(HttpResponse<?> response) {
        Optional<Duration> milliseconds = parseNumericDelay(
                response.headers().firstValue("retry-after-ms"),
                new BigDecimal("0.001"));
        if (milliseconds.isPresent()) {
            return milliseconds;
        }

        Optional<String> retryAfter = response.headers().firstValue("retry-after");
        Optional<Duration> seconds = parseNumericDelay(retryAfter, BigDecimal.ONE);
        if (seconds.isPresent()) {
            return seconds;
        }
        if (retryAfter.isEmpty()) {
            return Optional.empty();
        }
        try {
            Duration delay = Duration.between(
                    clock.instant(),
                    ZonedDateTime.parse(retryAfter.get(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            return validDelay(delay) ? Optional.of(delay) : Optional.empty();
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseNumericDelay(
            Optional<String> rawValue,
            BigDecimal secondsPerUnit) {
        if (rawValue.isEmpty()) {
            return Optional.empty();
        }
        try {
            BigDecimal seconds = new BigDecimal(rawValue.get().trim()).multiply(secondsPerUnit);
            if (seconds.compareTo(BigDecimal.ZERO) <= 0
                    || seconds.compareTo(BigDecimal.valueOf(60)) > 0) {
                return Optional.empty();
            }
            long delayNanos = seconds.movePointRight(9)
                    .setScale(0, RoundingMode.CEILING)
                    .longValueExact();
            return Optional.of(Duration.ofNanos(delayNanos));
        } catch (ArithmeticException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static boolean validDelay(Duration delay) {
        return !delay.isZero() && !delay.isNegative() && delay.compareTo(Duration.ofSeconds(60)) <= 0;
    }

    private Duration exponentialDelay(int retryNumber) {
        double baseSeconds = retryNumber >= 5
                ? 8.0D
                : 0.5D * Math.pow(2.0D, retryNumber - 1);
        double delaySeconds = baseSeconds * (1.0D - 0.25D * random.getAsDouble());
        return Duration.ofNanos(Math.round(delaySeconds * 1_000_000_000.0D));
    }

    private static void closeRetryBody(Object body) {
        if (!(body instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // 失败响应体采用尽力关闭策略，关闭异常不能遮蔽重试主流程。
        }
    }

    private static boolean shouldRetry(HttpResponse<?> response) {
        String override = response.headers().firstValue("x-should-retry").orElse(null);
        if ("true".equalsIgnoreCase(override)) {
            return true;
        }
        if ("false".equalsIgnoreCase(override)) {
            return false;
        }
        int statusCode = response.statusCode();
        return statusCode == 408
                || statusCode == 409
                || statusCode == 429
                || statusCode >= 500 && statusCode < 600;
    }

    @FunctionalInterface
    interface RetryableRequest<T> {
        HttpResponse<T> send(int retryCount) throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration delay) throws InterruptedException;
    }
}
