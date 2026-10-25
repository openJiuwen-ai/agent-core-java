/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModelHttpClients}, the shared model HttpClient cache: sharing
 * semantics per connection identity, key material, capacity and zero-cap behavior, invalid
 * property fallbacks, the warn-once ledger, concurrent builds, idle retirement via the
 * {@code nanoClock} seam, selector-death eviction (including the case-insensitive equality
 * matcher), and proxy credential redaction.
 *
 * <p>Isolated because these tests assert exact sizes of the process-global shared-client
 * cache.</p>
 *
 * @since 2026-09-21
 */
@Isolated
class ModelHttpClientsTest {
    private static final AtomicInteger TEST_THREAD_COUNTER = new AtomicInteger();

    // Records failures escaping test pool threads so concurrency tests fail loudly instead of
    // silently losing uncaught exceptions.
    private static final Queue<Throwable> TEST_THREAD_FAILURES = new ConcurrentLinkedQueue<>();

    @Test
    void buildsHttp2ClientWhenVersionConfigured() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_2, 7.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(7));
    }

    @Test
    void buildsHttp11ClientWhenVersionConfigured() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_1_1, 5.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(5));
    }

    @Test
    void defaultsToHttp11WhenHttpVersionIsNotConfigured() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(null, 6.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(6));
    }

    @Test
    void connectTimeoutComesFromConfigField() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_1_1, 11.0D), "https://api.example.test/v1")
                .build();

        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(11));
    }

    @Test
    void legacyTimeoutDoesNotDriveConnectTimeout() {
        HttpClient client = ModelHttpClients.builder(
                clientConfig(ModelHttpVersion.HTTP_2, null), "https://api.example.test/v1")
                .build();

        assertThat(client.version()).isEqualTo(HttpClient.Version.HTTP_2);
        assertThat(client.connectTimeout()).hasValue(Duration.ofSeconds(10));
    }

    @Test
    void explicitPortProxyModeIgnoresProxyWithoutPort() {
        ProxySelector proxySelector = ModelHttpClients.proxySelector(
                "http://proxy.example.test",
                ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY);

        assertThat(proxySelector).isNull();
    }

    @Test
    void defaultPortProxyModeAppliesDefaultPortWhenProxyOmitsPort() {
        ProxySelector httpProxySelector = ModelHttpClients.proxySelector(
                "http://proxy.example.test",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING);

        assertThat(httpProxySelector).isNotNull();
        assertProxyAddress(httpProxySelector, "proxy.example.test", 80);

        ProxySelector httpsProxySelector = ModelHttpClients.proxySelector(
                "https://secure-proxy.example.test",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING);

        assertThat(httpsProxySelector).isNotNull();
        assertProxyAddress(httpsProxySelector, "secure-proxy.example.test", 443);
    }

    @Test
    void redactUrlCredentialsWithUserInfoIsMasked() {
        assertThat(ModelHttpClients.redactUrlCredentials("http://user:secret@proxy.example.test:8080"))
                .isEqualTo("http://***@proxy.example.test:8080");
        assertThat(ModelHttpClients.redactUrlCredentials("user:secret@proxy.example.test:8080"))
                .isEqualTo("***@proxy.example.test:8080");
    }

    @Test
    void redactUrlCredentialsWithoutUserInfoIsUnchanged() {
        assertThat(ModelHttpClients.redactUrlCredentials(null)).isNull();
        assertThat(ModelHttpClients.redactUrlCredentials("http://proxy.example.test:8080"))
                .isEqualTo("http://proxy.example.test:8080");
        assertThat(ModelHttpClients.redactUrlCredentials("socks5://proxy.example.test"))
                .isEqualTo("socks5://proxy.example.test");
    }

    @Test
    void redactUrlCredentialsAtSignInPathIsNotUserInfo() {
        assertThat(ModelHttpClients.redactUrlCredentials("http://proxy.example.test/a@b?x=@y"))
                .isEqualTo("http://proxy.example.test/a@b?x=@y");
    }

    @Test
    void buildSharesHttpClientAcrossInstancesWithSameConfig() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .withSsl()
                    .withExplicitPortProxy()
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .withSsl()
                    .withExplicitPortProxy()
                    .build();

            assertThat(second).isSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildCreatesSeparateClientWhenTargetUrlDiffers() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-b.example.test/v1")
                    .build();

            assertThat(second).isNotSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildCreatesSeparateClientWhenConnectTimeoutDiffers() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1, 5.0D), "https://api.example.test/v1")
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1, 6.0D), "https://api.example.test/v1")
                    .build();

            assertThat(second).isNotSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildCreatesSeparateClientWhenHttpVersionDiffers() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_2), "https://api.example.test/v1")
                    .build();

            assertThat(second).isNotSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildSkipsSharingBeyondConfiguredCapacity() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "2");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            HttpClient firstAgain = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            assertThat(firstAgain).isSameAs(first);
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-b.example.test/v1")
                    .build();

            HttpClient overflowFirst = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();
            HttpClient overflowSecond = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();

            assertThat(overflowSecond).isNotSameAs(overflowFirst);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildDisablesSharingWhenCapConfiguredToZero() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "0");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(second).isNotSameAs(first);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildUsesDefaultCapWhenPropertyInvalid() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "not-a-number");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(second).isSameAs(first);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildUsesDefaultCapWhenPropertyNegative() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "-1");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient second = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(second).isSameAs(first);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildReturnsCachedClientWhenCacheIsFullAndKeyPresent() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "2");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-b.example.test/v1")
                    .build();

            HttpClient firstAgain = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();

            assertThat(firstAgain).isSameAs(first);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void cacheFullWarningFiresOncePerKey() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();

            Set<String> warnedKeys = ModelHttpClients.cacheFullWarnedKeysForTesting();
            assertThat(warnedKeys).hasSize(1);
            assertThat(warnedKeys.iterator().next()).contains("overflow.example.test");
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void concurrentMissesNeverExceedConfiguredCapacity() throws Exception {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "4");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            ExecutorService executor = newTestExecutor(12);
            try {
                List<Future<HttpClient>> builds = new ArrayList<>();
                for (int index = 0; index < 12; index++) {
                    String url = "https://api-" + index + ".example.test/v1";
                    builds.add(executor.submit(() -> ModelHttpClients
                            .builder(clientConfig(ModelHttpVersion.HTTP_1_1), url)
                            .build()));
                }
                for (Future<HttpClient> build : builds) {
                    assertThat(build.get(10, TimeUnit.SECONDS)).isNotNull();
                }
                assertThat(ModelHttpClients.sharedClientCountForTesting()).isEqualTo(4);
                assertThat(TEST_THREAD_FAILURES).isEmpty();
            } finally {
                executor.shutdownNow();
            }
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void concurrentBuildsForSameKeyShareOneInstance() throws Exception {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            ExecutorService executor = newTestExecutor(12);
            try {
                List<Future<HttpClient>> builds = new ArrayList<>();
                for (int index = 0; index < 12; index++) {
                    builds.add(executor.submit(() -> ModelHttpClients
                            .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                            .build()));
                }
                HttpClient first = builds.get(0).get(10, TimeUnit.SECONDS);
                for (Future<HttpClient> build : builds) {
                    assertThat(build.get(10, TimeUnit.SECONDS)).isSameAs(first);
                }
                assertThat(TEST_THREAD_FAILURES).isEmpty();
            } finally {
                executor.shutdownNow();
            }
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void buildSharesClientAcrossTrailingSlashAndWhitespaceVariants() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient slashVariant = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1/")
                    .build();
            HttpClient doubleSlashVariant = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1//")
                    .build();
            HttpClient whitespaceVariant = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "  https://api.example.test/v1  ")
                    .build();

            assertThat(slashVariant).isSameAs(first);
            assertThat(doubleSlashVariant).isSameAs(first);
            assertThat(whitespaceVariant).isSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void idleRetirementFreesCapacityForNewKeys() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        System.setProperty("openjiuwen.llm.http.shared-client-idle-seconds", "1");
        ModelHttpClients.clearSharedClientsForTesting();
        long realNow = System.nanoTime();
        try {
            // Build the stale entry under a past clock so its idle age is deterministic
            // no matter how long the build itself takes on a slow runner.
            ModelHttpClients.setNanoClockForTesting(() -> realNow - TimeUnit.SECONDS.toNanos(10));
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://stale.example.test/v1")
                    .build();
            ModelHttpClients.setNanoClockForTesting(() -> realNow + TimeUnit.SECONDS.toNanos(2));

            HttpClient fresh = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://fresh.example.test/v1")
                    .build();
            HttpClient freshAgain = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://fresh.example.test/v1")
                    .build();

            assertThat(freshAgain).isSameAs(fresh);
            assertThat(ModelHttpClients.sharedClientCountForTesting()).isEqualTo(1);
            assertThat(ModelHttpClients.cacheFullWarnedKeysForTesting()).isEmpty();
        } finally {
            ModelHttpClients.setNanoClockForTesting(System::nanoTime);
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            System.clearProperty("openjiuwen.llm.http.shared-client-idle-seconds");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void idleRetirementCanBeDisabled() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        System.setProperty("openjiuwen.llm.http.shared-client-idle-seconds", "0");
        ModelHttpClients.clearSharedClientsForTesting();
        long realNow = System.nanoTime();
        try {
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://stale.example.test/v1")
                    .build();
            ModelHttpClients.setNanoClockForTesting(() -> realNow + TimeUnit.SECONDS.toNanos(2));

            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://fresh.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.sharedClientCountForTesting()).isEqualTo(1);
            assertThat(ModelHttpClients.cacheFullWarnedKeysForTesting()).hasSize(1);
        } finally {
            ModelHttpClients.setNanoClockForTesting(System::nanoTime);
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            System.clearProperty("openjiuwen.llm.http.shared-client-idle-seconds");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void freshEntriesSurviveIdleSweep() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        System.setProperty("openjiuwen.llm.http.shared-client-idle-seconds", "1");
        ModelHttpClients.clearSharedClientsForTesting();
        long realNow = System.nanoTime();
        ModelHttpClients.setNanoClockForTesting(() -> realNow);
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://fresh-b.example.test/v1")
                    .build();
            HttpClient firstAgain = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();

            assertThat(firstAgain).isSameAs(first);
            assertThat(ModelHttpClients.sharedClientCountForTesting()).isEqualTo(1);
        } finally {
            ModelHttpClients.setNanoClockForTesting(System::nanoTime);
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            System.clearProperty("openjiuwen.llm.http.shared-client-idle-seconds");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void admittedKeyIsRemovedFromWarnedLedger() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();
            assertThat(ModelHttpClients.cacheFullWarnedKeysForTesting()).hasSize(1);

            HttpClient sharedFirst = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            ModelHttpClients.evictIfDead(sharedFirst, new IOException("selector manager closed"));

            HttpClient admitted = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();
            HttpClient admittedAgain = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://overflow.example.test/v1")
                    .build();

            assertThat(admittedAgain).isSameAs(admitted);
            assertThat(ModelHttpClients.cacheFullWarnedKeysForTesting()).isEmpty();
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void cacheFullLedgerIsBounded() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api-a.example.test/v1")
                    .build();
            for (int index = 0; index < 140; index++) {
                ModelHttpClients
                        .builder(clientConfig(ModelHttpVersion.HTTP_1_1),
                                "https://overflow-" + index + ".example.test/v1")
                        .build();
            }

            assertThat(ModelHttpClients.cacheFullWarnedKeysForTesting()).hasSize(128);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadRemovesSharedClientOnSelectorClosedError() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            boolean isEvicted = ModelHttpClients.evictIfDead(
                    shared, new IOException("selector manager closed"));

            assertThat(isEvicted).isTrue();
            HttpClient rebuilt = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(rebuilt).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadIgnoresNullClientOrError() {
        assertThat(ModelHttpClients.evictIfDead(null, new IOException("selector manager closed"))).isFalse();
        assertThat(ModelHttpClients.evictIfDead(HttpClient.newHttpClient(), null)).isFalse();
    }

    @Test
    void evictIfDeadMatchesHintThroughCauseChain() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            boolean isEvicted = ModelHttpClients.evictIfDead(
                    shared, new IOException("send failed", new IOException("selector manager closed")));

            assertThat(isEvicted).isTrue();
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadMatchesCaseInsensitiveSelectorMessage() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            boolean isEvicted = ModelHttpClients.evictIfDead(
                    shared, new IOException("Selector manager closed"));

            assertThat(isEvicted).isTrue();
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadIgnoresSelectorMessageEmbeddedInLongerText() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            boolean isEvicted = ModelHttpClients.evictIfDead(
                    shared, new IOException("request failed: selector manager closed"));

            assertThat(isEvicted).isFalse();
            HttpClient sameKeyClient = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(sameKeyClient).isSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadIgnoresUnrelatedTransportErrors() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            boolean isEvicted = ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"));

            assertThat(isEvicted).isFalse();
            HttpClient sameKeyClient = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(sameKeyClient).isSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadReportsDeadClientNotHeldByCache() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "0");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient unshared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            boolean isEvicted = ModelHttpClients.evictIfDead(
                    unshared, new IOException("selector manager closed"));

            assertThat(isEvicted).isTrue();
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadReportsDeadnessOnRepeatedCallsForSameClient() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("selector manager closed"))).isTrue();
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("selector manager closed"))).isTrue();

            HttpClient rebuilt = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(rebuilt).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDeadRecyclesClientAfterRepeatedUnknownFailures() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isFalse();
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection timed out"))).isFalse();
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isTrue();

            HttpClient recycled = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(recycled).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    private static ExecutorService newTestExecutor(int threads) {
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(32),
                runnable -> {
                    Thread thread = new Thread(runnable,
                            "model-httpclients-test-" + TEST_THREAD_COUNTER.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setUncaughtExceptionHandler((poolThread, error) -> TEST_THREAD_FAILURES.add(error));
                    return thread;
                });
    }

    private static ModelClientConfig clientConfig(ModelHttpVersion httpVersion, Double connectTimeoutSeconds) {
        ModelClientConfig.ModelClientConfigBuilder builder = ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiBase("https://api.example.test/v1")
                .httpVersion(httpVersion)
                .timeout(9.0D);
        if (connectTimeoutSeconds != null) {
            builder.connectTimeoutSeconds(connectTimeoutSeconds);
        }
        return builder.build();
    }

    private static ModelClientConfig clientConfig(ModelHttpVersion httpVersion) {
        return clientConfig(httpVersion, null);
    }

    private static void assertProxyAddress(ProxySelector proxySelector, String host, int port) {
        List<Proxy> proxies = proxySelector.select(URI.create("https://api.example.test/v1"));
        InetSocketAddress address = (InetSocketAddress) proxies.get(0).address();
        assertThat(address.getHostString()).isEqualTo(host);
        assertThat(address.getPort()).isEqualTo(port);
    }
}
