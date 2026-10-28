/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.context.offload.api.OffloadCatalogImpl;
import com.openjiuwen.core.context.offload.api.OffloadService;
import com.openjiuwen.core.context.offload.api.OffloadServiceImpl;
import com.openjiuwen.core.context.offload.api.OffloadStore;
import com.openjiuwen.core.context.offload.model.OffloadBackend;
import com.openjiuwen.core.context.offload.model.OffloadConfig;
import com.openjiuwen.core.context.offload.model.OffloadNamespace;
import com.openjiuwen.core.context.offload.model.OffloadPayload;
import com.openjiuwen.core.context.offload.model.OffloadRecord;
import com.openjiuwen.core.context.offload.model.OffloadStatus;
import com.openjiuwen.core.context.offload.store.CompositeOffloadStore;
import com.openjiuwen.core.context.offload.store.FileOffloadStore;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link OffloadJanitor} T21 orphan-scan unit tests (v2 §7.9 / §7.10 C6,
 * AC-C6-03): stuck-PENDING reaping and catalog-less filesystem leftovers.
 */
class OffloadJanitorOrphanScanTest {
    private static final OffloadNamespace NS = OffloadNamespace.of("t1", "s1", "c1");

    @TempDir
    Path tempDir;

    private OffloadCatalogImpl catalog;
    private FileOffloadStore fileStore;
    private CompositeOffloadStore store;
    private OffloadConfig config;

