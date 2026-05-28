/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.browser_move.playwright_runtime;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JSON-backed profile store with selected-profile tracking.
 *
 * <p>Mirrors Python's {@code BrowserProfileStore} in
 * {@code openjiuwen.harness.tools.browser_move.playwright_runtime.profiles}.</p>
 */
public class BrowserProfileStore {

    private final Path path;
    private final Map<String, BrowserProfile> profiles = new HashMap<>();
    private String selected = "";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create a BrowserProfileStore with the given path.
     *
     * @param path the path to the profile store file
     */
    public BrowserProfileStore(Path path) {
        this.path = path != null ? expandUser(path) : null;
        load();
    }

    /**
     * Expand user home directory in path (~ on Unix, user home on all platforms).
     *
     * @param path the path to expand
     * @return the expanded path
     */
    private Path expandUser(Path path) {
        String pathStr = path.toString();
        if (pathStr.startsWith("~")) {
            String userHome = System.getProperty("user.home");
            return Path.of(pathStr.replaceFirst("~", userHome));
        }
        return path;
    }

    /**
     * Load profiles from the JSON file.
     */
    private void load() {
        profiles.clear();
        selected = "";
        
        if (path == null || !Files.exists(path)) {
            return;
        }
        
        try {
            String content = Files.readString(path);
            Map<String, Object> payload = objectMapper.readValue(content, Map.class);
            
            if (!(payload instanceof Map)) {
                return;
            }
            
            Object selectedRaw = payload.get("selected_profile");
            selected = selectedRaw != null ? String.valueOf(selectedRaw).trim() : "";
            
            Object profilesRaw = payload.get("profiles");
            if (profilesRaw instanceof List) {
                for (Object item : (List<?>) profilesRaw) {
                    if (!(item instanceof Map)) {
                        continue;
                    }
                    BrowserProfile profile = BrowserProfile.fromDict((Map<String, Object>) item);
                    if (profile.getName() == null || profile.getName().isEmpty()) {
                        continue;
                    }
                    profiles.put(profile.getName(), profile);
                }
            }
        } catch (IOException ignored) {
            // Silently ignore load errors
        }
    }

    /**
     * Save profiles to the JSON file.
     */
    public void save() {
        if (path == null) {
            return;
        }
        
        try {
            Files.createDirectories(path.getParent());
            
            List<Map<String, Object>> profileList = new ArrayList<>();
            for (BrowserProfile profile : listProfiles()) {
                profileList.add(profile.toDict());
            }
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("selected_profile", selected);
            payload.put("profiles", profileList);
            
            String content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            Files.writeString(path, content);
        } catch (IOException ignored) {
            // Silently ignore save errors
        }
    }

    /**
     * List all profiles, sorted by name.
     *
     * @return the list of profiles
     */
    public List<BrowserProfile> listProfiles() {
        List<BrowserProfile> result = new ArrayList<>(profiles.values());
        result.sort(Comparator.comparing(BrowserProfile::getName));
        return result;
    }

    /**
     * Get a profile by name.
     *
     * @param name the profile name
     * @return the profile, or null if not found
     */
    public BrowserProfile getProfile(String name) {
        String key = name != null ? name.trim() : "";
        if (key.isEmpty()) {
            return null;
        }
        return profiles.get(key);
    }

    /**
     * Upsert (insert or update) a profile.
     *
     * @param profile the profile to upsert
     * @param select  whether to select this profile
     * @return the upserted profile
     * @throws IllegalArgumentException if profile name is empty
     */
    public BrowserProfile upsertProfile(BrowserProfile profile, boolean select) {
        String name = profile.getName() != null ? profile.getName().trim() : "";
        if (name.isEmpty()) {
            throw new IllegalArgumentException("profile.name is required");
        }
        
        profile.setName(name);
        String driverType = profile.getDriverType() != null ? profile.getDriverType().trim().toLowerCase() : "remote";
        if (driverType.isEmpty()) {
            driverType = "remote";
        }
        profile.setDriverType(driverType);
        
        profiles.put(name, profile);
        
        if (select) {
            selected = name;
        } else if (!selected.isEmpty() && !profiles.containsKey(selected)) {
            selected = "";
        }
        
        save();
        return profile;
    }

    /**
     * Upsert a profile without selecting it.
     *
     * @param profile the profile to upsert
     * @return the upserted profile
     */
    public BrowserProfile upsertProfile(BrowserProfile profile) {
        return upsertProfile(profile, false);
    }

    /**
     * Remove a profile by name.
     *
     * @param name the profile name
     * @return true if the profile was removed, false if not found
     */
    public boolean removeProfile(String name) {
        String key = name != null ? name.trim() : "";
        if (key.isEmpty() || !profiles.containsKey(key)) {
            return false;
        }
        
        profiles.remove(key);
        
        if (selected.equals(key)) {
            selected = "";
        }
        
        save();
        return true;
    }

    /**
     * Select a profile by name.
     *
     * @param name the profile name
     * @return the selected profile
     * @throws IllegalArgumentException if profile not found
     */
    public BrowserProfile selectProfile(String name) {
        String key = name != null ? name.trim() : "";
        BrowserProfile profile = profiles.get(key);
        
        if (profile == null) {
            throw new IllegalArgumentException("profile not found: " + key);
        }
        
        selected = key;
        save();
        return profile;
    }

    /**
     * Get the selected profile name.
     *
     * @return the selected profile name, or empty string if none selected
     */
    public String selectedName() {
        return selected;
    }

    /**
     * Get the selected profile.
     *
     * @return the selected profile, or null if none selected
     */
    public BrowserProfile selectedProfile() {
        if (selected.isEmpty()) {
            return null;
        }
        return profiles.get(selected);
    }

    /**
     * Get the store path.
     *
     * @return the path to the profile store file
     */
    public Path getPath() {
        return path;
    }
}