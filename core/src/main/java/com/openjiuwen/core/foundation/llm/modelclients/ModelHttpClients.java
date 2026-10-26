/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Shared {@code HttpClient} cache for model clients. Model clients are short-lived — some
 * families build one per request — so without sharing every task would spawn a dedicated
 * HttpClient with its own connection pool and selector thread. Builders whose connection
 * identity (target URL, HTTP version, connect timeout, TLS material, proxy) matches share
 * one pooled instance, subject to a configurable capacity with overflow degradation, idle
 * retirement, and eviction of clients whose selector manager has died. Shared clients stay
 * stateless: no CookieHandler or Authenticator is ever configured, and credentials ride
 * per request.
 *
 * @since 2026-10-19
 */
final class ModelHttpClients {
    private static final String MAX_SHARED_CLIENTS_PROPERTY =
            "openjiuwen.llm.http.max-shared-clients";

    // 0 disables sharing; invalid values fall back to this default. Matches the connector
    // pool cap precedent (ConnectorPoolManager default) and Python's per-client httpx limits.
    private static final int DEFAULT_SHARED_CLIENTS = 100;

    private static final String SHARED_CLIENT_IDLE_SECONDS_PROPERTY =
            "openjiuwen.llm.http.shared-client-idle-seconds";

    // Entry age refreshes only on cache hits, not during use, so an entry may retire while its
    // client is still serving (in-flight exchanges complete; the next admission rebuilds);
    // 0 disables idle retirement, invalid values use this default.
    private static final long DEFAULT_SHARED_CLIENT_IDLE_SECONDS = 1800L;

    // Some client families build a model client per request, so invalid-value warnings must not
    // repeat on every build; one warning per JVM is enough to surface the misconfiguration.
    private static volatile boolean hasWarnedInvalidCap;

    private static volatile boolean hasWarnedInvalidIdleSeconds;

    // Agents build short-lived model clients, so without sharing every task would spawn a
    // dedicated HttpClient with its own connection pool and selector thread.
    // Tenants share one entry per connection identity, which is safe only while the client
    // stays stateless: never configure CookieHandler, Authenticator, or any other client-level
    // credential/session state on the shared builder; credentials must ride per request.
    private static final ConcurrentHashMap<String, SharedClient> SHARED_CLIENTS = new ConcurrentHashMap<>();

    private static final Set<String> CACHE_FULL_WARNED_KEYS = ConcurrentHashMap.newKeySet();

    // Bounds the cache-full warning ledger so pathological key churn cannot grow it without bound.
    private static final int MAX_CACHE_FULL_WARNED_KEYS = 128;

    private static final Object INSERT_LOCK = new Object();

    // A JDK HttpClient stays permanently unusable once its selector manager is shut down, so a
    // shared client that died mid-flight must be dropped from the cache or it pins the key forever.
    // The JDK builds this message as a bare literal, so case-insensitive equality on any
    // cause-chain node tolerates capitalization drift while staying immune to the same text
    // embedded in server-controlled error bodies.
    private static final String SELECTOR_CLOSED_MESSAGE = "selector manager closed";

    // A dead client whose failure wording the matcher does not recognize would stay pinned in
    // the cache forever (hits keep refreshing its idle clock), so a cached client whose sends
    // keep failing is recycled after this many escaped IOExceptions. The cost of a false
    // positive is one extra client build; eviction resets the count.
    private static final int EVICTION_FAILURE_THRESHOLD = 3;

    // Seam for idle-retirement tests: swapped for a steppable clock so tests advance time
    // deterministically instead of sleeping; production always uses the real nanoTime.
    private static volatile LongSupplier nanoClock = System::nanoTime;

    // Compiled once: normalizeTargetUrl runs on every model client construction.
    private static final Pattern TRAILING_SLASH_PATTERN = Pattern.compile("/+$");

    private final ModelClientConfig clientConfig;
    private final String targetUrl;
    private final HttpClient.Builder builder;
    private long effectiveConnectTimeoutMillis;
    private String sslKey;
    private String proxyKey;
    private HttpClient.Version version;

    private ModelHttpClients(ModelClientConfig clientConfig, String targetUrl) {
        this.clientConfig = clientConfig;
        // Key components must be canonical or spelling variants fragment the shared cache.
        this.targetUrl = normalizeTargetUrl(targetUrl);
        // Connect timeout is tiered independently so refused LLM hosts fail fast.
        Duration connectTimeout = connectTimeoutDuration(clientConfig);
        this.effectiveConnectTimeoutMillis = connectTimeout.toMillis();
        this.builder = HttpClient.newBuilder()
                .connectTimeout(connectTimeout);
        ModelTimeoutResolver.warnIfLegacyTimeoutExplicit(clientConfig);
    }