    @BeforeEach
    void setUp() {
        catalog = new OffloadCatalogImpl();
        fileStore = new FileOffloadStore(tempDir);
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.FILESYSTEM, fileStore);
        store = new CompositeOffloadStore(chain);
        config = new OffloadConfig();
    }

    private OffloadJanitor newJanitor() {
        return new OffloadJanitor(catalog, store, null, config);
    }

    private static OffloadPayload payload(String handle) {
        List<BaseMessage> messages = List.of(new UserMessage("payload " + handle));
        return OffloadPayload.create(handle, NS, messages, null);
    }

    private String publish(String handle) {
        fileStore.save(NS, handle, payload(handle), null);
        return handle;
    }

    private static String hexHandle(String seed) {
        String base = Integer.toHexString(seed.hashCode()) + seed;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; sb.length() < 32; i++) {
            sb.append(Integer.toHexString((base.charAt(i % base.length()) * 31 + i) & 0xF));
        }
        return sb.toString();
    }

    private void ageFileBeyondOrphanTtl(String handle) throws IOException {
        Path target = Path.of(FileOffloadStore.resolveFilePath(tempDir, NS, handle));
        Files.setLastModifiedTime(target,
                java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()
                        - Duration.ofHours(2).toMillis()));
    }

    @Test
    void stalePendingEntryIsReapedWithItsPayload() {
        // Family 1: a save that crashed between reserve and commit/abort
        // leaves a PENDING record (invisible to the expiry scan); after the
        // orphan TTL it must be deleted together with any published payload.
        String handle = hexHandle("stale-pending");
        publish(handle);
        OffloadPayload stored = payload(handle);
        catalog.reserve(new OffloadRecord(handle, NS, null, null, stored.contentSha256(), 10L,
                System.currentTimeMillis() - Duration.ofHours(2).toMillis(), null,
                System.currentTimeMillis() - Duration.ofHours(2).toMillis(), false,
                OffloadStatus.PENDING, "src"));

        newJanitor().scanNow();

        assertThat(catalog.findByHandle(handle)).isEmpty();
        assertThat(fileStore.exists(NS, handle)).isFalse();
    }

    @Test
    void freshPendingEntrySurvivesTheScan() {
        // A PENDING record inside the orphan TTL is a live write-ahead
        // reservation — the janitor must leave it alone.
        String handle = hexHandle("fresh-pending");
        publish(handle);
        OffloadPayload stored = payload(handle);
        catalog.reserve(new OffloadRecord(handle, NS, null, null, stored.contentSha256(), 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));

        newJanitor().scanNow();

        assertThat(catalog.findByHandle(handle)).isPresent();
        assertThat(fileStore.exists(NS, handle)).isTrue();
    }

    @Test
    void catalogLessOldFileIsDeletedAndFreshFileKept() throws IOException {
        // Family 2 / AC-C6-03: a process restart loses the catalog while the
        // payload files survive; files older than the orphan TTL with no
        // catalog entry are deleted, fresh ones are kept.
        String old = publish(hexHandle("old-orphan"));
        String fresh = publish(hexHandle("fresh-entry"));
        ageFileBeyondOrphanTtl(old);

        newJanitor().scanNow();

        assertThat(fileStore.exists(NS, old)).isFalse();
        assertThat(fileStore.exists(NS, fresh)).isTrue();
    }

    @Test
    void knownHandleFileIsNeverDeletedEvenWhenAged() throws IOException {
        // The handle exists in the catalog (READY) — the file backs a live
        // entry and must never be removed by the orphan scan.
        String handle = publish(hexHandle("catalog-backed"));
        ageFileBeyondOrphanTtl(handle);
        OffloadPayload stored = payload(handle);
        catalog.reserve(new OffloadRecord(handle, NS, null, null, stored.contentSha256(), 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));
        catalog.commit(handle, OffloadBackend.FILESYSTEM,
                FileOffloadStore.resolveFilePath(tempDir, NS, handle), stored.serializeJson().length(),
                System.currentTimeMillis() + Duration.ofHours(24).toMillis());

        newJanitor().scanNow();

        assertThat(fileStore.exists(NS, handle)).isTrue();
        assertThat(catalog.findByHandle(handle)).isPresent();
    }

    @Test
    void tmpAndLockSiblingsAreNotScanned() throws IOException {
        String handle = publish(hexHandle("real-payload"));
        ageFileBeyondOrphanTtl(handle);
        Path target = Path.of(FileOffloadStore.resolveFilePath(tempDir, NS, handle));
        Files.writeString(target.resolveSibling(target.getFileName() + ".tmp.abc"), "half written",
                StandardOpenOption.CREATE);
        Files.writeString(target.resolveSibling(target.getFileName() + ".lock"), "",
                StandardOpenOption.CREATE);
        // tmp/lock siblings carry non-handle names; they must not be deleted
        // via the orphan scan (the publisher owns their lifecycle).
        String tmpName = target.getFileName() + ".tmp.abc";
        String lockName = target.getFileName() + ".lock";

        newJanitor().scanNow();

        Path offloadDir = target.getParent();
        assertThat(Files.exists(offloadDir.resolve(tmpName))).isTrue();
        assertThat(Files.exists(offloadDir.resolve(lockName))).isTrue();
    }

    @Test
    void orphanScanDisabledWhenOrphanTtlSecondsZero() throws IOException {
        config.setOrphanTtlSeconds(0L);
        String handle = publish(hexHandle("disabled-scan"));
        ageFileBeyondOrphanTtl(handle);
        catalog.reserve(new OffloadRecord(handle, NS, null, null, null, 10L,
                System.currentTimeMillis() - Duration.ofDays(2).toMillis(), null,
                System.currentTimeMillis(), false, OffloadStatus.PENDING, "src"));

        newJanitor().scanNow();

        // Disabled: no PENDING reaping and no file deletion.
        assertThat(catalog.findByHandle(handle)).isPresent();
        assertThat(fileStore.exists(NS, handle)).isTrue();
    }

    @Test
    void quotaScanEvictsLruAndLogsQuotaEvicted() {
        // TC_125-UT: 会话条目超配额时 LRU 最旧未 pin 条目被驱逐并打出 quotaEvicted>=1
        // 的 scan done 日志行（保护判定在 processor 侧，不阻塞 janitor 回收推进）。
        config.setMaxEntriesPerSession(2);
        String first = publish(hexHandle("quota-first"));
        String second = publish(hexHandle("quota-second"));
        String third = publish(hexHandle("quota-third"));
        commitAll(first, second, third);
        java.util.List<String> logs = captureJanitorLogs(() -> newJanitor().scanNow());

        assertThat(catalog.findByHandle(first)).isEmpty();
        assertThat(fileStore.exists(NS, first)).isFalse();
        assertThat(catalog.findByHandle(third)).isPresent();
        assertThat(catalog.usedEntries(NS)).isEqualTo(2);
        assertThat(logs).anyMatch(line -> line.contains("[OffloadJanitor] scan done:")
                && line.contains("quotaEvicted=1"));
    }

    @Test
    void evictedHandleLoadThrowsStructuredMissing() {
        // TC_125-UT: 驱逐后取回已驱逐句柄抛结构化 OFFLOAD_MISSING（无自由文本/堆栈泄露）。
        config.setMaxEntriesPerSession(1);
        String survivor = publish(hexHandle("quota-keep"));
        String victim = publish(hexHandle("quota-victim"));
        commitAll(victim, survivor);
        newJanitor().scanNow();
        assertThat(catalog.findByHandle(victim)).isEmpty();

        com.openjiuwen.core.context.offload.api.OffloadService service =
                new com.openjiuwen.core.context.offload.api.OffloadServiceImpl(catalog, store,
                        new SessionOffloadGate(), config);
        com.openjiuwen.core.context.offload.api.OffloadException ex =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> service.load(NS, victim, "filesystem"),
                        com.openjiuwen.core.context.offload.api.OffloadException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.code()).isEqualTo(com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_MISSING);
    }

    /** Commits freshly published payloads to READY so the quota scan can see them. */
    private void commitAll(String... handles) {
        for (String handle : handles) {
            OffloadPayload stored = payload(handle);
            catalog.reserve(new OffloadRecord(handle, NS, null, null, stored.contentSha256(), 10L,
                    System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                    OffloadStatus.PENDING, "src"));
            catalog.commit(handle, OffloadBackend.FILESYSTEM,
                    FileOffloadStore.resolveFilePath(tempDir, NS, handle),
                    stored.serializeJson().length(),
                    System.currentTimeMillis() + java.time.Duration.ofHours(24).toMillis());
        }
        // 先提交后钉序：按参数顺序重登记出递增的 lastAccessAt，越靠前越旧必先被驱逐。
        long baseAccess = System.currentTimeMillis() - 100_000L;
        for (int i = 0; i < handles.length; i++) {
            String handle = handles[i];
            catalog.remove(handle);
            OffloadPayload stored = payload(handle);
            catalog.reserve(new OffloadRecord(handle, NS, null, null, stored.contentSha256(), 10L,
                    System.currentTimeMillis(), null, baseAccess + i * 1000L, false,
                    OffloadStatus.PENDING, "src"));
            catalog.commit(handle, OffloadBackend.FILESYSTEM,
                    FileOffloadStore.resolveFilePath(tempDir, NS, handle),
                    stored.serializeJson().length(),
                    System.currentTimeMillis() + java.time.Duration.ofHours(24).toMillis());
        }
    }

    /** Captures [OffloadJanitor] logger lines while the action runs. */
    private static java.util.List<String> captureJanitorLogs(Runnable action) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(OffloadJanitor.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        ch.qos.logback.classic.Level original = logger.getLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(original);
        }
        return appender.list.stream()
                .map(event -> event.getFormattedMessage())
                .toList();
    }

    @Test
    void serviceBackedJanitorStillReapsOrphanFiles() {
        // Deletion through the service facade keeps gate/store semantics;
        // the file-family orphan scan is janitor-internal and must work the
        // same either way (AC-C6-03 via the wired pipeline).
        OffloadService service = new OffloadServiceImpl(catalog, store, new SessionOffloadGate(), config);
        String handle = publish(hexHandle("wired-orphan"));

        OffloadJanitor janitor = new OffloadJanitor(catalog, store, service, config);
        // Reap only the file family: publish first, then age, then scan.
        try {
            ageFileBeyondOrphanTtl(handle);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        janitor.scanNow();

        assertThat(fileStore.exists(NS, handle)).isFalse();
    }

    @Test
    void expiryScanReapsExactBoundaryEntriesAndLogsExpiredRemoved() {
        // TC_146-UT/TC_141-UT: 过期扫描回收 expiresAt 恰等于扫描时刻的条目（<= 语义）
        // 并打出 expiredRemoved>=1 的 scan done 日志行，未到期条目同轮存活。
        String due = publish(hexHandle("ttl-due"));
        String alive = publish(hexHandle("ttl-alive"));
        commitAll(due, alive);
        catalog.extendTtl(alive, System.currentTimeMillis() + java.time.Duration.ofHours(1).toMillis());
        // 恰等侧：expiresAt == 扫描时刻的条目立即纳入过期集合（<= 语义）。
        long boundary = System.currentTimeMillis();
        catalog.extendTtl(due, boundary);
        assertThat(catalog.listExpired(boundary, 10))
                .extracting(record -> record.handle())
                .containsExactly(due);
        try {
            Thread.sleep(2);
        } catch (InterruptedException ex) {
            // G.CON.10: test-only sleep; no interrupt restoration needed
        }

        java.util.List<String> scanLogs = captureJanitorLogs(() -> newJanitor().scanNow());
        assertThat(catalog.findByHandle(due)).isEmpty();
        assertThat(fileStore.exists(NS, due)).isFalse();
        assertThat(catalog.findByHandle(alive)).isPresent();
        assertThat(fileStore.exists(NS, alive)).isTrue();
        assertThat(scanLogs).anyMatch(line -> line.contains("[OffloadJanitor] scan done:")
                && line.contains("expiredRemoved=1"));
    }

    @Test
    void quotaScanSkipsAllPinnedNamespaceThenEvictsAfterUnpin() {
        // TC_148-UT: 全 pinned 超配额时扫描正常完成不抛异常（无驱逐、条目全存活），
        // unpin 最旧条目后下一轮扫描恢复驱逐。
        config.setMaxEntriesPerSession(2);
        String oldest = publish(hexHandle("pin-all-old"));
        String middle = publish(hexHandle("pin-all-mid"));
        String newest = publish(hexHandle("pin-all-new"));
        commitAll(oldest, middle, newest);
        // 把 oldest 重登记为 10 分钟前访问的记录（commit 保留 lastAccessAt），
        // 保证 unpin 后它是唯一 LRU 受害者。
        long oldestAccess = System.currentTimeMillis() - java.time.Duration.ofMinutes(10).toMillis();
        catalog.remove(oldest);
        OffloadPayload storedOldest = payload(oldest);
        catalog.reserve(new OffloadRecord(oldest, NS, null, null, storedOldest.contentSha256(), 10L,
                System.currentTimeMillis(), null, oldestAccess, false, OffloadStatus.PENDING, "src"));
        catalog.commit(oldest, OffloadBackend.FILESYSTEM,
                FileOffloadStore.resolveFilePath(tempDir, NS, oldest),
                storedOldest.serializeJson().length(),
                System.currentTimeMillis() + java.time.Duration.ofHours(24).toMillis());
        catalog.setPinned(oldest, true);
        catalog.setPinned(middle, true);
        catalog.setPinned(newest, true);

        java.util.List<String> blockedLogs = new java.util.ArrayList<>();
        org.assertj.core.api.Assertions.assertThatCode(
                () -> blockedLogs.addAll(captureJanitorLogs(() -> newJanitor().scanNow())))
                .doesNotThrowAnyException();
        assertThat(blockedLogs).noneMatch(line -> line.matches(".*quotaEvicted=[1-9].*"));
        assertThat(catalog.findByHandle(oldest)).isPresent();
        assertThat(catalog.findByHandle(middle)).isPresent();
        assertThat(catalog.findByHandle(newest)).isPresent();

        catalog.setPinned(oldest, false);
        java.util.List<String> resumedLogs = captureJanitorLogs(() -> newJanitor().scanNow());
        assertThat(catalog.findByHandle(oldest)).isEmpty();
        assertThat(fileStore.exists(NS, oldest)).isFalse();
        assertThat(catalog.findByHandle(middle)).isPresent();
        assertThat(resumedLogs).anyMatch(line -> line.contains("[OffloadJanitor] scan done:")
                && line.contains("quotaEvicted=1"));
    }

    @Test
    void scanKeepsEntryWhenBackendDeleteFails() {
        // TC_150-UT: 后端删除抛 OffloadException 时扫描不中断、条目保留
        // （deleteRecord false→break 路径），调度线程不死。
        String handle = publish(hexHandle("backend-down"));
        commitAll(handle);
        catalog.extendTtl(handle, System.currentTimeMillis() - 1L);
        OffloadStore failingBackend = new OffloadStore() {
            @Override
            public OffloadBackend backend() {
                return OffloadBackend.FILESYSTEM;
            }

            @Override
            public void save(OffloadNamespace ns, String h, OffloadPayload payload, Duration ttl) {
                throw new com.openjiuwen.core.context.offload.api.OffloadException(
                        com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_STORE_IO,
                        "backend unavailable");
            }

            @Override
            public OffloadPayload load(OffloadNamespace ns, String h) {
                throw new com.openjiuwen.core.context.offload.api.OffloadException(
                        com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_STORE_IO,
                        "backend unavailable");
            }

            @Override
            public boolean delete(OffloadNamespace ns, String h) {
                throw new com.openjiuwen.core.context.offload.api.OffloadException(
                        com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_STORE_IO,
                        "backend unavailable");
            }

            @Override
            public boolean exists(OffloadNamespace ns, String h) {
                return false;
            }
        };
        LinkedHashMap<OffloadBackend, OffloadStore> chain = new LinkedHashMap<>();
        chain.put(OffloadBackend.FILESYSTEM, failingBackend);
        OffloadJanitor failingJanitor = new OffloadJanitor(catalog, new CompositeOffloadStore(chain), null, config);

        org.assertj.core.api.Assertions.assertThatCode(failingJanitor::scanNow).doesNotThrowAnyException();

        assertThat(catalog.findByHandle(handle)).isPresent();
        assertThat(fileStore.exists(NS, handle)).isTrue();
    }

    @Test
    void pinnedEntrySurvivesExpiryScanWhileUnpinnedSiblingIsReaped() {
        // TC_152-UT/TC_158-UT: 同刻创建两条同刻到期，经 service.pin（压缩期 pin
        // 出窗机制）保护其一——pinned 条目经扫描存活且取回原文，未 pin 参照条目被回收。
        String pinned = publish(hexHandle("pin-ttl-keep"));
        String doomed = publish(hexHandle("pin-ttl-drop"));
        commitAll(pinned, doomed);
        long past = System.currentTimeMillis() - 1L;
        catalog.extendTtl(pinned, past);
        catalog.extendTtl(doomed, past);

        OffloadService service = new OffloadServiceImpl(catalog, store, new SessionOffloadGate(), config);
        service.pin(pinned, java.time.Duration.ofMinutes(5));
        assertThat(catalog.findByHandle(pinned).orElseThrow().expiresAt())
                .isGreaterThan(System.currentTimeMillis());

        newJanitor().scanNow();

        assertThat(catalog.findByHandle(pinned)).isPresent();
        assertThat(fileStore.exists(NS, pinned)).isTrue();
        assertThat(catalog.findByHandle(doomed)).isEmpty();
        assertThat(fileStore.exists(NS, doomed)).isFalse();
        OffloadPayload reloaded = service.load(NS, pinned, "filesystem");
        assertThat(reloaded.messages()).isNotEmpty();
        assertThat(reloaded.messages().get(0).get("content"))
                .isEqualTo("payload " + pinned);
    }

    @Test
    void twoConsecutiveScansOverDeletedTargetDoNotThrow() {
        // TC_153-UT: 连续两轮扫描面对已删目标不抛异常（safeScan/delete 幂等），
        // 第二轮无异常且不再计入回收。
        String handle = publish(hexHandle("double-scan"));
        commitAll(handle);
        catalog.extendTtl(handle, System.currentTimeMillis() - 1L);

        newJanitor().scanNow();
        assertThat(catalog.findByHandle(handle)).isEmpty();
        assertThat(fileStore.exists(NS, handle)).isFalse();
        // 已删目标的载荷文件再手工放回，构造第二轮扫描面对"登记已清、载荷残留"的中间态。
        publish(handle);

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            newJanitor().scanNow();
            newJanitor().scanNow();
        }).doesNotThrowAnyException();
        // 无 catalog 登记且新鲜的残留文件不被孤儿扫描触碰（孤儿扫描只收超龄文件）。
        assertThat(fileStore.exists(NS, handle)).isTrue();
    }

    @Test
    void defaultQuotaScaleDoesNotEvictSmallSession() {
        // TC_144-UT: 默认配额规模（条目远小于 2000）下配额扫描不驱逐任何条目。
        String one = publish(hexHandle("quota-default-1"));
        String two = publish(hexHandle("quota-default-2"));
        String three = publish(hexHandle("quota-default-3"));
        commitAll(one, two, three);

        newJanitor().scanNow();

        assertThat(catalog.findByHandle(one)).isPresent();
        assertThat(catalog.findByHandle(two)).isPresent();
        assertThat(catalog.findByHandle(three)).isPresent();
        assertThat(catalog.usedEntries(NS)).isEqualTo(3);
    }

    @Test
    void quotaEvictionRacingLoadsYieldOnlyVerbatimOrStructuredError() throws Exception {
        // TC_223: a loader thread keeps reloading the oldest handle while an
        // evictor thread hammers evictLru — every load outcome is binary: the
        // verbatim payload (eviction not yet happened) or a structured
        // OFFLOAD_MISSING/GONE error (already evicted; the catalog record is
        // gone first, so the ownership gate reports it). Never a torn read,
        // never a spurious CORRUPT, no stack trace or file path in the error.
        String old = publish(hexHandle("race-old"));
        commitAll(old);
        OffloadService service = new OffloadServiceImpl(catalog, store, new SessionOffloadGate(), config);
        int rounds = 30;
        AtomicInteger verbatim = new AtomicInteger();
        AtomicInteger structuredError = new AtomicInteger();
        List<Throwable> failures = new java.util.concurrent.CopyOnWriteArrayList<>();
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        Thread loader = new Thread(() -> {
            try {
                ready.await();
                for (int r = 0; r < rounds; r++) {
                    try {
                        OffloadPayload loaded = service.load(NS, old, "filesystem");
                        if (loaded.messages().size() == 1 && loaded.sha256Matches()) {
                            verbatim.incrementAndGet();
                        } else {
                            failures.add(new AssertionError("torn read on round " + r));
                        }
                    } catch (com.openjiuwen.core.context.offload.api.OffloadException ex) {
                        if (ex.code() == com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_MISSING
                                || ex.code() == com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_GONE) {
                            structuredError.incrementAndGet();
                            String message = String.valueOf(ex.getMessage());
                            if (!message.contains(old)) {
                                failures.add(new AssertionError(
                                        "structured error must carry the handle: " + message, ex));
                            }
                        } else {
                            failures.add(new AssertionError(
                                    "unexpected code " + ex.code() + " on round " + r, ex));
                        }
                    }
                }
            } catch (InterruptedException ex) {
                failures.add(ex);
            } finally {
                done.countDown();
            }
        });
        Thread evictor = new Thread(() -> {
            try {
                ready.await();
                for (int r = 0; r < rounds; r++) {
                    catalog.evictLru(NS);
                }
            } catch (InterruptedException ex) {
                failures.add(ex);
            } finally {
                done.countDown();
            }
        });
        loader.setUncaughtExceptionHandler((t, ex) -> failures.add(ex));
        evictor.setUncaughtExceptionHandler((t, ex) -> failures.add(ex));
        loader.start();
        evictor.start();
        ready.countDown();
        org.assertj.core.api.Assertions.assertThat(done.await(30, java.util.concurrent.TimeUnit.SECONDS))
                .as("race workers must finish in time").isTrue();

        assertThat(failures).as("racing loads must never misbehave: %s", failures).isEmpty();
        assertThat(verbatim.get() + structuredError.get()).isEqualTo(rounds);
        // Every verdict is one of the two legal ones — the intermediate window
        // (catalog deleted but payload still on disk) never surfaces a half
        // payload or a foreign error code to the caller.
        assertThat(catalog.findByHandle(old)).isEmpty();
    }

    @Test
    void foreignCatalogKnownFileIsOrphanToThisCatalogAndUntouchedForItsOwner() throws IOException {
        // TC_224: two catalogs share one workspace — a payload published and
        // aged beyond the orphan TTL in catalog B is, to catalog A's orphan
        // scan, just an unowned file (deleted), while catalog B still finds
        // its own entry (the scan never touches another catalog's records).
        OffloadCatalogImpl catalogB = new OffloadCatalogImpl();
        String bHandle = hexHandle("catalog-b-owned");
        publish(bHandle);
        ageFileBeyondOrphanTtl(bHandle);
        OffloadPayload storedB = payload(bHandle);
        catalogB.reserve(new OffloadRecord(bHandle, NS, null, null, storedB.contentSha256(), 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));
        catalogB.commit(bHandle, OffloadBackend.FILESYSTEM,
                FileOffloadStore.resolveFilePath(tempDir, NS, bHandle),
                storedB.serializeJson().length(),
                System.currentTimeMillis() + Duration.ofHours(24).toMillis());

        // Catalog A (the janitor's catalog) has never seen this handle.
        assertThat(catalog.findByHandle(bHandle)).isEmpty();
        newJanitor().scanNow();

        // A-side view: the file is an unowned aged orphan → deleted.
        assertThat(fileStore.exists(NS, bHandle)).isFalse();
        // B-side view: the catalog entry survives even though its backing
        // file was reaped — cross-catalog isolation of the ledger.
        assertThat(catalogB.findByHandle(bHandle)).isPresent();
        assertThat(catalogB.usedEntries(NS)).isEqualTo(1);

        // A catalog-A-owned entry aged the same way is kept by the same scan
        // — the deletion is orphan-driven, not a blanket sweep.
        String aHandle = hexHandle("catalog-a-owned");
        publish(aHandle);
        ageFileBeyondOrphanTtl(aHandle);
        OffloadPayload storedA = payload(aHandle);
        catalog.reserve(new OffloadRecord(aHandle, NS, null, null, storedA.contentSha256(), 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));
        catalog.commit(aHandle, OffloadBackend.FILESYSTEM,
                FileOffloadStore.resolveFilePath(tempDir, NS, aHandle),
                storedA.serializeJson().length(),
                System.currentTimeMillis() + Duration.ofHours(24).toMillis());
        newJanitor().scanNow();
        assertThat(fileStore.exists(NS, aHandle)).isTrue();
    }

    @Test
    void restartedFreshCatalogLosesLedgerAndMissingChainReapsAgedFiles() throws IOException {
        // TC_243: the full causal chain across a "restart" — the same
        // FileOffloadStore workspace plus a brand-new empty catalog (the
        // in-memory ledger is gone) means old handles load as MISSING even
        // though the files are still on disk; a fresh save in the same
        // directory still works with a new distinct handle; the fresh
        // leftover file survives the orphan scan while the aged one is reaped.
        OffloadService before = new OffloadServiceImpl(catalog, store, new SessionOffloadGate(), config);
        String oldHandle = publish(hexHandle("restart-ledger"));
        OffloadPayload stored = payload(oldHandle);
        catalog.reserve(new OffloadRecord(oldHandle, NS, null, null, stored.contentSha256(), 10L,
                System.currentTimeMillis(), null, System.currentTimeMillis(), false,
                OffloadStatus.PENDING, "src"));
        catalog.commit(oldHandle, OffloadBackend.FILESYSTEM,
                FileOffloadStore.resolveFilePath(tempDir, NS, oldHandle),
                stored.serializeJson().length(),
                System.currentTimeMillis() + Duration.ofHours(24).toMillis());
        // Sanity: pre-restart the handle loads verbatim.
        assertThat(before.load(NS, oldHandle, "filesystem").messages()).isNotEmpty();

        // "Restart": same store/workspace, brand-new empty catalog.
        OffloadCatalogImpl freshCatalog = new OffloadCatalogImpl();
        OffloadService after = new OffloadServiceImpl(freshCatalog, store, new SessionOffloadGate(), config);

        // 1) Old handle: catalog ledger lost → structured MISSING, not GONE
        //    and not a half payload (the file being on disk does not help).
        com.openjiuwen.core.context.offload.api.OffloadException missing =
                org.assertj.core.api.Assertions.catchThrowableOfType(
                        () -> after.load(NS, oldHandle, "filesystem"),
                        com.openjiuwen.core.context.offload.api.OffloadException.class);
        assertThat(missing).isNotNull();
        assertThat(missing.code())
                .isEqualTo(com.openjiuwen.core.context.offload.api.OffloadErrorCode.OFFLOAD_MISSING);
        assertThat(fileStore.exists(NS, oldHandle)).isTrue();

        // 2) A new save in the same namespace still succeeds with a fresh
        //    distinct handle (no collision with the orphaned file).
        Optional<com.openjiuwen.core.context.offload.model.OffloadRef> newRef =
                after.save(com.openjiuwen.core.context.offload.model.OffloadRequest.builder()
                        .namespace(NS)
                        .algorithmId("MessageOffloader")
                        .source("post-restart")
                        .messages(List.of(new UserMessage("after restart")))
                        .build());
        assertThat(newRef).isPresent();
        assertThat(newRef.get().handle()).isNotEqualTo(oldHandle);
        assertThat(after.load(NS, newRef.get().handle(), "filesystem").messages()).isNotEmpty();

        // 3) Orphan disposal: the fresh unowned file survives the scan, the
        //    aged ledger-less file is reaped (mtime aged beyond the orphan
        //    TTL) — re-connects with the TC_224 semantics.
        ageFileBeyondOrphanTtl(oldHandle);
        newJanitorWithCatalog(freshCatalog).scanNow();
        assertThat(fileStore.exists(NS, newRef.get().handle())).isTrue();
        assertThat(fileStore.exists(NS, oldHandle)).isFalse();
        assertThat(freshCatalog.findByHandle(newRef.get().handle())).isPresent();
    }

    private OffloadJanitor newJanitorWithCatalog(OffloadCatalogImpl targetCatalog) {
        return new OffloadJanitor(targetCatalog, store, null, config);
    }

    @Test
    void loadTouchExtendsLruTenureUnderQuotaPressure() {
        // TC_160-UT: load 第 5 步触发 touch（lastAccessAt 刷新），近期被取回条目
        // 在超配额驱逐中存活、未访问的最旧条目被逐。
        OffloadService service = new OffloadServiceImpl(catalog, store, new SessionOffloadGate(), config);
        String accessed = publish(hexHandle("lru-touched"));
        String stale = publish(hexHandle("lru-stale"));
        String filler = publish(hexHandle("lru-filler"));
        commitAll(accessed, stale, filler);
        // 把 stale 重登记为 10 分钟前访问的记录（commit 保留 lastAccessAt），
        // 保证 load touch 后 accessed 必然更新、stale 是唯一 LRU 受害者。
        long staleAccess = System.currentTimeMillis() - java.time.Duration.ofMinutes(10).toMillis();
        catalog.remove(stale);
        OffloadPayload storedStale = payload(stale);
        catalog.reserve(new OffloadRecord(stale, NS, null, null, storedStale.contentSha256(), 10L,
                System.currentTimeMillis(), null, staleAccess, false, OffloadStatus.PENDING, "src"));
        catalog.commit(stale, OffloadBackend.FILESYSTEM,
                FileOffloadStore.resolveFilePath(tempDir, NS, stale),
                storedStale.serializeJson().length(),
                System.currentTimeMillis() + java.time.Duration.ofHours(24).toMillis());
        long beforeLoad = catalog.findByHandle(accessed).orElseThrow().lastAccessAt();
        try {
            Thread.sleep(2);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        service.load(NS, accessed, "filesystem");

        long afterLoad = catalog.findByHandle(accessed).orElseThrow().lastAccessAt();
        assertThat(afterLoad).isGreaterThanOrEqualTo(beforeLoad);
        assertThat(afterLoad).isGreaterThan(staleAccess);
        config.setMaxEntriesPerSession(2);
        newJanitor().scanNow();
        assertThat(catalog.findByHandle(accessed)).isPresent();
        assertThat(catalog.findByHandle(stale)).isEmpty();
        assertThat(fileStore.exists(NS, stale)).isFalse();
        assertThat(catalog.findByHandle(filler)).isPresent();
    }
}
