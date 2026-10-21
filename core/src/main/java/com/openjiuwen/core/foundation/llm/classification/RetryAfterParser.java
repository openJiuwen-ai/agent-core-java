/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpHeaders;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Parses server retry hints from {@code retry-after-ms} / {@code retry-after}
 * response headers. Numeric milliseconds win over numeric seconds, which win
 * over RFC 1123 dates; values outside (0, 60s] are ignored. Migrated from the
 * former send-stage retry wrapper so the classification layer can attach the
 * suggested delay to rate-limit classifications.
 *
 * @since 2026-10-19
 */
public final class RetryAfterParser {
    private static final BigDecimal MILLIS_TO_SECONDS = new BigDecimal("0.001");
    private static final BigDecimal MAX_DELAY_SECONDS = BigDecimal.valueOf(60);
    private static final String RETRY_AFTER_MS = "retry-after-ms";
    private static final String RETRY_AFTER = "retry-after";

    private RetryAfterParser() {
    }

    /**
     * Parses the retry hint using the system clock for RFC 1123 dates.
     *
     * @param headers response headers, must not be null
     * @return suggested delay in seconds, or empty when absent or invalid
     */
    public static OptionalDouble parseSeconds(HttpHeaders headers) {
        return parseSeconds(headers, Clock.systemUTC());
    }

    /**
     * Parses the retry hint.
     *
     * @param headers response headers, must not be null
     * @param clock clock used to evaluate RFC 1123 dates, must not be null
     * @return suggested delay in seconds, or empty when absent or invalid
     */
    public static OptionalDouble parseSeconds(HttpHeaders headers, Clock clock) {
        OptionalDouble millis = parseNumeric(headers.firstValue(RETRY_AFTER_MS), MILLIS_TO_SECONDS);
        if (millis.isPresent()) {
            return millis;
        }
        Optional<String> rawRetryAfter = headers.firstValue(RETRY_AFTER);
        OptionalDouble seconds = parseNumeric(rawRetryAfter, BigDecimal.ONE);
        if (seconds.isPresent()) {
            return seconds;
        }
        return parseHttpDate(rawRetryAfter, clock);
    }

    private static OptionalDouble parseNumeric(Optional<String> rawValue, BigDecimal secondsPerUnit) {
        if (rawValue.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            BigDecimal seconds = new BigDecimal(rawValue.get().trim()).multiply(secondsPerUnit);
            if (seconds.compareTo(BigDecimal.ZERO) <= 0 || seconds.compareTo(MAX_DELAY_SECONDS) > 0) {
                return OptionalDouble.empty();
            }
            double rounded = seconds.setScale(9, RoundingMode.CEILING).doubleValue();
            return OptionalDouble.of(rounded);
        } catch (ArithmeticException | NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble parseHttpDate(Optional<String> rawValue, Clock clock) {
        if (rawValue.isEmpty()) {
            return OptionalDouble.empty();
        }
        try {
            Duration delay = Duration.between(clock.instant(),
                ZonedDateTime.parse(rawValue.get(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
            boolean isValid = !delay.isZero() && !delay.isNegative()
                && delay.compareTo(Duration.ofSeconds(MAX_DELAY_SECONDS.longValue())) <= 0;
            return isValid ? OptionalDouble.of(delay.toNanos() / 1_000_000_000.0D) : OptionalDouble.empty();
        } catch (DateTimeParseException exception) {
            return OptionalDouble.empty();
        }
    }
}