    static ModelHttpClients builder(ModelClientConfig clientConfig, String targetUrl) {
        return new ModelHttpClients(clientConfig, targetUrl);
    }

    ModelHttpClients withSsl() {
        if (clientConfig != null) {
            SslUtils.configureHttpClientSsl(
                    builder,
                    targetUrl,
                    clientConfig.isVerifySsl(),
                    clientConfig.getSslCert());
            sslKey = "ssl:" + clientConfig.isVerifySsl() + ':' + clientConfig.getSslCert();
        }
        return this;
    }

    ModelHttpClients withProxy() {
        return withProxy(ProxyPortMode.DEFAULT_PORT_WHEN_MISSING);
    }

    ModelHttpClients withExplicitPortProxy() {
        return withProxy(ProxyPortMode.EXPLICIT_PORT_ONLY);
    }

    private ModelHttpClients withProxy(ProxyPortMode portMode) {
        String proxyUrl = UrlUtils.getGlobalProxyUrl(targetUrl);
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return this;
        }
        ProxySelector proxySelector = proxySelector(proxyUrl, portMode);
        if (proxySelector != null) {
            builder.proxy(proxySelector);
            proxyKey = "proxy:" + redactUrlCredentials(proxyUrl) + ':' + portMode.name();
        }
        return this;
    }

    HttpClient build() {
        applyHttpVersion();
        return sharedClient();
    }

    private HttpClient sharedClient() {
        int maxSharedClients = maxSharedClients();
        if (maxSharedClients <= 0) {
            return builder.build();
        }
        String key = cacheKey();
        SharedClient cached = SHARED_CLIENTS.get(key);
        if (cached != null) {
            cached.touch();
            return cached.client;
        }
        // Serializing the size check with the insert keeps the cap a hard bound under races.
        synchronized (INSERT_LOCK) {
            cached = SHARED_CLIENTS.get(key);
            if (cached != null) {
                cached.touch();
                return cached.client;
            }
            // Sweep first so retired entries free capacity exactly when a new key needs it.
            sweepIdleSharedClients();
            if (SHARED_CLIENTS.size() >= maxSharedClients) {
                warnCacheFullOnce(key, maxSharedClients);
                return builder.build();
            }
            HttpClient admitted = SHARED_CLIENTS.computeIfAbsent(key, this::createSharedClient).client;
            // A later admission means the earlier cache-full warning no longer describes reality.
            CACHE_FULL_WARNED_KEYS.remove(key);
            return admitted;
        }
    }

    private void warnCacheFullOnce(String key, int maxSharedClients) {
        // Called only under INSERT_LOCK, so the size check is an exact bound, not a race guess.
        if (CACHE_FULL_WARNED_KEYS.size() >= MAX_CACHE_FULL_WARNED_KEYS) {
            return;
        }
        if (CACHE_FULL_WARNED_KEYS.add(key)) {
            Loggers.LLM.warning("Shared model HttpClient cache is full ({}/{}); building an unshared client for {}",
                    SHARED_CLIENTS.size(), maxSharedClients, redactUrlCredentials(targetUrl));
        }
    }

    private SharedClient createSharedClient(String key) {
        HttpClient created = builder.build();
        Loggers.LLM.info("Shared model HttpClient created for {} (client id={}, selector={})",
                redactUrlCredentials(key), System.identityHashCode(created), selectorDescription(created));
        return new SharedClient(created);
    }

    /**
     * Reports whether {@code error} proves {@code client} is permanently unusable and drops
     * the client from the shared cache when it is still cached. A JDK HttpClient whose
     * selector manager has died never recovers, so callers treat a {@code true} result as
     * the signal to rebuild their client before the next call.
     *
     * <p>Deadness is judged by an exact case-insensitive match of the JDK's bare
     * selector-closed message anywhere on the cause chain, or by the failure threshold: a
     * cached client whose sends have escaped as IOExceptions three times is recycled even
     * when the wording is unknown. The return value is deadness, not eviction bookkeeping:
     * a dead client that is no longer cached (retired or overflowed) still returns
     * {@code true} so its holder rebuilds too.</p>
     */
    static boolean evictIfDead(HttpClient client, IOException error) {
        if (client == null || error == null) {
            return false;
        }
        if (!indicatesClosedSelector(error) && !hasReachedFailureThreshold(client)) {
            return false;
        }
        boolean isEvicted = SHARED_CLIENTS.values().removeIf(shared -> shared.client == client);
        if (isEvicted) {
            Loggers.LLM.warning("Shared model HttpClient evicted as unusable (client id={}); "
                    + "the next call will build a fresh client", System.identityHashCode(client));
        }
        return true;
    }

    // Records another send failure on the cached entry and reports whether the recycle
    // threshold is reached; a client not held by the cache cannot accumulate and relies on
    // the message match alone.
    private static boolean hasReachedFailureThreshold(HttpClient client) {
        for (SharedClient shared : SHARED_CLIENTS.values()) {
            if (shared.client == client) {
                return shared.recordSendFailure() >= EVICTION_FAILURE_THRESHOLD;
            }
        }
        return false;
    }

    private static boolean indicatesClosedSelector(IOException error) {
        Throwable current = error;
        while (current != null) {
            if (SELECTOR_CLOSED_MESSAGE.equalsIgnoreCase(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String selectorDescription(HttpClient client) {
        try {
            Field implField = client.getClass().getDeclaredField("impl");
            implField.setAccessible(true);
            Object impl = implField.get(client);
            if (impl == null) {
                return "unavailable (null client impl)";
            }
            Field selectorField = impl.getClass().getDeclaredField("selmgr");
            selectorField.setAccessible(true);
            Object selectorManager = selectorField.get(impl);
            if (selectorManager instanceof Thread selectorThread) {
                return selectorThread.getName() + " (alive=" + selectorThread.isAlive() + ")";
            }
            return String.valueOf(selectorManager);
        } catch (ReflectiveOperationException | InaccessibleObjectException | SecurityException error) {
            return "unavailable (" + error.getClass().getSimpleName() + ")";
        }
    }

    private String cacheKey() {
        return targetUrl + '\n'
                + version + '\n'
                + effectiveConnectTimeoutMillis + '\n'
                + sslKey + '\n'
                + proxyKey;
    }

    private static String normalizeTargetUrl(String targetUrl) {
        if (targetUrl == null) {
            return null;
        }
        return TRAILING_SLASH_PATTERN.matcher(targetUrl.strip()).replaceAll("");
    }

    private static int maxSharedClients() {
        String raw = System.getProperty(MAX_SHARED_CLIENTS_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_SHARED_CLIENTS;
        }
        try {
            int configured = Integer.parseInt(raw.trim());
            if (configured >= 0) {
                return configured;
            }
        } catch (NumberFormatException error) {
            // Unparsable values fall through to the default, same as out-of-range ones.
        }
        if (!hasWarnedInvalidCap) {
            hasWarnedInvalidCap = true;
            Loggers.LLM.warning("Invalid {} value '{}'; using default {}",
                    MAX_SHARED_CLIENTS_PROPERTY, raw, DEFAULT_SHARED_CLIENTS);
        }
        return DEFAULT_SHARED_CLIENTS;
    }

    // Retirement drops the cache's reference; the JDK reclaims the client's selector thread and
    // connection pool only once no holder still references it (JDK 17 selectors have no
    // idle-exit timer). Sweeping on the miss path is enough because a lingering idle entry only
    // matters once a new key needs its slot.
    private static void sweepIdleSharedClients() {
        long idleNanos = sharedClientIdleNanos();
        if (idleNanos <= 0L) {
            return;
        }
        int sizeBeforeSweep = SHARED_CLIENTS.size();
        long now = nanoClock.getAsLong();
        SHARED_CLIENTS.entrySet()
                .removeIf(entry -> now - entry.getValue().lastAccessNanos >= idleNanos);
        int retiredCount = sizeBeforeSweep - SHARED_CLIENTS.size();
        if (retiredCount > 0) {
            Loggers.LLM.info("Retired {} idle shared model HttpClients (idle threshold {}s)",
                    retiredCount, TimeUnit.NANOSECONDS.toSeconds(idleNanos));
        }
    }

    private static long sharedClientIdleNanos() {
        String raw = System.getProperty(SHARED_CLIENT_IDLE_SECONDS_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return TimeUnit.SECONDS.toNanos(DEFAULT_SHARED_CLIENT_IDLE_SECONDS);
        }
        try {
            long configuredSeconds = Long.parseLong(raw.trim());
            if (configuredSeconds == 0L) {
                // 0 explicitly disables idle retirement.
                return 0L;
            }
            if (configuredSeconds > 0L) {
                // TimeUnit saturates instead of overflowing, so absurd thresholds simply never fire.
                return TimeUnit.SECONDS.toNanos(configuredSeconds);
            }
        } catch (NumberFormatException error) {
            // Unparsable values fall through to the default, same as negative ones.
        }
        if (!hasWarnedInvalidIdleSeconds) {
            hasWarnedInvalidIdleSeconds = true;
            Loggers.LLM.warning("Invalid {} value '{}'; using default {} seconds",
                    SHARED_CLIENT_IDLE_SECONDS_PROPERTY, raw, DEFAULT_SHARED_CLIENT_IDLE_SECONDS);
        }
        return TimeUnit.SECONDS.toNanos(DEFAULT_SHARED_CLIENT_IDLE_SECONDS);
    }

    static void clearSharedClientsForTesting() {
        SHARED_CLIENTS.clear();
        CACHE_FULL_WARNED_KEYS.clear();
    }

    static void setNanoClockForTesting(LongSupplier clock) {
        nanoClock = Objects.requireNonNull(clock);
    }

    static Set<String> cacheFullWarnedKeysForTesting() {
        return Set.copyOf(CACHE_FULL_WARNED_KEYS);
    }

    static int sharedClientCountForTesting() {
        return SHARED_CLIENTS.size();
    }

    private void applyHttpVersion() {
        if (clientConfig != null && clientConfig.getHttpVersion() != null) {
            version = clientConfig.getHttpVersion().toJdkVersion();
        } else {
            // Unconfigured clients use HTTP/1.1 so HTTP/1.1-only servers keep working.
            version = HttpClient.Version.HTTP_1_1;
        }
        builder.version(version);
    }

    // Cache keys and the shared-client INFO log must not leak credentials embedded per the
    // http://user:pass@host convention. The ProxySelector itself keeps parsing the raw proxy URL
    // because it only consumes host and port.
    static String redactUrlCredentials(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int schemeEnd = url.indexOf("://");
        int authorityStart = schemeEnd < 0 ? 0 : schemeEnd + 3;
        int authorityEnd = url.length();
        for (int i = authorityStart; i < authorityEnd; i++) {
            char current = url.charAt(i);
            if (current == '/' || current == '?' || current == '#') {
                authorityEnd = i;
                break;
            }
        }
        int userInfoSeparator = url.indexOf('@', authorityStart);
        if (userInfoSeparator < 0 || userInfoSeparator >= authorityEnd) {
            return url;
        }
        return url.substring(0, authorityStart) + "***@"
                + url.substring(userInfoSeparator + 1);
    }

    static ProxySelector proxySelector(String proxyUrl, ProxyPortMode portMode) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return null;
        }
        URI proxyUri = URI.create(proxyUrl);
        String host = proxyUri.getHost();
        if (host == null || host.isBlank()) {
            return null;
        }
        int port = proxyUri.getPort();
        if (port <= 0 && portMode == ProxyPortMode.EXPLICIT_PORT_ONLY) {
            return null;
        }
        int resolvedPort = port > 0 ? port : defaultProxyPort(proxyUri);
        return ProxySelector.of(new InetSocketAddress(host, resolvedPort));
    }

    private static int defaultProxyPort(URI proxyUri) {
        String scheme = proxyUri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            return 443;
        }
        return 80;
    }

    /**
     * Connect timeout is resolved by the tiered resolver (config field, then system property,
     * then default 10s) so a refused LLM host fails fast; response-header wait and stream idle
     * timeouts live elsewhere.
     */
    private static Duration connectTimeoutDuration(ModelClientConfig clientConfig) {
        return ModelTimeoutResolver.toDuration(
                ModelTimeoutResolver.resolveConnectTimeoutSeconds(clientConfig));
    }

    // Cache value wrapper: pairs the shared client with a last-access clock so idle entries can
    // be retired without touching the client itself.
    private static final class SharedClient {
        private final HttpClient client;
        private final AtomicInteger sendFailures = new AtomicInteger();
        private volatile long lastAccessNanos;

        private SharedClient(HttpClient client) {
            this.client = client;
            this.lastAccessNanos = nanoClock.getAsLong();
        }

        private void touch() {
            this.lastAccessNanos = nanoClock.getAsLong();
        }

        private int recordSendFailure() {
            return sendFailures.incrementAndGet();
        }
    }

    enum ProxyPortMode {
        EXPLICIT_PORT_ONLY,
        DEFAULT_PORT_WHEN_MISSING
    }
}
