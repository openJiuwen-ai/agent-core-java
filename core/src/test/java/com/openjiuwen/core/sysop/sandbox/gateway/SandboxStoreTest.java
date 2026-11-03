/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.sandbox.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxStoreTest {

    @Test
    void sandboxRecordDefaultsMirrorPythonDataclass() {
        double before = System.currentTimeMillis() / 1000.0d;
        SandboxRecord record = new SandboxRecord(
                "sandbox-1",
                "http://localhost",
                SandboxStatus.RUNNING,
                "local",
                "aio",
                "cfg");
        double after = System.currentTimeMillis() / 1000.0d;

        assertEquals("sandbox-1", record.getSandboxId());
        assertEquals("http://localhost", record.getBaseUrl());
        assertEquals(SandboxStatus.RUNNING, record.getStatus());
        assertEquals("local", record.getLauncherType());
        assertEquals("aio", record.getSandboxType());
        assertEquals("cfg", record.getContainerConfigHash());
        assertTrue(record.getCreatedTs() >= before && record.getCreatedTs() <= after);
        assertTrue(record.getLastUsedTs() >= before && record.getLastUsedTs() <= after);
        assertTrue(record.getMetadata().isEmpty());
    }

    @Test
    void inMemoryStoreRoundTripsAndDeletesRecords() {
        InMemorySandboxStore store = new InMemorySandboxStore();
        SandboxRecord record = new SandboxRecord(
                "sandbox-2",
                "http://gateway",
                SandboxStatus.PAUSED,
                "remote",
                "container",
                "hash",
                10.0d,
                11.0d,
                Map.of("owner", "team"));

        store.set("key", record);

        assertSame(record, store.get("key").orElseThrow());
        assertSame(record, store.hdel("key").orElseThrow());
        assertTrue(store.get("key").isEmpty());
        assertTrue(store.hdel("key").isEmpty());
    }

    @Test
    void flushdbReturnsRecordsAndClearsStore() {
        InMemorySandboxStore store = new InMemorySandboxStore();
        SandboxRecord first = new SandboxRecord("a", "u1", SandboxStatus.RUNNING, "l1", "s1", "c1");
        SandboxRecord second = new SandboxRecord("b", "u2", SandboxStatus.KILLED, "l2", "s2", "c2");
        store.set("a", first);
        store.set("b", second);

        assertEquals(List.of(first, second), store.flushdb());
        assertFalse(store.get("a").isPresent());
        assertFalse(store.get("b").isPresent());
    }

    @Test
    void evictExpiredUsesStrictGreaterThanThreshold() {
        InMemorySandboxStore store = new InMemorySandboxStore();
        SandboxRecord exact = new SandboxRecord("exact", "u1", SandboxStatus.RUNNING, "l1", "s1", "c1", 0.0d, 91.0d, Map.of());
        SandboxRecord expired = new SandboxRecord("expired", "u2", SandboxStatus.RUNNING, "l2", "s2", "c2", 0.0d, 50.0d, Map.of());
        store.set("exact", exact);
        store.set("expired", expired);

        assertEquals(List.of(expired), store.evictExpired(10, 101.0d));
        assertSame(exact, store.get("exact").orElseThrow());
        assertTrue(store.get("expired").isEmpty());
    }
}
