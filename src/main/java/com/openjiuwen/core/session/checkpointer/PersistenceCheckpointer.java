/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.core.foundation.store.kv.DbBasedKVStore;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.foundation.store.kv.ShelveStore;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.graph.store.Serializer;
import com.openjiuwen.core.graph.store.Store;
import com.openjiuwen.core.multitenant.TenantKVStoreKeyResolver;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.constants.SessionConstants;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.NodeSession;
import com.openjiuwen.core.session.state.AgentStateCollection;
import com.openjiuwen.core.session.state.SessionStateAccess;
import com.openjiuwen.core.session.state.WorkflowCommitState;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;

import javax.sql.DataSource;

/**
 * Persistent checkpointer backed by the shared {@link BaseKVStore} abstraction.
 *
 * <p>Mirrors Python's {@code PersistenceCheckpointer} in
 * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
 */
public class PersistenceCheckpointer extends Checkpointer {

    private final BaseKVStore kvStore;
    private final AgentStorage agentStorage;
    private final AgentTeamStorage agentTeamStorage;
    private final WorkflowStorage workflowStorage;
    private final PersistenceGraphStore graphStore;

    public PersistenceCheckpointer(BaseKVStore kvStore) {
        this.kvStore = Objects.requireNonNull(kvStore, "kvStore");
        this.agentStorage = new AgentStorage(kvStore);
        this.agentTeamStorage = new AgentTeamStorage(kvStore);
        this.workflowStorage = new WorkflowStorage(kvStore);
        this.graphStore = new PersistenceGraphStore(kvStore);
    }

    static Checkpointer createFromConfig(Map<String, Object> conf) {
        Map<String, Object> actualConf = conf == null ? Map.of() : conf;
        Object explicitStore = firstPresent(actualConf, "kv_store", "kvStore", "store");
        if (explicitStore instanceof BaseKVStore typedStore) {
            return new PersistenceCheckpointer(typedStore);
        }
        Object dbClient = firstPresent(actualConf, "db_client", "dbClient", "data_source", "dataSource");
        if (dbClient instanceof BaseKVStore typedStore) {
            return new PersistenceCheckpointer(typedStore);
        }
        if (dbClient instanceof DataSource dataSource) {
            return new PersistenceCheckpointer(new DbBasedKVStore(dataSource));
        }

        String dbType = String.valueOf(actualConf.getOrDefault("db_type", actualConf.getOrDefault("dbType", "sqlite")));
        String dbPath = String.valueOf(actualConf.getOrDefault("db_path", actualConf.getOrDefault("dbPath", "checkpointer")));
        return switch (dbType) {
            case "memory", "in_memory", "inmemory" -> new PersistenceCheckpointer(new InMemoryKVStore());
            case "shelve" -> new PersistenceCheckpointer(new ShelveStore(stripDbSuffix(dbPath)));
            case "sqlite" -> new PersistenceCheckpointer(new DbBasedKVStore(sqliteDataSource(normalizeSqlitePath(dbPath))));
            default -> throw ErrorHelper.buildError(
                    StatusCode.CHECKPOINTER_CONFIG_ERROR,
                    "reason",
                    "db type[" + dbType + "] is not supported"
            );
        };
    }

    @Override
    public void preAgentExecute(BaseSession session, Object inputs) {
        agentStorage.recover(session, null);
        if (inputs != null && session.state() != null) {
            session.state().update(mapOf(Constant.INTERACTIVE_INPUT, new ArrayList<>(List.of(inputs))));
        }
    }

    @Override
    public void preAgentTeamExecute(BaseSession session, Object inputs) {
        agentTeamStorage.recover(session, null);
        if (inputs != null && session.state() != null) {
            session.state().updateGlobal(mapOf(Constant.INTERACTIVE_INPUT, new ArrayList<>(List.of(inputs))));
        }
    }

    @Override
    public void interruptAgentExecute(BaseSession session) {
        agentStorage.save(session);
    }

    @Override
    public void postAgentExecute(BaseSession session) {
        agentStorage.save(session);
    }

    @Override
    public void postAgentTeamExecute(BaseSession session) {
        agentTeamStorage.save(session);
    }

    @Override
    public void preWorkflowExecute(BaseSession session, InteractiveInput inputs) {
        if (inputs != null) {
            workflowStorage.recover(session, inputs);
            return;
        }
        preWorkflowExecute(session, (Object) null);
    }

