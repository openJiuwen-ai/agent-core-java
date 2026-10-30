/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.classification;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.foundation.llm.modelclients.IdleTimeoutIOException;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Predicate;

/**
 * Default {@link ModelErrorClassifier}: an ordered rule table combining HTTP
 * status codes with error message keywords. Exact status rules win over
 * keyword rules (a 429 with quota wording still classifies as rate limited);
 * cancellation and timeout type markers win over everything else. Rules are
 * evaluated in order and the first match wins. Endpoint-specific quirks are
 * absorbed by appending rules (they slot in before the two fallback rules);
 * the category set itself stays stable.
 *
 * @since 2026-10-19
 */
public class RuleBasedModelErrorClassifier implements ModelErrorClassifier {
    private static final int TIMEOUT_ERROR_CODE = 181008;
    private static final int STATUS_RATE_LIMITED = 429;
    private static final int STATUS_PAYMENT_REQUIRED = 402;
    private static final int STATUS_UNAUTHORIZED = 401;
    private static final int STATUS_FORBIDDEN = 403;
    private static final int STATUS_REQUEST_TIMEOUT = 408;
    private static final int STATUS_CONFLICT = 409;
    private static final int STATUS_4XX_LOW = 400;
    private static final int STATUS_4XX_HIGH = 500;
    private static final int STATUS_5XX_HIGH = 600;
    private static final int MAX_CAUSE_DEPTH = 32;

    private static final Set<String> QUOTA_KEYWORDS = Set.of(
        "insufficient_balance", "insufficient balance", "insufficient_quota", "欠费", "余额不足");
    private static final Set<String> CONTEXT_LENGTH_KEYWORDS = Set.of(
        "context_length_exceeded", "prompt_too_long", "max_context_length", "context window",
        "context length", "maximum context", "too many tokens", "reduce the length",
        "prompt exceeds max length", "prompt exceeds maximum length");
    private static final Set<String> RATE_LIMIT_KEYWORDS = Set.of(
        "rate limit", "rate_limit", "ratelimit", "too many requests");
    private static final Set<String> TRUNCATION_KEYWORDS =
        Set.of("premature eof", "bytes received", "received no bytes", "closed",
            "chunked transfer encoding");

    private final List<ClassificationRule> rules;

    /**
     * Creates the classifier with the built-in rule table only.
     */
    public RuleBasedModelErrorClassifier() {
        this(List.of());
    }

    /**
     * Creates the classifier with additional rules. Additional rules are
     * evaluated after all built-in specific rules but before the two fallback
     * rules, so new endpoint-specific expressions take effect without
     * disturbing the category set.
     *
     * @param additionalRules extra rules to slot before the fallbacks, must not be null
     */
    public RuleBasedModelErrorClassifier(List<ClassificationRule> additionalRules) {
        List<ClassificationRule> table = new ArrayList<>();
        table.add(RuleBasedModelErrorClassifier::ruleCancellationChannel);
        table.add(RuleBasedModelErrorClassifier::ruleTimeoutMarkers);
        table.add(RuleBasedModelErrorClassifier::ruleThreadInterrupt);
        table.add(RuleBasedModelErrorClassifier::ruleRateLimitedStatus);
        table.add(RuleBasedModelErrorClassifier::ruleQuotaExceeded);
        table.add(RuleBasedModelErrorClassifier::ruleAuthFailed);
        table.add(RuleBasedModelErrorClassifier::ruleContextLengthExceeded);
        table.add(RuleBasedModelErrorClassifier::ruleServerTemporary);
        table.add(RuleBasedModelErrorClassifier::ruleNetworkTemporary);
        table.add(RuleBasedModelErrorClassifier::ruleRateLimitKeywords);
        table.addAll(additionalRules);
        table.add(RuleBasedModelErrorClassifier::rulePermanent4xx);
        table.add(RuleBasedModelErrorClassifier::ruleFallback);
        this.rules = List.copyOf(table);
    }

    @Override
    public ModelCallClassification classify(ModelErrorFacts facts) {
        for (ClassificationRule rule : rules) {
            Optional<ModelCallClassification> matched = rule.match(facts);
            if (matched.isPresent()) {
                return matched.get();
            }
        }
        throw new IllegalStateException("rule table is not total: no fallback rule matched");
    }

