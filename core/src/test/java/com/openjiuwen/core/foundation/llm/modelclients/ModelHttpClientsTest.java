/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import static com.openjiuwen.core.foundation.llm.modelclients.ModelHttpClients.DeadClientVerdict.ALIVE;
import static com.openjiuwen.core.foundation.llm.modelclients.ModelHttpClients.DeadClientVerdict.DEAD_BY_FAILURE_THRESHOLD;
import static com.openjiuwen.core.foundation.llm.modelclients.ModelHttpClients.DeadClientVerdict.DEAD_BY_OFFICIAL_SIGNAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelHttpVersion;
import com.openjiuwen.core.foundation.llm.schema.ProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModelHttpClients}, the shared model HttpClient cache: sharing
 * semantics per connection identity, key material, capacity and zero-cap behavior, invalid
 * property fallbacks, the warn-once ledger, concurrent builds, idle retirement via the
 * {@code nanoClock} seam, selector-death eviction (including the case-insensitive equality
 * matcher), event-log throttling (per-event per-identity cooldown, global retirement
 * throttle), and proxy credential redaction.
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
        assertThat(ModelHttpClients.resolveProxyAddress(
                "http://proxy.example.test",
                ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY)).isEmpty();
    }

    @Test
    void defaultPortProxyModeAppliesDefaultPortWhenProxyOmitsPort() {
        assertThat(ModelHttpClients.resolveProxyAddress(
                "http://proxy.example.test",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING))
                .hasValueSatisfying(address -> {
                    assertThat(address.getHostString()).isEqualTo("proxy.example.test");
                    assertThat(address.getPort()).isEqualTo(80);
                });

        assertThat(ModelHttpClients.resolveProxyAddress(
                "https://secure-proxy.example.test",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING))
                .hasValueSatisfying(address -> {
                    assertThat(address.getHostString()).isEqualTo("secure-proxy.example.test");
                    assertThat(address.getPort()).isEqualTo(443);
                });
    }

    @Test
    void redactUrlCredentials_userInfoInAuthority_masksCredentials() {
        assertThat(ModelHttpClients.redactUrlCredentials("http://user:secret@proxy.example.test:8080"))
                .isEqualTo("http://***@proxy.example.test:8080");
        assertThat(ModelHttpClients.redactUrlCredentials("user:secret@proxy.example.test:8080"))
                .isEqualTo("***@proxy.example.test:8080");
    }

    @Test
    void redactUrlCredentials_noUserInfo_returnsUrlUnchanged() {
        assertThat(ModelHttpClients.redactUrlCredentials(null)).isNull();
        assertThat(ModelHttpClients.redactUrlCredentials("http://proxy.example.test:8080"))
                .isEqualTo("http://proxy.example.test:8080");
        assertThat(ModelHttpClients.redactUrlCredentials("socks5://proxy.example.test"))
                .isEqualTo("socks5://proxy.example.test");
    }

    @Test
    void redactUrlCredentials_atSignInPath_keepsUrlUnchanged() {
        assertThat(ModelHttpClients.redactUrlCredentials("http://proxy.example.test/a@b?x=@y"))
                .isEqualTo("http://proxy.example.test/a@b?x=@y");
    }

    @Test
    void redactUrlCredentials_passwordContainingRawAt_masksWholePassword() {
        assertThat(ModelHttpClients.redactUrlCredentials("http://user:p@ss@proxy.example.test:8080"))
                .isEqualTo("http://***@proxy.example.test:8080");
        assertThat(ModelHttpClients.redactUrlCredentials("http://user:p@ssw0rd@proxy.example.test:8080"))
                .isEqualTo("http://***@proxy.example.test:8080");
    }

    @Test
    void redactUrlCredentials_atSignInQuery_masksAuthorityOnly() {
        assertThat(ModelHttpClients.redactUrlCredentials("http://user:secret@host.example.test/path?x=@y"))
                .isEqualTo("http://***@host.example.test/path?x=@y");
    }

    @Test
    void build_identicalConfig_sharesClientAcrossInstances() {
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
    void proxyKeyFor_explicitPortProxy_mergesAcrossPortModes() {
        String explicitOnly = ModelHttpClients.proxyKeyFor(ModelHttpClients.resolveProxyAddress(
                "http://proxy.example.test:8080", ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY).orElseThrow());
        String defaultMode = ModelHttpClients.proxyKeyFor(ModelHttpClients.resolveProxyAddress(
                "http://user:secret@proxy.example.test:8080/extra",
                ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING).orElseThrow());

        assertThat(explicitOnly).isEqualTo(defaultMode).isEqualTo("proxy:proxy.example.test:8080");
    }

    @Test
    void proxyKeyFor_portlessProxy_resolvesOnlyInDefaultPortMode() {
        InetSocketAddress defaulted = ModelHttpClients.resolveProxyAddress(
                "http://proxy.example.test", ModelHttpClients.ProxyPortMode.DEFAULT_PORT_WHEN_MISSING)
                .orElseThrow();

        assertThat(ModelHttpClients.proxyKeyFor(defaulted)).isEqualTo("proxy:proxy.example.test:80");
        assertThat(ModelHttpClients.resolveProxyAddress(
                "http://proxy.example.test", ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY)).isEmpty();
    }

    @Test
    void build_differentTargetUrl_createsSeparateClient() {
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
    void build_differentConnectTimeout_createsSeparateClient() {
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
    void build_differentHttpVersion_createsSeparateClient() {
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
    void build_capacityExhausted_skipsSharing() {
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
    void build_capZero_disablesSharing() {
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
    void build_invalidCapProperty_usesDefaultCap() {
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
    void build_negativeCapProperty_usesDefaultCap() {
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
    void build_capacityFullAndKeyPresent_returnsCachedClient() {
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
    void warnCacheFullOnce_perKey_firesOnlyOnce() {
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
    void build_concurrentMisses_neverExceedCapacity() throws Exception {
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
    void build_sameKeyConcurrentBuilds_shareOneInstance() throws Exception {
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
    void build_hostSpellingVariants_shareClient() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient upperCaseHost = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://API.EXAMPLE.TEST/v1")
                    .build();
            HttpClient trailingDotHost = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test./v1")
                    .build();

            assertThat(upperCaseHost).isSameAs(first);
            assertThat(trailingDotHost).isSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void build_pathCaseAndInvalidMultiDotHost_keptDistinct() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient upperCasePath = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/V1")
                    .build();
            HttpClient doubleTrailingDotHost = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test../v1")
                    .build();
            HttpClient explicitDefaultPort = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test:443/v1")
                    .build();

            assertThat(upperCasePath).isNotSameAs(first);
            assertThat(doubleTrailingDotHost).isNotSameAs(first);
            assertThat(explicitDefaultPort).isNotSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void build_ipv6LiteralCaseVariants_shareClient() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient first = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "http://[2001:db8::1]:8080/v1")
                    .build();
            HttpClient upperCaseHex = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "http://[2001:DB8::1]:8080/v1")
                    .build();

            assertThat(upperCaseHex).isSameAs(first);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void proxyKeyFor_hostSpellingVariants_merge() {
        String upperCase = ModelHttpClients.proxyKeyFor(ModelHttpClients.resolveProxyAddress(
                "http://PROXY.EXAMPLE.TEST:8080", ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY).orElseThrow());
        String trailingDot = ModelHttpClients.proxyKeyFor(ModelHttpClients.resolveProxyAddress(
                "http://proxy.example.test.:8080", ModelHttpClients.ProxyPortMode.EXPLICIT_PORT_ONLY).orElseThrow());

        assertThat(upperCase).isEqualTo(trailingDot).isEqualTo("proxy:proxy.example.test:8080");
    }

    @Test
    void build_differentSslConfig_createsSeparateClient() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient noTlsConfig = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            HttpClient verifyOn = ModelHttpClients
                    .builder(sslClientConfig(true), "https://api.example.test/v1")
                    .withSsl()
                    .build();
            HttpClient verifyOff = ModelHttpClients
                    .builder(sslClientConfig(false), "https://api.example.test/v1")
                    .withSsl()
                    .build();

            assertThat(verifyOn).isNotSameAs(noTlsConfig);
            assertThat(verifyOff).isNotSameAs(verifyOn);
            assertThat(verifyOff).isNotSameAs(noTlsConfig);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void build_differentEffectiveProxy_createsSeparateClient() throws Exception {
        ModelHttpClients.clearSharedClientsForTesting();
        setEnvReader(name -> "https_proxy".equalsIgnoreCase(name)
                ? "http://proxy-a.example.test:8080" : null);
        try {
            HttpClient viaProxyA = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .withProxy()
                    .build();
            setEnvReader(name -> "https_proxy".equalsIgnoreCase(name)
                    ? "http://proxy-b.example.test:8080" : null);
            HttpClient viaProxyB = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .withProxy()
                    .build();
            setEnvReader(name -> null);
            HttpClient direct = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .withProxy()
                    .build();

            assertThat(viaProxyB).isNotSameAs(viaProxyA);
            assertThat(direct).isNotSameAs(viaProxyA);
            assertThat(direct).isNotSameAs(viaProxyB);
        } finally {
            resetEnvReader();
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void build_trailingSlashAndWhitespaceVariants_shareClient() {
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
    void sweepIdleSharedClients_staleEntryRetired_freesCapacityForNewKeys() {
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
    void sweepIdleSharedClients_idleSecondsZero_skipsRetirement() {
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
    void sweepIdleSharedClients_freshEntries_surviveSweep() {
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
    void sweepIdleSharedClients_thresholdBoundary_retainsThroughExactThreshold() {
        long thresholdNanos = TimeUnit.SECONDS.toNanos(1);

        assertIdleEntryRetention(thresholdNanos - 1L, true);
        assertIdleEntryRetention(thresholdNanos, true);
        assertIdleEntryRetention(thresholdNanos + 1L, false);
    }

    @Test
    void warnCacheFullOnce_keyAdmittedLater_clearsLedgerEntry() {
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
    void cacheFullWarnedKeys_repeatedOverflow_boundedAtMax128() {
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
    void evictIfDead_selectorClosedError_removesSharedClient() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            ModelHttpClients.DeadClientVerdict verdict = ModelHttpClients.evictIfDead(
                    shared, new IOException("selector manager closed"));

            assertThat(verdict).isEqualTo(DEAD_BY_OFFICIAL_SIGNAL);
            HttpClient rebuilt = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(rebuilt).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_nullClientOrError_returnsAlive() {
        assertThat(ModelHttpClients.evictIfDead(null, new IOException("selector manager closed")))
                .isEqualTo(ALIVE);
        assertThat(ModelHttpClients.evictIfDead(HttpClient.newHttpClient(), null)).isEqualTo(ALIVE);
    }

    @Test
    void evictIfDead_hintThroughCauseChain_judgesOfficialSignal() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            ModelHttpClients.DeadClientVerdict verdict = ModelHttpClients.evictIfDead(
                    shared, new IOException("send failed", new IOException("selector manager closed")));

            assertThat(verdict).isEqualTo(DEAD_BY_OFFICIAL_SIGNAL);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_caseInsensitiveSelectorMessage_judgesOfficialSignal() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            ModelHttpClients.DeadClientVerdict verdict = ModelHttpClients.evictIfDead(
                    shared, new IOException("Selector manager closed"));

            assertThat(verdict).isEqualTo(DEAD_BY_OFFICIAL_SIGNAL);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_messageEmbeddedInLongerText_returnsAlive() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            ModelHttpClients.DeadClientVerdict verdict = ModelHttpClients.evictIfDead(
                    shared, new IOException("request failed: selector manager closed"));

            assertThat(verdict).isEqualTo(ALIVE);
            HttpClient sameKeyClient = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(sameKeyClient).isSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_unrelatedTransportError_returnsAlive() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            ModelHttpClients.DeadClientVerdict verdict = ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"));

            assertThat(verdict).isEqualTo(ALIVE);
            HttpClient sameKeyClient = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(sameKeyClient).isSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_clientNotHeldByCache_reportsDeadWithoutEviction() {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "0");
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient unshared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            ModelHttpClients.DeadClientVerdict verdict = ModelHttpClients.evictIfDead(
                    unshared, new IOException("selector manager closed"));

            assertThat(verdict).isEqualTo(DEAD_BY_OFFICIAL_SIGNAL);
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_repeatedCallsForSameClient_reportsDeadnessEveryTime() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("selector manager closed"))).isEqualTo(DEAD_BY_OFFICIAL_SIGNAL);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("selector manager closed"))).isEqualTo(DEAD_BY_OFFICIAL_SIGNAL);

            HttpClient rebuilt = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(rebuilt).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_repeatedUnknownFailures_recyclesClient() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection timed out"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(DEAD_BY_FAILURE_THRESHOLD);

            HttpClient recycled = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(recycled).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_sendSuccess_resetsFailureCount() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection timed out"))).isEqualTo(ALIVE);
            ModelHttpClients.noteSendSuccess(shared);

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection timed out"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer")))
                    .isEqualTo(DEAD_BY_FAILURE_THRESHOLD);

            HttpClient recycled = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();
            assertThat(recycled).isNotSameAs(shared);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_laggingHolder_getsRebuildSignalFromRegistry() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection timed out"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer")))
                    .isEqualTo(DEAD_BY_FAILURE_THRESHOLD);

            // The entry is evicted; a lagging holder still referencing the dead instance keeps
            // receiving the rebuild signal through the death registry.
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer")))
                    .isEqualTo(DEAD_BY_FAILURE_THRESHOLD);

            // A success un-registers the client: a misjudged healthy client heals itself.
            ModelHttpClients.noteSendSuccess(shared);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(ALIVE);
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void evictIfDead_thresholdEviction_zeroesPendingSendFailures() {
        ModelHttpClients.clearSharedClientsForTesting();
        try {
            HttpClient shared = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://api.example.test/v1")
                    .build();

            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection timed out"))).isEqualTo(ALIVE);
            assertThat(ModelHttpClients.evictIfDead(
                    shared, new IOException("Connection reset by peer")))
                    .isEqualTo(DEAD_BY_FAILURE_THRESHOLD);

            // The evicted entry's counts must not leak into the pool-wide pending counter:
            // a leaked count would permanently defeat the zero-scan hot path (every later
            // success would rescan the whole pool — a lock-free CHM iteration plus one
            // short mutex remove at the registry tail).
            assertThat(ModelHttpClients.pendingSendFailuresForTesting()).isZero();
        } finally {
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void eventLogAllowed_perEventAndIdentity_throttlesUntilCooldown() {
        ModelHttpClients.clearSharedClientsForTesting();
        AtomicLong clock = new AtomicLong(1_000_000L);
        ModelHttpClients.setNanoClockForTesting(clock::get);
        try {
            assertThat(ModelHttpClients.eventLogAllowed("create:http://a.example.test")).isTrue();
            assertThat(ModelHttpClients.eventLogAllowed("create:http://a.example.test")).isFalse();
            assertThat(ModelHttpClients.eventLogAllowed("evict:http://a.example.test")).isTrue();
            assertThat(ModelHttpClients.eventLogAllowed("create:http://b.example.test")).isTrue();
            assertThat(ModelHttpClients.eventLogAllowed("evict:http://a.example.test")).isFalse();

            clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
            assertThat(ModelHttpClients.eventLogAllowed("create:http://a.example.test")).isTrue();
            assertThat(ModelHttpClients.eventLogAllowed("evict:http://a.example.test")).isTrue();
        } finally {
            ModelHttpClients.setNanoClockForTesting(System::nanoTime);
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    @Test
    void retireLogAllowed_globally_throttlesUntilCooldown() {
        ModelHttpClients.clearSharedClientsForTesting();
        AtomicLong clock = new AtomicLong(1_000_000L);
        ModelHttpClients.setNanoClockForTesting(clock::get);
        try {
            assertThat(ModelHttpClients.retireLogAllowed()).isTrue();
            assertThat(ModelHttpClients.retireLogAllowed()).isFalse();

            clock.addAndGet(TimeUnit.SECONDS.toNanos(60));
            assertThat(ModelHttpClients.retireLogAllowed()).isTrue();
        } finally {
            ModelHttpClients.setNanoClockForTesting(System::nanoTime);
            ModelHttpClients.clearSharedClientsForTesting();
        }
    }

    private static void assertIdleEntryRetention(long ageNanos, boolean shouldRetain) {
        System.setProperty("openjiuwen.llm.http.max-shared-clients", "1");
        System.setProperty("openjiuwen.llm.http.shared-client-idle-seconds", "1");
        ModelHttpClients.clearSharedClientsForTesting();
        AtomicLong clock = new AtomicLong(TimeUnit.SECONDS.toNanos(10));
        ModelHttpClients.setNanoClockForTesting(clock::get);
        try {
            HttpClient original = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://idle.example.test/v1")
                    .build();
            clock.addAndGet(ageNanos);
            ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://sweep.example.test/v1")
                    .build();

            HttpClient reloaded = ModelHttpClients
                    .builder(clientConfig(ModelHttpVersion.HTTP_1_1), "https://idle.example.test/v1")
                    .build();

            if (shouldRetain) {
                assertThat(reloaded).isSameAs(original);
            } else {
                assertThat(reloaded).isNotSameAs(original);
            }
        } finally {
            System.clearProperty("openjiuwen.llm.http.max-shared-clients");
            System.clearProperty("openjiuwen.llm.http.shared-client-idle-seconds");
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

    private static ModelClientConfig sslClientConfig(boolean verifySsl) {
        return ModelClientConfig.builder()
                .clientProvider(ProviderType.OPEN_AI)
                .apiBase("https://api.example.test/v1")
                .httpVersion(ModelHttpVersion.HTTP_1_1)
                .timeout(9.0D)
                .verifySsl(verifySsl)
                .build();
    }

    private static void setEnvReader(Function<String, String> reader) throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("setEnvReaderForTests", Function.class);
        method.setAccessible(true);
        method.invoke(null, reader);
    }

    private static void resetEnvReader() throws Exception {
        Method method = UrlUtils.class.getDeclaredMethod("resetEnvReaderForTests");
        method.setAccessible(true);
        method.invoke(null);
    }
}
