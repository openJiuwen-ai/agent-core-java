/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-backed profile store with selected-profile tracking.
 *
 * <p>Mirrors Python's {@code BrowserProfileStore} in
 * {@code openjiuwen/harness/tools/browser_move/playwright_runtime/profiles.py}.
 */
public final class BrowserProfileStore {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Path path;
    private final Map<String, BrowserProfile> profiles = new LinkedHashMap<>();
    private String selected = "";

    public BrowserProfileStore(Path path) {
        this.path = path.toAbsolutePath().normalize();
        load();
    }

    public Path getPath() {
        return path;
    }

    public void load() {
        profiles.clear();
        selected = "";
        if (!Files.exists(path)) {
            return;
        }
        Map<String, Object> payload;
        try {
            payload = OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            return;
        }
        if (payload == null) {
            return;
        }
        selected = String.valueOf(payload.getOrDefault("selected_profile", "")).trim();
        Object rawProfiles = payload.get("profiles");
        if (rawProfiles instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> rawMap) {
                    BrowserProfile profile = BrowserProfile.fromMap(cast(rawMap));
                    if (!profile.getName().isEmpty()) {
                        profiles.put(profile.getName(), profile);
                    }
                }
            }
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("selected_profile", selected);
            payload.put("profiles", listProfiles().stream().map(BrowserProfile::toMap).toList());
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), payload);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to save browser profiles", ex);
        }
    }

    public List<BrowserProfile> listProfiles() {
        return profiles.values().stream()
                .sorted(Comparator.comparing(BrowserProfile::getName))
                .toList();
    }

    public BrowserProfile getProfile(String name) {
        String key = name == null ? "" : name.trim();
        return key.isEmpty() ? null : profiles.get(key);
    }

    public BrowserProfile upsertProfile(BrowserProfile profile, boolean select) {
        String name = profile.getName().trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("profile.name is required");
        }
        profile.setName(name);
        profile.setDriverType(profile.getDriverType());
        profiles.put(name, profile);
        if (select) {
            selected = name;
        } else if (!selected.isEmpty() && !profiles.containsKey(selected)) {
            selected = "";
        }
        save();
        return profile;
    }

    public boolean removeProfile(String name) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty() || !profiles.containsKey(key)) {
            return false;
        }
        profiles.remove(key);
        if (key.equals(selected)) {
            selected = "";
        }
        save();
        return true;
    }

    public BrowserProfile selectProfile(String name) {
        String key = name == null ? "" : name.trim();
        BrowserProfile profile = profiles.get(key);
        if (profile == null) {
            throw new IllegalArgumentException("profile not found: " + key);
        }
        selected = key;
        save();
        return profile;
    }

    public String selectedName() {
        return selected;
    }

    public BrowserProfile selectedProfile() {
        return selected.isEmpty() ? null : profiles.get(selected);
    }

    private Map<String, Object> cast(Map<?, ?> rawMap) {
        Map<String, Object> casted = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() instanceof String key) {
                casted.put(key, entry.getValue());
            }
        }
        return casted;
    }
}
