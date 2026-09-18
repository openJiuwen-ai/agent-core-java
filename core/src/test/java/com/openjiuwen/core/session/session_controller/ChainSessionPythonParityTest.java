/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code test_chain_session.py} in
 * {@code tests/unit_tests/core/session/session_controller/test_chain_session.py}.
 */
class ChainSessionPythonParityTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final String TEST_CONTAINER_TYPE = "mtt00044-memory";

    @TempDir
    Path tempDir;

    @BeforeAll
    static void registerContainer() {
        DataContainerFactory.register(TEST_CONTAINER_TYPE, new DataContainerFactory.DataContainerProvider() {
            @Override
            public DataContainer create(Map<String, Object> kwargs) {
                return new MapContainer(Map.of(), Map.of(), false, false);
            }

            @Override
            public CompletionStage<DataContainer> load(String agentId,
                                                       String sessionId,
                                                       Object serialized,
                                                       Map<String, Object> kwargs) {
                Map<String, Object> loaded = serialized instanceof Map<?, ?> map
                        ? stringMap(map)
                        : Map.of();
                return CompletableFuture.completedFuture(new MapContainer(loaded, loaded, true, false));
            }
        });
    }

    @Test
    void testSessionKey() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("session-key"));

        SessionScopeKey key = session.getSessionKey();

        assertThat(key.agentId()).isEqualTo("agent1");
        assertThat(key.sessionScope()).isEqualTo(sessionScope());
    }

    @Test
    void testProperties() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("properties"));

        assertThat(session.getCreatedAt()).isPositive();
        assertThat(session.getUpdatedAt()).isPositive();
        assertThat(session.getVersion()).isEqualTo(1);
        assertThat(session.isActive()).isTrue();
    }

    @Test
    void testIsActiveSetter() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("active-setter"));

        session.setActive(false);
        assertThat(session.isActive()).isFalse();
        session.setActive(true);

        assertThat(session.isActive()).isTrue();
    }

    @Test
    void testGetData() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("get-data"));

        assertThat(session.getData()).containsExactly(Map.entry("key", "value"));
    }

    @Test
    void testUpdateData() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("update-data"));

        boolean result = session.updateData(Map.of("count", 1));

        assertThat(result).isTrue();
        assertThat(session.getData()).containsEntry("count", 1);
        assertThat(session.getVersion()).isEqualTo(2);
    }

    @Test
    void testUpdateDataFailure() {
        ChainSession<Map<String, Object>> session = activeSession(
                sessionDir("update-data-failure"),
                new MapContainer(Map.of("key", "value"), Map.of(), false, true)
        );

        boolean result = session.updateData(Map.of("count", 1));

        assertThat(result).isFalse();
        assertThat(session.getVersion()).isEqualTo(1);
    }

    @Test
    void testAddDownstream() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("add-downstream"));

        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ));

        assertThat(session.hasDownstream("agent2", "session-2")).isTrue();
    }

    @Test
    void testRemoveDownstream() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("remove-downstream"));

        session.addDownstream("agent2", "session-2", null);
        session.removeDownstream("agent2", "session-2");

        assertThat(session.hasDownstream("agent2", "session-2")).isFalse();
    }

    @Test
    void testRemoveNonexistentDownstream() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("remove-nonexistent"));

        session.removeDownstream("agent2", "session-2");

        assertThat(session.getDownstreams()).isEmpty();
    }

    @Test
    void testGetDownstreams() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("get-downstreams"));
        SharingPolicy policy = new SharingPolicy(Permission.READ);

        session.addDownstream("agent2", "session-2", policy);

        assertThat(session.getDownstreams())
                .containsEntry(new ChainSession.DownstreamKey("agent2", "session-2"), policy);
    }

    @Test
    void testGetDownstreamsReturnsCopy() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("downstreams-copy"));
        session.addDownstream("agent2", "session-2", null);

        Map<ChainSession.DownstreamKey, SharingPolicy> downstreams = session.getDownstreams();
        downstreams.clear();

        assertThat(session.hasDownstream("agent2", "session-2")).isTrue();
    }

    @Test
    void testGetDownstreamPolicy() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("downstream-policy"));
        SharingPolicy policy = new SharingPolicy(Permission.READ, Set.of("field1"));

        session.addDownstream("agent2", "session-2", policy);
        Optional<SharingPolicy> found = session.getDownstreamPolicy("agent2", "session-2");

        assertThat(found).containsSame(policy);
    }

    @Test
    void testGetDownstreamPolicyNotFound() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("downstream-policy-missing"));

        assertThat(session.getDownstreamPolicy("agent2", "session-2")).isEmpty();
    }

    @Test
    void testRemoveAllDownstreams() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("remove-all"));

        session.addDownstream("agent2", "session-2", null);
        session.addDownstream("agent3", "session-3", null);
        session.removeAllDownstreams();

        assertThat(session.hasDownstream("agent2", "session-2")).isFalse();
        assertThat(session.hasDownstream("agent3", "session-3")).isFalse();
    }

    @Test
    void testCanSeeSelf() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("can-see-self"));

        assertThat(session.canSee("agent1", "session-1")).isTrue();
    }

    @Test
    void testCanSeeDownstream() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("can-see-downstream"));

        session.addDownstream("agent2", "session-2", null);

        assertThat(session.canSee("agent2", "session-2")).isTrue();
    }

    @Test
    void testCannotSeeUnknown() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("cannot-see-unknown"));

        assertThat(session.canSee("agent2", "session-2")).isFalse();
    }

    @Test
    void testFlushAndLoad() throws Exception {
        Path sessionDir = sessionDir("flush-and-load");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of("field1")));

        assertThat(session.flush()).isTrue();

        Map<String, Object> stateData = readMap(SessionPaths.stateFile(sessionDir));
        assertThat(mapValue(stateData.get("data"))).isEmpty();
        assertThat(mapValue(stateData.get("meta"))).containsEntry("is_active", true);
        assertThat(SessionPaths.linkFile(sessionDir, "agent2", "session-2")).exists();
    }

    @Test
    void testFlushRemovesDeletedDownstream() {
        Path sessionDir = sessionDir("flush-removes-deleted");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);
        session.addDownstream("agent2", "session-2", null);
        assertThat(session.flush()).isTrue();
        Path linkFile = SessionPaths.linkFile(sessionDir, "agent2", "session-2");
        assertThat(linkFile).exists();

        session.removeDownstream("agent2", "session-2");
        assertThat(session.flush()).isTrue();

        assertThat(linkFile).doesNotExist();
    }

    @Test
    void testLoad() {
        Path sessionDir = sessionDir("load");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ));
        assertThat(session.flush()).isTrue();

        ChainSession<Map<String, Object>> loaded = emptySession(sessionDir);

        assertThat(loaded.load()).isTrue();
        assertThat(loaded.isActive()).isTrue();
        assertThat(loaded.hasDownstream("agent2", "session-2")).isTrue();
    }

    @Test
    void testLoadSkipsRemovedLink() throws Exception {
        Path sessionDir = sessionDir("load-skips-removed");
        Files.createDirectories(SessionPaths.downstreamsDir(sessionDir));
        Path linkFile = SessionPaths.linkFile(sessionDir, "agent2", "session-2");
        writeJson(linkFile, Map.of("removed", true));
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);

        assertThat(session.load()).isTrue();

        assertThat(session.hasDownstream("agent2", "session-2")).isFalse();
        assertThat(linkFile).doesNotExist();
    }

    @Test
    void testToSessionMeta() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("to-meta"));

        SessionMeta meta = session.toSessionMeta();

        assertThat(meta.getSessionId()).isEqualTo("session-1");
        assertThat(meta.isActive()).isTrue();
    }

    @Test
    void testUpdateFromMeta() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("update-meta"));
        SessionMeta meta = SessionMeta.createNew("session-1", 10);
        meta.setActive(false);

        session.updateFromMeta(meta);

        assertThat(session.getVersion()).isEqualTo(10);
        assertThat(session.isActive()).isFalse();
    }

    @Test
    void testUpdateFromMetaWithContainerType() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("meta-container-type"));
        SessionMeta meta = SessionMeta.createNew("session-1", 1, "custom_type");

        session.updateFromMeta(meta);

        assertThat(session.getDataContainerType()).isEqualTo("custom_type");
    }

    @Test
    void testUpdateFromMetaWithoutContainerType() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("meta-without-container-type"));
        session.setDataContainerType("original");
        SessionMeta meta = SessionMeta.createNew("session-1");
        meta.setDataContainerType("");

        session.updateFromMeta(meta);

        assertThat(session.getDataContainerType()).isEqualTo("original");
    }

    @Test
    void testAddDownstreamOverwritesExisting() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("overwrite-downstream"));
        SharingPolicy policy1 = new SharingPolicy(Permission.READ);
        SharingPolicy policy2 = new SharingPolicy(Permission.READ, Set.of("f2"));

        session.addDownstream("agent2", "session-2", policy1);
        session.addDownstream("agent2", "session-2", policy2);

        assertThat(session.getDownstreamPolicy("agent2", "session-2").orElseThrow().getFieldScopes())
                .containsExactly("f2");
    }

    @Test
    void testCanSeeAfterRemoveDownstream() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("can-see-after-remove"));
        session.addDownstream("agent2", "session-2", null);
        assertThat(session.canSee("agent2", "session-2")).isTrue();

        session.removeDownstream("agent2", "session-2");

        assertThat(session.canSee("agent2", "session-2")).isFalse();
    }

    @Test
    void testAddDownstreamDefaultPolicy() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("default-policy"));

        session.addDownstream("agent2", "session-2", null);
        SharingPolicy policy = session.getDownstreamPolicy("agent2", "session-2").orElseThrow();

        assertThat(policy.getPermission()).isEqualTo(Permission.READ);
        assertThat(policy.getFieldScopes()).isNull();
    }

    @Test
    void testFlushCreatesDirectories() {
        Path nestedDir = sessionDir("nested").resolve("deep");
        ChainSession<Map<String, Object>> session = activeSession(nestedDir);

        boolean result = session.flush();

        assertThat(result).isTrue();
        assertThat(nestedDir).exists();
        assertThat(SessionPaths.downstreamsDir(nestedDir)).exists();
    }

    @Test
    void testFlushNoDownstreams() throws Exception {
        Path sessionDir = sessionDir("flush-no-downstreams");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);

        assertThat(session.flush()).isTrue();

        List<Path> links = linkFiles(sessionDir);
        assertThat(links).isEmpty();
    }

    @Test
    void testFlushMarksRemovedBeforeDelete() {
        Path sessionDir = sessionDir("flush-mark-delete");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);
        session.addDownstream("agent2", "session-2", null);
        assertThat(session.flush()).isTrue();
        Path linkFile = SessionPaths.linkFile(sessionDir, "agent2", "session-2");
        assertThat(linkFile).exists();

        session.removeDownstream("agent2", "session-2");
        assertThat(session.flush()).isTrue();

        assertThat(linkFile).doesNotExist();
    }

    @Test
    void testLoadRestoresFieldScopes() {
        Path sessionDir = sessionDir("load-field-scopes");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ, Set.of("f1", "f2")));
        assertThat(session.flush()).isTrue();

        ChainSession<Map<String, Object>> loaded = emptySession(sessionDir);
        assertThat(loaded.load()).isTrue();

        assertThat(loaded.getDownstreamPolicy("agent2", "session-2").orElseThrow().getFieldScopes())
                .containsExactlyInAnyOrder("f1", "f2");
    }

    @Test
    void testLoadCorruptedLinkFile() throws Exception {
        Path sessionDir = sessionDir("load-corrupt-link");
        Files.createDirectories(SessionPaths.downstreamsDir(sessionDir));
        Path badLink = SessionPaths.linkFile(sessionDir, "agent2", "session-2");
        Files.writeString(badLink, "NOT VALID JSON{{{{");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);

        boolean result = session.load();

        assertThat(result).isTrue();
        assertThat(session.hasDownstream("agent2", "session-2")).isFalse();
    }

    @Test
    void testLoadLinkFileNoUnderscore() throws Exception {
        Path sessionDir = sessionDir("load-link-no-underscore");
        Files.createDirectories(SessionPaths.downstreamsDir(sessionDir));
        Path linkFile = SessionPaths.downstreamsDir(sessionDir).resolve("nounderscore.link");
        writeJson(linkFile, Map.of("permission", Map.of("level", 1)));
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);

        boolean result = session.load();

        assertThat(result).isTrue();
        assertThat(session.getDownstreams()).isEmpty();
    }

    @Test
    void testLoadNoStateFile() {
        ChainSession<Map<String, Object>> session = emptySession(sessionDir("load-no-state"));

        boolean result = session.load();

        assertThat(result).isTrue();
        assertThat(session.isActive()).isFalse();
        assertThat(session.getVersion()).isEqualTo(1);
    }

    @Test
    void testLoadStateDataNoMeta() throws Exception {
        Path sessionDir = sessionDir("load-state-no-meta");
        Files.createDirectories(sessionDir);
        writeJson(SessionPaths.stateFile(sessionDir), Map.of("data", Map.of("key", "val")));
        ChainSession<Map<String, Object>> session = emptySession(sessionDir);
        session.setDataContainerType(TEST_CONTAINER_TYPE);

        boolean result = session.load();

        assertThat(result).isTrue();
        assertThat(session.isActive()).isFalse();
        assertThat(session.getVersion()).isEqualTo(1);
    }

    @Test
    void testFlushLoadRoundtrip() {
        Path sessionDir = sessionDir("flush-load-roundtrip");
        ChainSession<Map<String, Object>> session = activeSession(sessionDir);
        session.addDownstream("agent2", "session-2", new SharingPolicy(Permission.READ));
        assertThat(session.flush()).isTrue();
        SessionMeta originalMeta = session.toSessionMeta();

        ChainSession<Map<String, Object>> loaded = emptySession(sessionDir);
        assertThat(loaded.load()).isTrue();
        SessionMeta loadedMeta = loaded.toSessionMeta();

        assertThat(loadedMeta.getSessionId()).isEqualTo(originalMeta.getSessionId());
        assertThat(loadedMeta.isActive()).isEqualTo(originalMeta.isActive());
        assertThat(loadedMeta.getVersion()).isEqualTo(originalMeta.getVersion());
        assertThat(loaded.hasDownstream("agent2", "session-2")).isTrue();
    }

    @Test
    void testFlushExceptionReturnsFalse() {
        ChainSession<Map<String, Object>> session = activeSession(
                sessionDir("flush-exception"),
                new MapContainer(Map.of("key", "value"), Map.of(), true, false)
        );

        boolean result = session.flush();

        assertThat(result).isFalse();
    }

    @Test
    void testLoadInvalidJsonStateReturnsFalse() throws Exception {
        Path sessionDir = sessionDir("load-invalid-json");
        Files.createDirectories(sessionDir);
        Files.writeString(SessionPaths.stateFile(sessionDir), "NOT JSON{{{");
        ChainSession<Map<String, Object>> session = emptySession(sessionDir);

        boolean result = session.load();

        assertThat(result).isFalse();
    }

    @Test
    void testDefaultMetadataValues() {
        ChainSession<Map<String, Object>> session = emptySession(sessionDir("default-values"));

        assertThat(session.getCreatedAt()).isZero();
        assertThat(session.getUpdatedAt()).isZero();
        assertThat(session.getVersion()).isEqualTo(1);
        assertThat(session.isActive()).isFalse();
        assertThat(session.getAgentId()).isEqualTo("agent1");
        assertThat(session.getSessionId()).isEqualTo("session-1");
        assertThat(session.getSessionScope()).isEqualTo(sessionScope());
    }

    @Test
    void testIsActiveSetterUpdatesTimestamp() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("active-updates-ts"));
        double oldTimestamp = session.getUpdatedAt();

        session.setActive(true);

        assertThat(session.getUpdatedAt()).isGreaterThanOrEqualTo(oldTimestamp);
    }

    @Test
    void testIsActiveSetterFalseNoTimestampUpdate() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("active-false-no-ts"));
        session.setActive(true);
        double timestampAfterTrue = session.getUpdatedAt();

        session.setActive(false);

        assertThat(session.getUpdatedAt()).isEqualTo(timestampAfterTrue);
    }

    @Test
    void testConcurrentUpdateData() {
        ChainSession<Map<String, Object>> session = activeSession(sessionDir("concurrent-update"));
        List<CompletableFuture<Boolean>> tasks = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            int index = i;
            tasks.add(CompletableFuture.supplyAsync(() -> session.updateData(Map.of("key", "val_" + index))));
        }
        List<Boolean> results = tasks.stream().map(CompletableFuture::join).toList();

        assertThat(results).allMatch(Boolean.TRUE::equals);
        assertThat(session.getVersion()).isEqualTo(6);
    }

    @Test
    void testFieldScopesInSharingPolicy() {
        SharingPolicy policy = new SharingPolicy(Permission.READ, Set.of("f1", "f2"));

        assertThat(policy.getFieldScopes()).containsExactlyInAnyOrder("f1", "f2");
        assertThat(policy.getFieldScopes()).contains("f1");
        assertThat(policy.getFieldScopes()).doesNotContain("f3");
    }

    private ChainSession<Map<String, Object>> activeSession(Path sessionDir) {
        return activeSession(sessionDir, new MapContainer(Map.of("key", "value"), Map.of(), false, false));
    }

    private ChainSession<Map<String, Object>> activeSession(Path sessionDir, DataContainer container) {
        ChainSession<Map<String, Object>> session = new ChainSession<>(
                "agent1",
                sessionScope(),
                "session-1",
                container,
                sessionDir
        );
        session.updateFromMeta(SessionMeta.createNew("session-1"));
        session.setDataContainerType(TEST_CONTAINER_TYPE);
        return session;
    }

    private ChainSession<Map<String, Object>> emptySession(Path sessionDir) {
        ChainSession<Map<String, Object>> session = new ChainSession<>(
                "agent1",
                sessionScope(),
                "session-1",
                new MapContainer(Map.of(), Map.of(), false, false),
                sessionDir
        );
        session.setDataContainerType(TEST_CONTAINER_TYPE);
        return session;
    }

    private SessionScope sessionScope() {
        return new SessionScope(new MainScope(), new DirectSubject("user1"));
    }

    private Path sessionDir(String name) {
        return tempDir.resolve(name);
    }

    private static List<Path> linkFiles(Path sessionDir) throws IOException {
        Path downstreamsDir = SessionPaths.downstreamsDir(sessionDir);
        if (!Files.exists(downstreamsDir)) {
            return List.of();
        }
        try (var paths = Files.list(downstreamsDir)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".link")).toList();
        }
    }

    private static Map<String, Object> readMap(Path path) throws IOException {
        return OBJECT_MAPPER.readValue(path.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private static void writeJson(Path path, Object value) throws IOException {
        Files.createDirectories(path.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> stringMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static final class MapContainer implements DataContainer {
        private final LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        private final LinkedHashMap<String, Object> dumpValues = new LinkedHashMap<>();
        private final boolean failDump;
        private final boolean failUpdate;
        private final boolean dumpFollowsValues;

        private MapContainer(Map<String, Object> values,
                             Map<String, Object> dumpValues,
                             boolean failDump,
                             boolean failUpdate) {
            this.values.putAll(values);
            this.dumpValues.putAll(dumpValues);
            this.failDump = failDump;
            this.failUpdate = failUpdate;
            this.dumpFollowsValues = values == dumpValues;
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
            if (failUpdate) {
                throw new RuntimeException("fail");
            }
            values.putAll(data);
            if (dumpFollowsValues) {
                dumpValues.putAll(data);
            }
            return true;
        }

        @Override
        public CompletionStage<Object> dump() {
            if (failDump) {
                return CompletableFuture.failedFuture(new RuntimeException("disk error"));
            }
            return CompletableFuture.completedFuture(new LinkedHashMap<>(dumpValues));
        }
    }
}
