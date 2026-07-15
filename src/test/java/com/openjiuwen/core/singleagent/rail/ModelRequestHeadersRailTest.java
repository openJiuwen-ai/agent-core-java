/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.rail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.runner.callback.AbortError;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRequestHeadersRailTest {

    @Test
    void completedProviderMergesHeadersWithoutChangingValues() {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setRequestHeaders(Map.of("X-Trace-Id", "trace-1"));
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(Map.of("Authorization", "custom-token")));

        CompletionStage<Void> result = rail.beforeModelCall(contextWith(inputs));

        assertThat(result.toCompletableFuture()).isCompleted();
        assertThat(inputs.getRequestHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "X-Trace-Id", "trace-1",
                "Authorization", "custom-token"
        ));
    }

    @Test
    void waitsForAsynchronousProviderBeforeReturning() throws Exception {
        CompletableFuture<Map<String, String>> providedHeaders = new CompletableFuture<>();
        CountDownLatch providerCalled = new CountDownLatch(1);
        ModelCallInputs inputs = new ModelCallInputs();
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context -> {
            providerCalled.countDown();
            return providedHeaders;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<CompletionStage<Void>> invocation = executor.submit(() -> rail.beforeModelCall(contextWith(inputs)));
            assertThat(providerCalled.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(invocation).isNotDone();

            providedHeaders.complete(Map.of("Authorization", "async-token"));

            assertThat(invocation.get(5, TimeUnit.SECONDS).toCompletableFuture()).isCompleted();
            assertThat(inputs.getRequestHeaders()).containsEntry("Authorization", "async-token");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void requestHeaderInputAndGetterAreDefensiveCopies() {
        ModelCallInputs inputs = new ModelCallInputs();
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Authorization", "original-token");

        inputs.setRequestHeaders(source);
        source.put("Authorization", "changed-source-token");
        Map<String, String> returned = inputs.getRequestHeaders();
        returned.put("Authorization", "changed-getter-token");

        assertThat(inputs.getRequestHeaders()).containsExactly(
                Map.entry("Authorization", "original-token"));
    }

    @Test
    void setRequestHeadersClearsPreviouslyStoredHeaders() {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setRequestHeaders(Map.of(
                "Authorization", "old-token",
                "X-Old-Header", "old-value"
        ));

        inputs.setRequestHeaders(Map.of("X-New-Header", "new-value"));

        assertThat(inputs.getRequestHeaders()).containsExactly(
                Map.entry("X-New-Header", "new-value"));
    }

    @Test
    void modelInvokeOptionsCopiesRequestHeadersAndKeepsExtraFieldsSemantics() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Authorization", "original-token");
        Map<String, Object> extraFields = new LinkedHashMap<>();
        extraFields.put("request_tag", "visible");

        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .requestHeaders(source)
                .extraFields(extraFields)
                .build();
        source.put("Authorization", "changed-source-token");
        Map<String, String> returned = options.getRequestHeaders();
        returned.put("Authorization", "changed-getter-token");

        assertThat(options.getRequestHeaders()).containsExactly(
                Map.entry("Authorization", "original-token"));
        assertThat(options.getExtraFields()).containsExactly(Map.entry("request_tag", "visible"));
        assertThat(ModelInvokeOptions.builder().build().getRequestHeaders()).isEmpty();
        assertThat(ModelInvokeOptions.builder().build().getExtraFields()).isEmpty();
    }

    @Test
    void modelInvokeOptionsConstructorCopiesRequestHeaders() throws Exception {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("Authorization", "original-token");
        var constructor = java.util.Arrays.stream(ModelInvokeOptions.class.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 11)
                .findFirst()
                .orElseThrow();
        constructor.setAccessible(true);

        ModelInvokeOptions options = (ModelInvokeOptions) constructor.newInstance(
                null, null, null, null, null, null, null, null, null, source, new LinkedHashMap<>());
        source.put("Authorization", "changed-source-token");

        assertThat(options.getRequestHeaders()).containsExactly(
                Map.entry("Authorization", "original-token"));
    }

    @Test
    void modelInvokeOptionsToStringDoesNotExposeRequestHeaders() {
        String sensitiveValue = "sensitive-authorization-value";
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .requestHeaders(Map.of("Authorization", sensitiveValue))
                .build();

        assertThat(options.toString()).doesNotContain("Authorization", sensitiveValue);
    }

    @Test
    void modelInvokeOptionsBuilderToStringDoesNotExposeRequestHeaders() {
        String sensitiveValue = "sensitive-builder-authorization-value";
        ModelInvokeOptions.ModelInvokeOptionsBuilder builder = ModelInvokeOptions.builder()
                .requestHeaders(Map.of("Authorization", sensitiveValue));

        assertThat(builder.toString()).doesNotContain("Authorization", sensitiveValue);
    }

    @Test
    void modelInvokeOptionsToBuilderStringDoesNotExposeRequestHeaders() {
        String sensitiveValue = "sensitive-to-builder-authorization-value";
        ModelInvokeOptions options = ModelInvokeOptions.builder()
                .requestHeaders(Map.of("Authorization", sensitiveValue))
                .build();

        assertThat(options.toBuilder().toString()).doesNotContain("Authorization", sensitiveValue);
    }

    @Test
    void mergeKeepsDifferentHeaders() {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setRequestHeaders(Map.of("X-Trace-Id", "trace-1"));

        inputs.mergeRequestHeaders(Map.of("Authorization", "token-1"));

        assertThat(inputs.getRequestHeaders()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "X-Trace-Id", "trace-1",
                "Authorization", "token-1"
        ));
    }

    @Test
    void mergeOverwritesSameHeaderIgnoringCase() {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setRequestHeaders(Map.of("Authorization", "old-token"));

        inputs.mergeRequestHeaders(Map.of("authorization", "new-token"));

        assertThat(inputs.getRequestHeaders()).hasSize(1);
        assertThat(headerValue(inputs.getRequestHeaders(), "Authorization")).isEqualTo("new-token");
    }

    @Test
    void consumeReturnsCopyAndClearsHeaders() {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setRequestHeaders(Map.of("Authorization", "token-1"));

        Map<String, String> consumed = inputs.consumeRequestHeaders();
        consumed.put("Authorization", "changed-token");

        assertThat(consumed).containsEntry("Authorization", "changed-token");
        assertThat(inputs.getRequestHeaders()).isEmpty();
    }

    @Test
    void jacksonSerializationIgnoresRequestHeaders() throws Exception {
        ModelCallInputs inputs = new ModelCallInputs();
        inputs.setRequestHeaders(Map.of("Authorization", "secret-token"));

        JsonNode serialized = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(inputs));

        assertThat(serialized.has("requestHeaders")).isFalse();
        assertThat(serialized.toString()).doesNotContain("secret-token");
    }

    @Test
    void constructorRejectsNullProvider() {
        assertThatThrownBy(() -> new ModelRequestHeadersRail(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void rejectsNonModelCallInputs() {
        ModelRequestHeadersRail rail = railReturning(Map.of("Authorization", "token-1"));

        assertAbort(() -> rail.beforeModelCall(new AgentCallbackContext()), "inputs");
    }

    @Test
    void rejectsNullProviderStage() {
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context -> null);

        assertAbort(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())), "stage");
    }

    @Test
    void rejectsExceptionalProviderStageWithoutLeakingCause() {
        String providerMessage = "provider-failure-secret-message";
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.failedFuture(new IllegalStateException(providerMessage)));

        assertThatThrownBy(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())))
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase("provider");
                    assertThat(error.getReason()).doesNotContain(providerMessage);
                    assertThat(error.getCause()).isNull();
                });
    }

    @Test
    void rejectsSynchronousProviderFailureWithoutLeakingCause() {
        String providerMessage = "synchronous-provider-secret-message";
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context -> {
            throw new IllegalStateException(providerMessage);
        });

        assertThatThrownBy(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())))
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase("provider");
                    assertThat(error.getReason()).doesNotContain(providerMessage);
                    assertThat(error.getCause()).isNull();
                });
    }

    @Test
    void rejectsHeaderMapIterationFailureWithoutLeakingCause() {
        String mapFailureMessage = "secret-map-failure";
        Map<String, String> failingMap = new AbstractMap<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public Set<Entry<String, String>> entrySet() {
                throw new IllegalStateException(mapFailureMessage);
            }
        };
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(failingMap));

        assertThatThrownBy(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())))
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase("headers");
                    assertThat(error.getReason()).doesNotContain(mapFailureMessage);
                    assertThat(error.getCause()).isNull();
                });
    }

    @Test
    void sanitizesAbortErrorThrownWhileCopyingProviderMap() {
        String sensitiveReason = "sensitive-map-reason";
        Map<String, String> failingMap = new AbstractMap<>() {
            @Override
            public int size() {
                return 1;
            }

            @Override
            public Set<Entry<String, String>> entrySet() {
                throw new AbortError(sensitiveReason);
            }
        };
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(failingMap));

        assertThatThrownBy(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())))
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase("headers");
                    assertThat(error.getReason()).doesNotContain(sensitiveReason);
                    assertThat(error.getCause()).isNull();
                });
    }

    @Test
    void sanitizesAbortErrorThrownWhileMergingIntoInputs() {
        String sensitiveReason = "sensitive-merge-reason";
        ModelCallInputs inputs = new ModelCallInputs() {
            @Override
            public void mergeRequestHeaders(Map<String, String> headers) {
                throw new AbortError(sensitiveReason);
            }
        };
        ModelRequestHeadersRail rail = railReturning(Map.of("Authorization", "token-1"));

        assertThatThrownBy(() -> rail.beforeModelCall(contextWith(inputs)))
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase("headers");
                    assertThat(error.getReason()).doesNotContain(sensitiveReason);
                    assertThat(error.getCause()).isNull();
                });
    }

    @Test
    void providerMapChangesAfterCallbackDoNotAffectInputs() {
        Map<String, String> providedHeaders = new LinkedHashMap<>();
        providedHeaders.put("Authorization", "original-token");
        ModelCallInputs inputs = new ModelCallInputs();
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(providedHeaders));

        rail.beforeModelCall(contextWith(inputs));
        providedHeaders.put("Authorization", "changed-token");
        providedHeaders.put("X-Late-Header", "late-value");

        assertThat(inputs.getRequestHeaders()).containsExactly(
                Map.entry("Authorization", "original-token"));
    }

    @Test
    void rejectsNullHeaderMap() {
        ModelRequestHeadersRail rail = new ModelRequestHeadersRail(context ->
                CompletableFuture.completedFuture(null));

        assertAbort(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())), "headers");
    }

    @Test
    void rejectsEmptyHeaderMap() {
        assertAbort(() -> railReturning(Map.of()).beforeModelCall(contextWith(new ModelCallInputs())), "headers");
    }

    @Test
    void rejectsProviderMapThatClaimsNonEmptyButHasNoEntries() {
        Map<String, String> inconsistentMap = new AbstractMap<>() {
            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public Set<Entry<String, String>> entrySet() {
                return Set.of();
            }
        };

        assertAbort(() -> railReturning(inconsistentMap).beforeModelCall(contextWith(new ModelCallInputs())),
                "headers");
    }

    @Test
    void rejectsBlankAuthorizationWithoutLeakingValue() {
        String sensitiveBlankValue = "  \t ";
        ModelRequestHeadersRail rail = railReturning(Map.of("aUtHoRiZaTiOn", sensitiveBlankValue));

        assertThatThrownBy(() -> rail.beforeModelCall(contextWith(new ModelCallInputs())))
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase("authorization");
                    assertThat(error.getReason()).doesNotContain(sensitiveBlankValue);
                    assertThat(error.getCause()).isNull();
                });
    }

    private static ModelRequestHeadersRail railReturning(Map<String, String> headers) {
        return new ModelRequestHeadersRail(context -> CompletableFuture.completedFuture(headers));
    }

    private static AgentCallbackContext contextWith(ModelCallInputs inputs) {
        AgentCallbackContext context = new AgentCallbackContext();
        context.setInputs(inputs);
        return context;
    }

    private static void assertAbort(Runnable invocation, String stageDescription) {
        assertThatThrownBy(invocation::run)
                .isInstanceOfSatisfying(AbortError.class, error -> {
                    assertThat(error.getReason()).containsIgnoringCase(stageDescription);
                    assertThat(error.getCause()).isNull();
                });
    }

    private static String headerValue(Map<String, String> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }
}
