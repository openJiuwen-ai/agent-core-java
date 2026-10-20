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
}
