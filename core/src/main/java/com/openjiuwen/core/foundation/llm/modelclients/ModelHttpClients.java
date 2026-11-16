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
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Shared {@code HttpClient} cache for model clients. Model clients are short-lived — some
 * families build one per request — so without sharing every task would spawn a dedicated
 * HttpClient with its own connection pool and selector thread. Builders whose connection
 * identity (target URL, HTTP version, connect timeout, TLS material, proxy) matches share
 * one pooled instance, subject to a configurable capacity with overflow degradation, idle
 * retirement, and eviction of clients whose selector manager has died or whose unrecognized
 * send failures have accumulated three times in a row (a healthy send resets the count;
 * threshold-judged clients stay weakly registered so lagging holders still get the rebuild
 * signal). Shared clients stay
 * stateless: no CookieHandler or Authenticator is ever configured, and credentials ride
 * per request.
 *
 * @since 2026-11-04
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
    // compareAndSet makes the once-guarantee race-free (a lost race could only double-log).
    private static final AtomicBoolean HAS_WARNED_INVALID_CAP = new AtomicBoolean();

    private static final AtomicBoolean HAS_WARNED_INVALID_IDLE_SECONDS = new AtomicBoolean();

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
    // keep failing is recycled after this many escaped IOExceptions. The count resets on a
    // successful send, so only consecutive failures accumulate. The cost of a false positive
    // is one extra client build; eviction resets the count.
    private static final int EVICTION_FAILURE_THRESHOLD = 3;

    // Clients judged dead by the failure threshold stay weakly registered so lagging holders
    // (per-holder families still referencing an already-evicted client) keep receiving the
    // rebuild signal on their own failed sends. Entries vanish with GC once no holder
    // references the client anymore; a successful send un-registers its client so a
    // misjudged healthy client heals itself. Unlike the warn ledgers (which reject new
    // keys at the cap), hitting this cap clears the whole table: entries still referenced
    // by lagging holders lose the rebuild signal and surface explicitly on every call —
    // accepted because memory stays GC-bounded and the trigger is harsh (>=128 deaths
    // between GCs).
    private static final Map<HttpClient, Void> DEAD_CLIENT_REGISTRY =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static final int MAX_DEAD_CLIENT_REGISTRY = 128;

    // initCause rejects only a direct self-cause — two-exception cycles are constructible
    // in pure JDK (JDK-6962571), so this depth ceiling is required protection, aligned with
    // the error classifier's own ceiling.
    private static final int MAX_CAUSE_DEPTH = 32;

    // Pool-wide signal that some entry has pending failure counts. Successful sends consult
    // this first: zero means no reset work exists and the pool scan is skipped entirely. The
    // counter is conserved under the entry's monitor — eviction, idle retirement and the
    // test-seam swap drain a leaving entry's residual counts and mark it retired, so a
    // recorder racing the removal declines instead of orphaning counts — and churn cannot
    // permanently defeat the skip.
    private static final AtomicInteger PENDING_SEND_FAILURES = new AtomicInteger();


    // Seam for idle-retirement tests: swapped for a steppable clock so tests advance time
    // deterministically instead of sleeping; production always uses the real nanoTime.
    private static volatile LongSupplier nanoClock = System::nanoTime;

    // Endpoint outages can drive evict/rebuild churn — a failing shared client is recycled,
    // the next admission rebuilds, and the WARN/INFO pair would repeat per cycle: the same
    // log amplification the cache-full ledger guards against. One line per event per
    // identity per cooldown window keeps the signal visible without the flood; only logs
    // are throttled, never the eviction, admission, or retirement itself.
    private static final long EVENT_LOG_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(60);

    // Last-logged timestamps per "<event>:<identity>" so creation INFO and eviction WARN
    // throttle independently of each other; expired entries leave only when the soft cap
    // trips (pruning happens at admission, not on a timer), so sustained size stays bounded
    // by recent identities.
    private static final ConcurrentHashMap<String, Long> EVENT_LOG_AT = new ConcurrentHashMap<>();

    private static final int MAX_EVENT_LOG_KEYS = 512;

    // The retirement event is aggregate (no single identity), so it throttles globally;
    // 0 doubles as the "never logged" sentinel.
    private static final AtomicLong LAST_RETIRE_LOG_AT = new AtomicLong();

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
        this.targetUrl = targetUrl == null ? null : normalizeTargetUrl(targetUrl);
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
        resolveProxyAddress(proxyUrl, portMode).ifPresent(proxyAddress -> {
            builder.proxy(ProxySelector.of(proxyAddress));
            proxyKey = proxyKeyFor(proxyAddress);
        });
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
        if (eventLogAllowed("create:" + key)) {
            Loggers.LLM.info("Shared model HttpClient created for {} (client id={}, selector={})",
                    redactUrlCredentials(key), System.identityHashCode(created), selectorDescription(created));
        }
        return new SharedClient(created);
    }

    /**
     * Reports whether {@code error} proves {@code client} is permanently unusable and drops
     * the client from the shared cache when it is still cached. A JDK HttpClient whose
     * selector manager has died never recovers, so callers treat any non-ALIVE verdict as
     * the signal to rebuild their client before the next call.
     *
     * <p>Deadness is judged by an exact case-insensitive match of the JDK's bare
     * selector-closed message anywhere on the cause chain, or by the failure threshold: a
     * cached client whose sends have escaped as IOExceptions three times in a row (a
     * successful send resets the count via {@link #noteSendSuccess}) is recycled even when
     * the wording is unknown. The return value is the deadness verdict, not eviction
     * bookkeeping: an uncached dead client returns a dead verdict so its holder rebuilds
     * too — the official signal without a pool lookup, and a threshold-judged client via
     * the death registry.</p>
     *
     * <p>The verdict grades the two death paths for two consumers: eviction WARN wording, and
     * death-registry bookkeeping — a threshold death registers the client so lagging holders
     * keep the rebuild signal, while an official-signal death does not (it is detectable
     * without a pool lookup). The caller-facing disposition is the same for both dead
     * verdicts: evict/register as applicable and surface the failure. No verdict licenses
     * an in-call resend: the official signal can also fire after the request bytes reached
     * the wire (JDK 17 SocketTube post-write check), so resending either verdict could
     * duplicate a call the server may already have billed.
     * The next call runs on a fresh client.</p>
     *
     * @param client the caller's HttpClient instance; may be an uncached exclusive client
     * @param error the send failure to judge; null failures never count as death
     * @return the deadness verdict — any non-ALIVE value means the caller must rebuild
     *         before the next send
     */
    static DeadClientVerdict evictIfDead(HttpClient client, IOException error) {
        if (client == null || error == null) {
            return DeadClientVerdict.ALIVE;
        }
        boolean isOfficialSignal = indicatesClosedSelector(error);
        if (!isOfficialSignal && !hasReachedFailureThreshold(client)) {
            return isRegisteredDeadClient(client)
                    ? DeadClientVerdict.DEAD_BY_FAILURE_THRESHOLD
                    : DeadClientVerdict.ALIVE;
        }
        Optional<String> evictedKey = sharedKeyOf(client);
        boolean isEvicted = false;
        Iterator<SharedClient> evictedIterator = SHARED_CLIENTS.values().iterator();
        while (evictedIterator.hasNext()) {
            SharedClient shared = evictedIterator.next();
            if (shared.client == client) {
                evictedIterator.remove();
                // A leaving entry returns its failure counts to the pool-wide pending
                // counter's zero baseline and marks itself retired, so a recorder racing
                // this removal inside the entry's monitor declines instead of orphaning
                // counts; without the compensation one eviction leaks its counts forever
                // and permanently defeats the zero-scan hot path.
                shared.retire();
                isEvicted = true;
            }
        }
        if (isEvicted) {
            warnEvicted(client, evictedKey.orElse("unknown"), isOfficialSignal);
        }
        if (!isOfficialSignal) {
            registerDeadClient(client);
        }
        return isOfficialSignal ? DeadClientVerdict.DEAD_BY_OFFICIAL_SIGNAL
                : DeadClientVerdict.DEAD_BY_FAILURE_THRESHOLD;
    }

    /**
     * Records that a send succeeded on the given client: the cached entry's failure count
     * resets (only consecutive failures accumulate toward the threshold) and a
     * threshold-judged client leaves the death registry (a misjudged healthy client heals
     * on its next success). Cheap on the hot path — a zero pending-failure count skips the
     * pool scan entirely.
     *
     * @param client the client whose send just succeeded; uncached clients are a no-op
     */
    static void noteSendSuccess(HttpClient client) {
        if (client == null
                || (PENDING_SEND_FAILURES.get() <= 0 && DEAD_CLIENT_REGISTRY.isEmpty())) {
            return;
        }
        for (SharedClient shared : SHARED_CLIENTS.values()) {
            if (shared.client == client) {
                shared.resetSendFailures();
                break;
            }
        }
        DEAD_CLIENT_REGISTRY.remove(client);
    }

    private static boolean isRegisteredDeadClient(HttpClient client) {
        return DEAD_CLIENT_REGISTRY.containsKey(client);
    }

    private static void registerDeadClient(HttpClient client) {
        Map<HttpClient, Void> registry = DEAD_CLIENT_REGISTRY;
        synchronized (registry) {
            if (registry.size() >= MAX_DEAD_CLIENT_REGISTRY) {
                registry.clear();
            }
            registry.put(client, null);
        }
    }

    // The verdict separates "dead beyond doubt" from "recycled by failure-count fallback"
    // for eviction WARN wording and registry bookkeeping: threshold deaths register the
    // client (lagging holders keep the rebuild signal) while official-signal deaths do
    // not. The caller-facing disposition is the same for both dead verdicts —
    // evict/register and surface the failure, with no in-call resend (the official
    // selector-closed signal can also fire after the request bytes reached the wire;
    // see evictIfDead).
    enum DeadClientVerdict {
        ALIVE,
        DEAD_BY_OFFICIAL_SIGNAL,
        DEAD_BY_FAILURE_THRESHOLD;

        boolean isDead() {
            return this != ALIVE;
        }
    }

    // Locates the cache key of the entry holding this client before removal so the eviction
    // warning can name the redacted identity; a dead instance is never re-admitted, so the
    // key found here belongs to the entry the removal loop below drops. Lookup and removal share
    // the same identity predicate, so a present entry always yields a key — the orElse
    // fallback below is unreachable defensive shape, never an expected path.
    private static Optional<String> sharedKeyOf(HttpClient client) {
        for (String key : SHARED_CLIENTS.keySet()) {
            SharedClient shared = SHARED_CLIENTS.get(key);
            if (shared != null && shared.client == client) {
                return Optional.of(key);
            }
        }
        return Optional.empty();
    }

    // The eviction reason must stay distinguishable in the message: the official signal is a
    // known stack death, while the threshold path marks unrecognized failures worth probing.
    private static void warnEvicted(HttpClient client, String evictedKey, boolean isOfficialSignal) {
        if (eventLogAllowed("evict:" + evictedKey)) {
            String reason = isOfficialSignal
                    ? "official selector-closed signal"
                    : "unrecognized send-failure threshold reached";
            Loggers.LLM.warning("Shared model HttpClient evicted as unusable for {} (client id={}; evicted by {}); "
                            + "the next call will build a fresh client",
                    redactUrlCredentials(evictedKey), System.identityHashCode(client), reason);
        }
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
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
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
        } catch (InaccessibleObjectException | SecurityException | IllegalArgumentException
                | ReflectiveOperationException error) {
            // JDK 16+ strong-encapsulation rejection, security-manager denial and malformed
            // reflective-call rejections, all caught specifically (never bare
            // RuntimeException); the description is purely diagnostic, so it must never
            // break the surrounding log line.
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

    // Expects a non-null target URL (the constructor guards the null corner before calling);
    // strip + trailing-slash removal + host-segment normalization run in sequence.
    private static String normalizeTargetUrl(String targetUrl) {
        return withNormalizedHost(TRAILING_SLASH_PATTERN.matcher(targetUrl.strip()).replaceAll(""));
    }

    // Host-segment normalization only: within the scheme://authority segment the host part
    // is lowercased and one trailing FQDN dot stripped, while the path and query keep
    // their case. Unparseable shapes (no scheme delimiter, empty or malformed host)
    // return unchanged so the conservative-split direction never widens merging to
    // malformed inputs.
    private static String withNormalizedHost(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd <= 0) {
            return url;
        }
        int authorityStart = schemeEnd + 3;
        int authorityEnd = authorityStart;
        while (authorityEnd < url.length()) {
            char current = url.charAt(authorityEnd);
            if (current == '/' || current == '?' || current == '#') {
                break;
            }
            authorityEnd++;
        }
        String authority = url.substring(authorityStart, authorityEnd);
        int hostStart = authority.lastIndexOf('@') + 1;
        if (hostStart >= authority.length()) {
            return url;
        }
        int hostEnd = authority.length();
        if (authority.charAt(hostStart) == '[') {
            int closing = authority.indexOf(']', hostStart);
            if (closing < 0) {
                return url;
            }
            hostEnd = closing + 1;
        } else {
            int portColon = authority.indexOf(':', hostStart);
            if (portColon >= 0) {
                hostEnd = portColon;
            }
        }
        String host = authority.substring(hostStart, hostEnd);
        if (host.isEmpty()) {
            return url;
        }
        String normalized = normalizeHost(host);
        if (normalized.equals(host)) {
            return url;
        }
        return url.substring(0, authorityStart + hostStart) + normalized
                + url.substring(authorityStart + hostEnd);
    }

    // DNS names are case-insensitive and an FQDN trailing dot is equivalent to the bare
    // name, so one trailing dot is stripped (a valid FQDN has at most one; multiple dots
    // are an invalid name left alone). Bracketed IPv6 literals only lowercase their hex
    // digits and never end in a dot.
    private static String normalizeHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
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
        if (HAS_WARNED_INVALID_CAP.compareAndSet(false, true)) {
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
        long now = nanoClock.getAsLong();
        int retiredCount = 0;
        Iterator<Map.Entry<String, SharedClient>> idleIterator = SHARED_CLIENTS.entrySet().iterator();
        while (idleIterator.hasNext()) {
            Map.Entry<String, SharedClient> entry = idleIterator.next();
            if (now - entry.getValue().lastAccessNanos > idleNanos) {
                idleIterator.remove();
                // Same pending-counter conservation as eviction: retired entries must not
                // leak their residual failure counts, and the retired flag declines any
                // recorder still racing this removal.
                entry.getValue().retire();
                retiredCount++;
            }
        }
        if (retiredCount > 0 && retireLogAllowed()) {
            Loggers.LLM.info("Retired {} idle shared model HttpClients (idle threshold {}s)",
                    retiredCount, TimeUnit.NANOSECONDS.toSeconds(idleNanos));
        }
    }

    // Atomic per key: check-and-mark must be one step or concurrent evictions of the same
    // identity would each decide they were first. compute() gives exactly that.
    static boolean eventLogAllowed(String eventKey) {
        long now = nanoClock.getAsLong();
        if (EVENT_LOG_AT.size() >= MAX_EVENT_LOG_KEYS) {
            EVENT_LOG_AT.entrySet()
                    .removeIf(entry -> now - entry.getValue() >= EVENT_LOG_COOLDOWN_NANOS);
        }
        boolean[] allowed = {false};
        EVENT_LOG_AT.compute(eventKey, (ignored, last) -> {
            if (last == null || now - last >= EVENT_LOG_COOLDOWN_NANOS) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }

    // Global CAS loop so concurrent sweeps still yield at most one retirement line per window.
    static boolean retireLogAllowed() {
        long now = nanoClock.getAsLong();
        while (true) {
            long last = LAST_RETIRE_LOG_AT.get();
            if (last != 0L && now - last < EVENT_LOG_COOLDOWN_NANOS) {
                return false;
            }
            if (LAST_RETIRE_LOG_AT.compareAndSet(last, now)) {
                return true;
            }
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
        if (HAS_WARNED_INVALID_IDLE_SECONDS.compareAndSet(false, true)) {
            Loggers.LLM.warning("Invalid {} value '{}'; using default {} seconds",
                    SHARED_CLIENT_IDLE_SECONDS_PROPERTY, raw, DEFAULT_SHARED_CLIENT_IDLE_SECONDS);
        }
        return TimeUnit.SECONDS.toNanos(DEFAULT_SHARED_CLIENT_IDLE_SECONDS);
    }

    static void clearSharedClientsForTesting() {
        SHARED_CLIENTS.clear();
        CACHE_FULL_WARNED_KEYS.clear();
        EVENT_LOG_AT.clear();
        LAST_RETIRE_LOG_AT.set(0L);
        DEAD_CLIENT_REGISTRY.clear();
        PENDING_SEND_FAILURES.set(0);
        HAS_WARNED_INVALID_CAP.set(false);
        HAS_WARNED_INVALID_IDLE_SECONDS.set(false);
        nanoClock = System::nanoTime;
    }

    // Read-only seam (L2 §4.3): leak-regression assertions read the pool-wide pending
    // counter — a leaked count would permanently defeat the zero-scan hot path.
    static int pendingSendFailuresForTesting() {
        return PENDING_SEND_FAILURES.get();
    }

    // Test seam (L2 §4.3): replaces the sole pooled entry's client with a stub, so
    // family-level hook tests poison the pool through a designed seam instead of reflection.
    static void replaceSolePooledClientForTesting(HttpClient replacement) {
        if (SHARED_CLIENTS.size() != 1) {
            throw new IllegalStateException(
                    "expected exactly one pooled entry, found " + SHARED_CLIENTS.size());
        }
        Map.Entry<String, SharedClient> sole = SHARED_CLIENTS.entrySet().iterator().next();
        // Retire (drain + mark) the replaced entry so a poisoned stub's residual failure
        // counts cannot survive the swap into the pool-wide pending counter.
        sole.getValue().retire();
        sole.setValue(new SharedClient(replacement));
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
    // http://user:pass@host convention. The separator is the last '@' inside the authority,
    // searched only within authority bounds (so a path or query '@' never wins): a raw '@'
    // inside the password is malformed per RFC 3986 yet common in hand-written URLs, and the
    // whole secret must still be masked then. The ProxySelector itself keeps parsing the raw
    // proxy URL because it only consumes host and port.
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
        int userInfoSeparator = url.lastIndexOf('@', authorityEnd - 1);
        if (userInfoSeparator < authorityStart) {
            return url;
        }
        return url.substring(0, authorityStart) + "***@"
                + url.substring(userInfoSeparator + 1);
    }

    // Resolves the effective proxy address: a blank host, or EXPLICIT_PORT_ONLY with a
    // portless URL, means no proxy; otherwise the scheme default fills a missing port.
    static Optional<InetSocketAddress> resolveProxyAddress(String proxyUrl, ProxyPortMode portMode) {
        if (proxyUrl == null || proxyUrl.isBlank()) {
            return Optional.empty();
        }
        URI proxyUri = URI.create(proxyUrl);
        String host = proxyUri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        int port = proxyUri.getPort();
        if (port <= 0 && portMode == ProxyPortMode.EXPLICIT_PORT_ONLY) {
            return Optional.empty();
        }
        int resolvedPort = port > 0 ? port : defaultProxyPort(proxyUri);
        return Optional.of(new InetSocketAddress(host, resolvedPort));
    }

    // The proxy key is the resolved address, never the raw URL or the port mode: families
    // with different port modes resolve the same explicit-port proxy to the same address
    // and must share one entry, while portless URLs already differ by resolved address
    // (default port vs no proxy). The address is credential-free by construction because
    // URI host parsing never carries userinfo; the host spelling is normalized the same
    // way as the target host so case and FQDN trailing-dot variants share one entry.
    static String proxyKeyFor(InetSocketAddress proxyAddress) {
        return "proxy:" + normalizeHost(proxyAddress.getHostString()) + ':' + proxyAddress.getPort();
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
     *
     * @param clientConfig the family config supplying the tiered connect-timeout inputs
     * @return the effective connect timeout as a positive duration
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

        // Guarded by this entry's monitor; set by removal paths (eviction, retirement,
        // test-seam replacement) after draining the counts, so a recorder that loses the
        // race declines instead of orphaning counts in the pool-wide pending counter.
        private boolean isRetired;

        private SharedClient(HttpClient client) {
            this.client = client;
            this.lastAccessNanos = nanoClock.getAsLong();
        }

        private void touch() {
            this.lastAccessNanos = nanoClock.getAsLong();
        }

        private synchronized int recordSendFailure() {
            if (isRetired) {
                return sendFailures.get();
            }
            PENDING_SEND_FAILURES.incrementAndGet();
            return sendFailures.incrementAndGet();
        }

        private synchronized void resetSendFailures() {
            int previous = sendFailures.getAndSet(0);
            if (previous > 0) {
                PENDING_SEND_FAILURES.addAndGet(-previous);
            }
        }

        // Removal paths call this inside the entry's monitor: drain the residual counts back
        // to the pool-wide baseline, then refuse late recorders.
        private synchronized void retire() {
            resetSendFailures();
            isRetired = true;
        }
    }

    enum ProxyPortMode {
        EXPLICIT_PORT_ONLY,
        DEFAULT_PORT_WHEN_MISSING
    }
}