    @Override
    public void preWorkflowExecute(BaseSession session, Object inputs) {
        if (inputs instanceof InteractiveInput interactiveInput) {
            workflowStorage.recover(session, interactiveInput);
            return;
        }
        if (!workflowStorage.exists(session)) {
            return;
        }
        String workflowId = Checkpointer.workflowId(session);
        String sessionId = session.sessionId();
        if (Boolean.TRUE.equals(session.config().getEnv(SessionConstants.FORCE_DEL_WORKFLOW_STATE_KEY, false))) {
            graphStore.delete(sessionId, workflowId).toCompletableFuture().join();
            workflowStorage.clear(workflowId, sessionId);
            return;
        }
        // Workflow state exists and input is a non-InteractiveInput query (e.g. String).
        // Recover the saved state without interactive input processing so the workflow
        // can resume with the new query input. This mirrors Python's behavior where
        // a query recovery is treated as a valid resumption path.
        workflowStorage.recover(session, null);
    }

    @Override
    public void postWorkflowExecute(BaseSession session, Object result, Exception exception) {
        String workflowId = Checkpointer.workflowId(session);
        String sessionId = session.sessionId();
        if (exception != null) {
            workflowStorage.save(session);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(exception);
        }
        if (!containsInterrupt(result)) {
            graphStore.delete(sessionId, workflowId).toCompletableFuture().join();
            workflowStorage.clear(workflowId, sessionId);
            return;
        }
        workflowStorage.save(session);
    }

