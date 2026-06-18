/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for chained session persistence and visibility.
 *
 * <p>Mirrors Python's {@code ChainSession} in
 * {@code openjiuwen/core/session/session_controller/chain_session.py}.</p>
 */
class ChainSessionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void flushPersistsStateAndDownstreamLinks() throws IOException {
        Path sessionDir = tempDir.resolve("session-a");
        ChainSession<Map<String, Object>> session = newSession(sessionDir, new MapContainer(Map.of("answer", 42)));
        session.setActive(true);
        session.addDownstream("target", "session-b", new SharingPolicy(Permission.READ, new LinkedHashSet<>(Set.of("x"))));

        assertThat(session.flush()).isTrue();

        Map<String, Object> stateData = readMap(SessionPaths.stateFile(sessionDir));
        assertThat(mapValue(stateData.get("meta")))
                .containsEntry("version", 1)
                .containsEntry("is_active", true)
                .containsEntry("data_container_type", DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
        assertThat(stateData.get("data")).isEqualTo(Map.of("answer", 42));

        Path linkFile = SessionPaths.linkFile(sessionDir, "target", "session-b");
        assertThat(linkFile).exists();
        Map<String, Object> linkData = readMap(linkFile);
        Map<String, Object> permissionData = mapValue(linkData.get("permission"));
        assertThat(permissionData).containsEntry("level", 1);
        assertThat((Iterable<?>) permissionData.get("field_scopes")).anyMatch("x"::equals);
    }

    @Test
    void loadRestoresMetadataAndDownstreamsAndDeletesRemovedLinks() throws IOException {
        Path sessionDir = tempDir.resolve("session-load");
        Files.createDirectories(SessionPaths.downstreamsDir(sessionDir));
        writeJson(SessionPaths.stateFile(sessionDir), Map.of(
                "meta", Map.of(
                        "created_at", 1.25D,
                        "updated_at", 2.5D,
                        "version", 7,
                        "is_active", true,
                        "data_container_type", DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE
                ),
                "data", Map.of("ignored", true)
        ));
        writeJson(SessionPaths.linkFile(sessionDir, "worker", "child"), Map.of(
                "permission", Map.of("level", 1, "field_scopes", Set.of("left", "right")),
                "created_at", 2.5D
        ));
        Path removedLink = SessionPaths.linkFile(sessionDir, "old", "child");
        writeJson(removedLink, Map.of("removed", true));

        ChainSession<Map<String, Object>> session = newSession(sessionDir, new MapContainer(Map.of()));

        assertThat(session.load()).isTrue();

        assertThat(session.getCreatedAt()).isEqualTo(1.25D);
        assertThat(session.getUpdatedAt()).isEqualTo(2.5D);
        assertThat(session.getVersion()).isEqualTo(7);
        assertThat(session.isActive()).isTrue();
        assertThat(session.hasDownstream("worker", "child")).isTrue();
        assertThat(session.getDownstreamPolicy("worker", "child")).isPresent();
        assertThat(session.getDownstreamPolicy("worker", "child").orElseThrow().getFieldScopes())
                .containsExactlyInAnyOrder("left", "right");
        assertThat(removedLink).doesNotExist();
    }

    @Test
    void downstreamVisibilityAndDataUpdatesMirrorPythonRules() {
        MapContainer container = new MapContainer(new LinkedHashMap<>());
        ChainSession<Map<String, Object>> session = newSession(tempDir.resolve("session-visibility"), container);

        assertThat(session.getSessionKey()).isEqualTo(new SessionScopeKey("agent-a", mainScope()));
        assertThat(session.canSee("agent-a", "session-a")).isTrue();
        assertThat(session.canSee("target", "session-b")).isFalse();

        session.addDownstream("target", "session-b", new SharingPolicy());
        assertThat(session.canSee("target", "session-b")).isTrue();
        assertThat(session.getDownstreams()).containsKey(new ChainSession.DownstreamKey("target", "session-b"));

        int originalVersion = session.getVersion();
        assertThat(session.updateData(Map.of("answer", 42))).isTrue();
        assertThat(session.getVersion()).isEqualTo(originalVersion + 1);
        assertThat(session.getData()).isEqualTo(Map.of("answer", 42));

        session.removeDownstream("target", "session-b");
        assertThat(session.canSee("target", "session-b")).isFalse();
        session.addDownstream("target", "session-c", new SharingPolicy());
        session.removeAllDownstreams();
        assertThat(session.getDownstreams()).isEmpty();
    }

    @Test
    void sessionMetaRoundTripUpdatesPrivateState() {
        ChainSession<Map<String, Object>> session = newSession(tempDir.resolve("session-meta"), new MapContainer(Map.of()));
        SessionMeta meta = new SessionMeta("session-a", 3.0D, 4.0D, 5, true, "custom");

        session.updateFromMeta(meta);
        SessionMeta copied = session.toSessionMeta();

        assertThat(copied.getSessionId()).isEqualTo("session-a");
        assertThat(copied.getCreatedAt()).isEqualTo(3.0D);
        assertThat(copied.getUpdatedAt()).isEqualTo(4.0D);
        assertThat(copied.getVersion()).isEqualTo(5);
        assertThat(copied.isActive()).isTrue();
        assertThat(copied.getDataContainerType()).isEqualTo("custom");
    }

    private ChainSession<Map<String, Object>> newSession(Path sessionDir, DataContainer container) {
        return new ChainSession<>("agent-a", mainScope(), "session-a", container, sessionDir);
    }

    private SessionScope mainScope() {
        return new SessionScope(new MainScope(), null);
    }

    private static Map<String, Object> readMap(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return (Map<String, Object>) value;
    }

    private static void writeJson(Path path, Object data) throws IOException {
        Files.createDirectories(path.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
    }

    private static final class MapContainer implements DataContainer {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();

        private MapContainer(Map<String, Object> values) {
            this.values.putAll(values);
        }

        @Override
        public Object get(Object key) {
            if (key == null) {
                return new LinkedHashMap<>(values);
            }
            return values.get(String.valueOf(key));
        }

        @Override
        public boolean update(Map<String, Object> data) {
            values.putAll(data);
            return true;
        }

        @Override
        public CompletionStage<Object> dump() {
            return CompletableFuture.completedFuture(new LinkedHashMap<>(values));
        }
    }
}
