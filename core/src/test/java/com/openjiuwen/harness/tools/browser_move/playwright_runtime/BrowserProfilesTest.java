/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BrowserProfilesTest {

    @TempDir
    Path tempDir;

    @Test
    void browserProfileFromMapNormalizesFields() {
        BrowserProfile profile = BrowserProfile.fromMap(Map.of(
                "name", " test-profile ",
                "driver_type", "MANAGED",
                "debug_port", "9333",
                "host", "",
                "extra_args", List.of("--headless", " ")
        ));

        assertEquals("test-profile", profile.getName());
        assertEquals("managed", profile.getDriverType());
        assertEquals(9333, profile.getDebugPort());
        assertEquals("127.0.0.1", profile.getHost());
        assertEquals(List.of("--headless"), profile.getExtraArgs());
    }

    @Test
    void profileStorePersistsSelection() {
        BrowserProfileStore store = new BrowserProfileStore(tempDir.resolve("profiles.json"));
        BrowserProfile alpha = new BrowserProfile("alpha", "managed", "http://127.0.0.1:9333", "", ".", 9333, "127.0.0.1", List.of());
        BrowserProfile beta = new BrowserProfile("beta", "remote", "", "", "", 0, "127.0.0.1", List.of("--incognito"));

        store.upsertProfile(alpha, false);
        store.upsertProfile(beta, true);

        BrowserProfileStore restored = new BrowserProfileStore(tempDir.resolve("profiles.json"));
        assertEquals("beta", restored.selectedName());
        assertNotNull(restored.selectedProfile());
        assertEquals(2, restored.listProfiles().size());
        assertEquals(List.of("--incognito"), restored.getProfile("beta").getExtraArgs());
    }

    @Test
    void removeProfileClearsSelection() {
        BrowserProfileStore store = new BrowserProfileStore(tempDir.resolve("profiles.json"));
        store.upsertProfile(new BrowserProfile("alpha", "remote", "", "", "", 0, "127.0.0.1", List.of()), true);

        assertTrue(store.removeProfile("alpha"));
        assertEquals("", store.selectedName());
        assertNull(store.selectedProfile());
    }
}