    private static Optional<ModelCallClassification> ruleCancellationChannel(ModelErrorFacts facts) {
        if (hasCauseMatching(facts.throwable(), cause -> cause instanceof CancellationException)) {
            return Optional.of(cancelled());
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleTimeoutMarkers(ModelErrorFacts facts) {
        boolean isTimeout = hasCauseMatching(facts.throwable(), RuleBasedModelErrorClassifier::isTimeoutCause);
        if (isTimeout) {
            return Optional.of(temporary(true));
        }
        return Optional.empty();
    }

    private static boolean isTimeoutCause(Throwable cause) {
        if (cause instanceof IdleTimeoutIOException || cause instanceof SocketTimeoutException
            || cause instanceof HttpTimeoutException) {
            return true;
        }
        return cause instanceof BaseError baseError && baseError.getCode() == TIMEOUT_ERROR_CODE;
    }

    // Caveat: classification can run on any thread; when that thread's interrupt flag is
    // set for an unrelated reason and the cause chain happens to contain an
    // InterruptedException, the failure is judged as user cancelled. The window is
    // bounded and documented as an accepted trade-off.
    private static Optional<ModelCallClassification> ruleThreadInterrupt(ModelErrorFacts facts) {
        boolean hasInterruptedIo = hasCauseMatching(facts.throwable(),
            cause -> cause instanceof InterruptedIOException
                && !(cause instanceof SocketTimeoutException)
                && !(cause instanceof IdleTimeoutIOException));
        boolean hasInterrupt = Thread.currentThread().isInterrupted()
            && hasCauseMatching(facts.throwable(), cause -> cause instanceof InterruptedException);
        if (hasInterruptedIo || hasInterrupt) {
            return Optional.of(cancelled());
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleRateLimitedStatus(ModelErrorFacts facts) {
        Integer status = facts.httpStatus();
        if (status != null && status == STATUS_RATE_LIMITED) {
            return Optional.of(rateLimited(facts));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleQuotaExceeded(ModelErrorFacts facts) {
        Integer status = facts.httpStatus();
        if (status != null && status == STATUS_PAYMENT_REQUIRED) {
            return Optional.of(permanent(ModelErrorCategory.QUOTA_EXCEEDED));
        }
        if (containsAny(facts, QUOTA_KEYWORDS)) {
            return Optional.of(permanent(ModelErrorCategory.QUOTA_EXCEEDED));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleAuthFailed(ModelErrorFacts facts) {
        Integer status = facts.httpStatus();
        if (status != null && (status == STATUS_UNAUTHORIZED || status == STATUS_FORBIDDEN)) {
            return Optional.of(permanent(ModelErrorCategory.AUTH_FAILED));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleContextLengthExceeded(ModelErrorFacts facts) {
        if (containsAny(facts, CONTEXT_LENGTH_KEYWORDS)) {
            return Optional.of(permanent(ModelErrorCategory.CONTEXT_LENGTH_EXCEEDED));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleServerTemporary(ModelErrorFacts facts) {
        Integer status = facts.httpStatus();
        if (status == null) {
            return Optional.empty();
        }
        boolean isTemporary = status == STATUS_REQUEST_TIMEOUT || status == STATUS_CONFLICT
            || (status >= STATUS_4XX_HIGH && status < STATUS_5XX_HIGH);
        if (isTemporary) {
            return Optional.of(temporary(true));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleNetworkTemporary(ModelErrorFacts facts) {
        boolean isNetwork = hasCauseMatching(facts.throwable(),
            cause -> cause instanceof ConnectException || cause instanceof UnknownHostException
                || cause instanceof NoRouteToHostException || cause instanceof SocketException);
        if (isNetwork) {
            return Optional.of(temporary(true));
        }
        // Truncated bodies (premature EOF, short content-length) are transport
        // failures too, but the JDK reports them as plain IOExceptions with only
        // message markers, so they are matched by keyword. Same gate as the
        // rate-limit keyword rule: a marker echoed inside a 4xx error body must
        // not flip the permanent classification.
        Integer status = facts.httpStatus();
        boolean isKeywordSignal = status == null || status < STATUS_4XX_LOW;
        if (isKeywordSignal && containsAny(facts, TRUNCATION_KEYWORDS)) {
            return Optional.of(temporary(true));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleRateLimitKeywords(ModelErrorFacts facts) {
        Integer status = facts.httpStatus();
        // Keywords are the only signal for transport failures (no status) and for
        // embedded error bodies in success responses (2xx/3xx); a precise 4xx/5xx
        // status has already been handled by the status rules above.
        boolean isKeywordSignal = status == null || status < STATUS_4XX_LOW;
        if (isKeywordSignal && containsAny(facts, RATE_LIMIT_KEYWORDS)) {
            return Optional.of(rateLimited(facts));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> rulePermanent4xx(ModelErrorFacts facts) {
        Integer status = facts.httpStatus();
        if (status != null && status >= STATUS_4XX_LOW && status < STATUS_4XX_HIGH) {
            return Optional.of(temporary(false));
        }
        return Optional.empty();
    }

    private static Optional<ModelCallClassification> ruleFallback(ModelErrorFacts facts) {
        return Optional.of(temporary(false));
    }

    private static ModelCallClassification cancelled() {
        return new ModelCallClassification(ModelErrorCategory.USER_CANCELLED, false, null);
    }

    private static ModelCallClassification temporary(boolean isRetryable) {
        return new ModelCallClassification(ModelErrorCategory.TEMPORARY, isRetryable, null);
    }

    private static ModelCallClassification permanent(ModelErrorCategory category) {
        return new ModelCallClassification(category, false, null);
    }

    private static ModelCallClassification rateLimited(ModelErrorFacts facts) {
        return new ModelCallClassification(ModelErrorCategory.RATE_LIMITED, true, facts.retryAfterSeconds());
    }

    private static boolean containsAny(ModelErrorFacts facts, Set<String> keywords) {
        String haystack = searchableText(facts);
        if (haystack.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String searchableText(ModelErrorFacts facts) {
        StringBuilder text = new StringBuilder();
        appendIfPresent(text, facts.errorCode());
        appendIfPresent(text, facts.errorType());
        appendIfPresent(text, facts.errorMessage());
        appendIfPresent(text, facts.errorBody());
        Throwable cursor = facts.throwable();
        int depth = 0;
        while (cursor != null && depth < MAX_CAUSE_DEPTH) {
            appendIfPresent(text, cursor.getMessage());
            cursor = cursor.getCause();
            depth++;
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendIfPresent(StringBuilder text, String value) {
        if (value != null && !value.isEmpty()) {
            text.append(value).append(' ');
        }
    }

    private static boolean hasCauseMatching(Throwable error, Predicate<Throwable> predicate) {
        Throwable cursor = error;
        int depth = 0;
        while (cursor != null && depth < MAX_CAUSE_DEPTH) {
            if (predicate.test(cursor)) {
                return true;
            }
            cursor = cursor.getCause();
            depth++;
        }
        return false;
    }
}