    @Override
    public boolean sessionExists(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }
        Map<String, Object> keys = kvStore.getByPrefix(sessionId + ":").join();
        return keys != null && !keys.isEmpty();
    }

    @Override
    public void release(String sessionId) {
        release(sessionId, null);
    }

    public void release(String sessionId, String agentId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        if (agentId != null) {
            agentStorage.clear(agentId, sessionId);
            return;
        }
        kvStore.deleteByPrefix(sessionId + ":", null).join();
    }

    @Override
    public Store graphStore() {
        return graphStore;
    }

    BaseKVStore kvStore() {
        return kvStore;
    }

    private static boolean containsInterrupt(Object result) {
        if (result instanceof Map<?, ?> map) {
            return map.get(PregelConstants.TASK_STATUS_INTERRUPT) != null;
        }
        return false;
    }

    private static Object firstPresent(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private static String stripDbSuffix(String dbPath) {
        if (dbPath != null && dbPath.endsWith(".db")) {
            return dbPath.substring(0, dbPath.length() - 3);
        }
        return dbPath == null || dbPath.isBlank() ? "checkpointer" : dbPath;
    }

    private static String normalizeSqlitePath(String dbPath) {
        String actual = dbPath == null || dbPath.isBlank() ? "checkpointer" : dbPath;
        if (actual.startsWith("jdbc:")) {
            return actual;
        }
        if (!actual.endsWith(".db")) {
            actual = actual + ".db";
        }
        if (!actual.startsWith(":memory:")) {
            try {
                Path parent = Path.of(actual).toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to initialize sqlite checkpointer path", exception);
            }
        }
        return actual;
    }

    private static DataSource sqliteDataSource(String dbPath) {
        String jdbcUrl = dbPath.startsWith("jdbc:") ? dbPath : "jdbc:sqlite:" + dbPath;
        return new DriverManagerDataSource(jdbcUrl);
    }

    private static Map<String, Object> mapOf(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    /**
     * Shared serializer and KV helpers.
     *
     * <p>Mirrors Python's {@code BaseStorage} in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private abstract static class BaseStorage {
        private static final String EMPTY_TYPE = "empty";

        protected final BaseKVStore kvStore;
        private final Serializer serializer = Serializer.create("java");

        BaseStorage(BaseKVStore kvStore) {
            this.kvStore = kvStore;
        }

        protected Serializer.TypedBytes serializeState(Object state) {
            return serializer.dumpsTyped(state);
        }

        protected Object deserializeState(Object dumpType, Object blob) {
            String type = decodeDumpType(dumpType);
            if (type.isEmpty() || EMPTY_TYPE.equals(type) || blob == null) {
                return null;
            }
            byte[] bytes = blob instanceof byte[] byteArray ? byteArray : decodeBlob(String.valueOf(blob));
            return serializer.loadsTyped(new Serializer.TypedBytes(type, bytes));
        }

        protected String decodeDumpType(Object dumpType) {
            if (dumpType == null) {
                return "";
            }
            if (dumpType instanceof byte[] bytes) {
                return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            }
            return String.valueOf(dumpType);
        }

        protected List<Object> execute(BasedKVStorePipeline pipeline) {
            return pipeline.execute().join();
        }

        protected void set(BasedKVStorePipeline pipeline, String key, Object value) {
            pipeline.set(key, value, null).join();
        }

        @SuppressWarnings("unchecked")
        protected Map<String, Object> toMap(Object value) {
            if (!(value instanceof Map<?, ?> map)) {
                return new LinkedHashMap<>();
            }
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return copy;
        }

        private byte[] decodeBlob(String blob) {
            try {
                return Base64.getDecoder().decode(blob);
            } catch (IllegalArgumentException ignored) {
                return blob.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Common storage behavior for single entity state.
     *
     * <p>Mirrors Python's {@code BaseSingleStateStorage} in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private abstract static class BaseSingleStateStorage extends BaseStorage {
        private static final int KEY_COUNT = 2;

        BaseSingleStateStorage(BaseKVStore kvStore) {
            super(kvStore);
        }

        abstract String namespace();

        abstract String stateBlobsKey();

        abstract String stateDumpTypeKey();

        abstract String entityId(BaseSession session);

        abstract Object stateToSave(BaseSession session);

        abstract void restoreState(BaseSession session, Object state);

        void save(BaseSession session) {
            if (session == null) {
                return;
            }
            Serializer.TypedBytes typedBytes = serializeState(stateToSave(session));
            String sessionId = session.sessionId();
            String entityId = entityId(session);
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            set(pipeline, stateDumpTypeKey(sessionId, entityId), typedBytes.type());
            set(pipeline, stateBlobKey(sessionId, entityId), typedBytes.data());
            execute(pipeline);
        }

        void recover(BaseSession session, InteractiveInput inputs) {
            if (session == null) {
                return;
            }
            String sessionId = session.sessionId();
            String entityId = entityId(session);
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(stateDumpTypeKey(sessionId, entityId)).join();
            pipeline.get(stateBlobKey(sessionId, entityId)).join();
            List<Object> result = execute(pipeline);
            if (result.size() != KEY_COUNT) {
                return;
            }
            Object state = deserializeState(result.get(0), result.get(1));
            if (state != null) {
                restoreState(session, state);
            }
        }

        void clear(String entityId, String sessionId) {
            kvStore.batchDelete(List.of(
                    stateDumpTypeKey(sessionId, entityId),
                    stateBlobKey(sessionId, entityId)
            ), null).join();
        }

        boolean exists(BaseSession session) {
            String sessionId = session.sessionId();
            String entityId = entityId(session);
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            pipeline.exists(stateDumpTypeKey(sessionId, entityId)).join();
            pipeline.exists(stateBlobKey(sessionId, entityId)).join();
            List<Object> result = execute(pipeline);
            return result.size() == KEY_COUNT
                    && Boolean.TRUE.equals(result.get(0))
                    && Boolean.TRUE.equals(result.get(1));
        }

        private String stateBlobKey(String sessionId, String entityId) {
            return Checkpointer.resolveNsKey(sessionId, namespace(), entityId, stateBlobsKey());
        }

        private String stateDumpTypeKey(String sessionId, String entityId) {
            return Checkpointer.resolveNsKey(sessionId, namespace(), entityId, stateDumpTypeKey());
        }
    }

    /**
     * Agent state storage.
     *
     * <p>Mirrors Python's {@code AgentStorage} in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private static final class AgentStorage extends BaseSingleStateStorage {
        AgentStorage(BaseKVStore kvStore) {
            super(kvStore);
        }

        @Override
        String namespace() {
            return SESSION_NAMESPACE_AGENT;
        }

        @Override
        String stateBlobsKey() {
            return "agent_state_blobs";
        }

        @Override
        String stateDumpTypeKey() {
            return "agent_state_blobs_dump_type";
        }

        @Override
        String entityId(BaseSession session) {
            return agentId(session);
        }

        @Override
        Object stateToSave(BaseSession session) {
            return session.state() == null ? Map.of() : session.state().getState();
        }

        @Override
        void restoreState(BaseSession session, Object state) {
            SessionStateAccess sessionState = session.state();
            if (sessionState != null) {
                sessionState.setState(toMap(state));
            }
        }
    }

    /**
     * Agent-team global state storage.
     *
     * <p>Mirrors Python's {@code AgentTeamStorage} in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private static final class AgentTeamStorage extends BaseSingleStateStorage {
        AgentTeamStorage(BaseKVStore kvStore) {
            super(kvStore);
        }

        @Override
        String namespace() {
            return SESSION_NAMESPACE_AGENT_TEAM;
        }

        @Override
        String stateBlobsKey() {
            return "agent_team_state_blobs";
        }

        @Override
        String stateDumpTypeKey() {
            return "agent_team_state_blobs_dump_type";
        }

        @Override
        String entityId(BaseSession session) {
            return teamId(session);
        }

        @Override
        Object stateToSave(BaseSession session) {
            return session.state() == null ? Map.of() : session.state().getGlobal(null);
        }

        @Override
        void restoreState(BaseSession session, Object state) {
            SessionStateAccess sessionState = session.state();
            if (sessionState instanceof AgentStateCollection agentStateCollection) {
                agentStateCollection.getGlobalStateLike().setState(toMap(state));
                return;
            }
            if (sessionState != null) {
                sessionState.updateGlobal(toMap(state));
            }
        }
    }

    /**
     * Workflow state and update storage.
     *
     * <p>Mirrors Python's {@code WorkflowStorage} in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private static final class WorkflowStorage extends BaseStorage {
        private static final String STATE_BLOBS = "workflow_state_blobs";
        private static final String STATE_BLOBS_DUMP_TYPE = "workflow_state_blobs_dump_type";
        private static final String UPDATE_BLOBS = "workflow_update_blobs";
        private static final String UPDATE_BLOBS_DUMP_TYPE = "workflow_update_blobs_dump_type";
        private static final int KEY_COUNT = 4;

        WorkflowStorage(BaseKVStore kvStore) {
            super(kvStore);
        }

        void save(BaseSession session) {
            if (session == null || session.state() == null) {
                return;
            }
            String sessionId = session.sessionId();
            String workflowId = Checkpointer.workflowId(session);
            Serializer.TypedBytes stateBlob = serializeState(session.state().getState());
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            set(pipeline, key(sessionId, workflowId, STATE_BLOBS_DUMP_TYPE), stateBlob.type());
            set(pipeline, key(sessionId, workflowId, STATE_BLOBS), stateBlob.data());
            if (session.state() instanceof WorkflowCommitState workflowState) {
                Serializer.TypedBytes updateBlob = serializeState(workflowState.getUpdates());
                set(pipeline, key(sessionId, workflowId, UPDATE_BLOBS_DUMP_TYPE), updateBlob.type());
                set(pipeline, key(sessionId, workflowId, UPDATE_BLOBS), updateBlob.data());
            }
            execute(pipeline);
        }

        void recover(BaseSession session, InteractiveInput inputs) {
            if (session == null || session.state() == null) {
                return;
            }
            String sessionId = session.sessionId();
            String workflowId = Checkpointer.workflowId(session);
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(key(sessionId, workflowId, STATE_BLOBS_DUMP_TYPE)).join();
            pipeline.get(key(sessionId, workflowId, STATE_BLOBS)).join();
            pipeline.get(key(sessionId, workflowId, UPDATE_BLOBS_DUMP_TYPE)).join();
            pipeline.get(key(sessionId, workflowId, UPDATE_BLOBS)).join();
            List<Object> result = execute(pipeline);
            if (result.size() != KEY_COUNT) {
                return;
            }
            Object state = deserializeState(result.get(0), result.get(1));
            if (state != null) {
                session.state().setState(toMap(state));
            }
            if (inputs != null) {
                processInteractiveInputs(session, inputs);
            }
            Object updates = deserializeState(result.get(2), result.get(3));
            if (updates != null && session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.setUpdates(toMap(updates));
            }
        }

        void clear(String workflowId, String sessionId) {
            kvStore.batchDelete(List.of(
                    key(sessionId, workflowId, STATE_BLOBS_DUMP_TYPE),
                    key(sessionId, workflowId, STATE_BLOBS),
                    key(sessionId, workflowId, UPDATE_BLOBS_DUMP_TYPE),
                    key(sessionId, workflowId, UPDATE_BLOBS)
            ), null).join();
        }

        boolean exists(BaseSession session) {
            String sessionId = session.sessionId();
            String workflowId = Checkpointer.workflowId(session);
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            pipeline.exists(key(sessionId, workflowId, STATE_BLOBS_DUMP_TYPE)).join();
            pipeline.exists(key(sessionId, workflowId, STATE_BLOBS)).join();
            List<Object> result = execute(pipeline);
            return result.size() == 2 && Boolean.TRUE.equals(result.get(0)) && Boolean.TRUE.equals(result.get(1));
        }

        private void processInteractiveInputs(BaseSession session, InteractiveInput inputs) {
            if (inputs.getRawInputs() != null) {
                if (session.state() instanceof WorkflowCommitState workflowState) {
                    workflowState.updateAndCommitWorkflowState(mapOf(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                } else {
                    session.state().update(mapOf(Constant.INTERACTIVE_INPUT, inputs.getRawInputs()));
                }
                return;
            }
            Map<String, Object> userInputs = inputs.getUserInputs();
            if (userInputs == null || userInputs.isEmpty()) {
                return;
            }
            // Also store at workflow level so popWorkflowInteractiveInput can find it.
            // Store userInputs values as a List, matching the rawInputs format.
            if (session.state() instanceof WorkflowCommitState workflowState) {
                Object existing = workflowState.getWorkflowState(Constant.INTERACTIVE_INPUT);
                List<Object> values = existing instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
                values.addAll(userInputs.values());
                workflowState.updateAndCommitWorkflowState(mapOf(Constant.INTERACTIVE_INPUT, values));
            }
            for (Map.Entry<String, Object> entry : userInputs.entrySet()) {
                NodeSession nodeSession = new NodeSession(session, entry.getKey());
                Object interactiveInput = nodeSession.state() == null
                        ? null
                        : nodeSession.state().get(Constant.INTERACTIVE_INPUT);
                List<Object> values = interactiveInput instanceof List<?> list
                        ? new ArrayList<>(list)
                        : new ArrayList<>();
                values.add(entry.getValue());
                if (nodeSession.state() != null) {
                    nodeSession.state().update(mapOf(Constant.INTERACTIVE_INPUT, values));
                }
            }
            if (session.state() instanceof WorkflowCommitState workflowState) {
                workflowState.commit();
            }
        }

        private String key(String sessionId, String workflowId, String suffix) {
            return Checkpointer.resolveNsKey(sessionId, SESSION_NAMESPACE_WORKFLOW, workflowId, suffix);
        }
    }

    /**
     * Graph checkpoint storage backed by the persistence KV store.
     *
     * <p>Mirrors Python's {@code GraphStore} in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private static final class PersistenceGraphStore extends BaseStorage implements Store {
        private static final String DATA_TYPE = "checkpoint_data_type";
        private static final String DATA_VALUE = "checkpoint_data_value";

        PersistenceGraphStore(BaseKVStore kvStore) {
            super(kvStore);
        }

        @Override
        public CompletionStage<Optional<GraphStoreState>> get(String sessionId, String ns) {
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            pipeline.get(key(sessionId, ns, DATA_TYPE)).join();
            pipeline.get(key(sessionId, ns, DATA_VALUE)).join();
            List<Object> result = execute(pipeline);
            if (result.size() != 2) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
            Object graphState = deserializeState(result.get(0), result.get(1));
            if (graphState instanceof GraphStoreState typedState) {
                return CompletableFuture.completedFuture(Optional.of(typedState));
            }
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public CompletionStage<Void> save(String sessionId, String ns, GraphStoreState state) {
            Serializer.TypedBytes typedBytes = serializeState(state);
            BasedKVStorePipeline pipeline = kvStore.pipeline();
            set(pipeline, key(sessionId, ns, DATA_TYPE), typedBytes.type());
            set(pipeline, key(sessionId, ns, DATA_VALUE), typedBytes.data());
            execute(pipeline);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> delete(String sessionId, String ns) {
            String prefix = ns == null || ns.isBlank()
                    ? TenantKVStoreKeyResolver.resolvePrefix(Checkpointer.buildKey(sessionId, WORKFLOW_NAMESPACE_GRAPH))
                    : Checkpointer.resolveNsPrefix(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns);
            kvStore.deleteByPrefix(prefix, null).join();
            return CompletableFuture.completedFuture(null);
        }

        private String key(String sessionId, String ns, String suffix) {
            return Checkpointer.resolveNsKey(sessionId, WORKFLOW_NAMESPACE_GRAPH, ns, suffix);
        }
    }

    /**
     * Minimal JDBC data source for sqlite-style checkpointer configuration.
     *
     * <p>Mirrors Python's sqlite engine creation in
     * {@code openjiuwen/core/session/checkpointer/persistence.py}.</p>
     */
    private static final class DriverManagerDataSource implements DataSource {
        private final String jdbcUrl;

        DriverManagerDataSource(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(jdbcUrl);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return DriverManager.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            DriverManager.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            DriverManager.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return DriverManager.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            throw new SQLException("DataSource cannot unwrap " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return iface.isInstance(this);
        }
    }
}
