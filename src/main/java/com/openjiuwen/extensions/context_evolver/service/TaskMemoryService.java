/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.context_evolver.service;

import com.openjiuwen.extensions.context_evolver.core.config.Config;
import com.openjiuwen.extensions.context_evolver.core.context.RuntimeContext;
import com.openjiuwen.extensions.context_evolver.core.context.ServiceContext;
import com.openjiuwen.extensions.context_evolver.core.op.BaseOp;
import com.openjiuwen.extensions.context_evolver.core.op.SequentialOp;
import com.openjiuwen.extensions.context_evolver.core.MemoryPersistenceHelper;
import com.openjiuwen.extensions.context_evolver.core.schema.VectorNode;
import com.openjiuwen.extensions.context_evolver.core.vector_store.MemoryVectorStore;
import com.openjiuwen.extensions.context_evolver.retrieve.task.ace.RecallMemoryOp;
import com.openjiuwen.extensions.context_evolver.schema.ACEMemory;
import com.openjiuwen.extensions.context_evolver.schema.ACERetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReMeMemoryMetadata;
import com.openjiuwen.extensions.context_evolver.schema.ReMeRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemory;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankMemoryItem;
import com.openjiuwen.extensions.context_evolver.schema.ReasoningBankRetrievedMemory;
import com.openjiuwen.extensions.context_evolver.schema.RetrieveResponse;
import com.openjiuwen.extensions.context_evolver.schema.SchemaUtils;
import com.openjiuwen.extensions.context_evolver.schema.SummarizeResponse;
import com.openjiuwen.extensions.context_evolver.schema.memory.PersonalMemory;
import com.openjiuwen.extensions.context_evolver.schema.memory.TaskMemory;
import com.openjiuwen.extensions.context_evolver.summary.task.ace.ApplyDeltaOp;
import com.openjiuwen.extensions.context_evolver.summary.task.ace.CurateOp;
import com.openjiuwen.extensions.context_evolver.summary.task.ace.LoadPlaybookOp;
import com.openjiuwen.extensions.context_evolver.summary.task.ace.ReflectOp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mirrors Python's {@code TaskMemoryService} in
 * {@code openjiuwen/extensions/context_evolver/service/task_memory_service.py}.
 *
 * Service for task memory retrieval and summarization.
 */
public class TaskMemoryService {

    private static final Logger log = LoggerFactory.getLogger(TaskMemoryService.class);

    private final ServiceContext serviceContext;
    private final MemoryVectorStore vectorStore;
    private String retrievalAlgorithm;
    private String summaryAlgorithm;
    private final String persistType;
    private final String persistPath;
    private final String milvusHost;
    private final int milvusPort;
    private final String milvusCollection;
    private final MemoryPersistenceHelper persistenceHelper;
    private BaseOp retrieveFlow;
    private BaseOp summaryFlow;

    public TaskMemoryService() {
        this(null, null, null, null, null, null);
    }

    public TaskMemoryService(
            String llmModel,
            String embeddingModel,
            String apiKey,
            String retrievalAlgo,
            String summaryAlgo
    ) {
        this(llmModel, embeddingModel, apiKey, retrievalAlgo, summaryAlgo, null);
    }

    public TaskMemoryService(
            String llmModel,
            String embeddingModel,
            String apiKey,
            String retrievalAlgo,
            String summaryAlgo,
            String configPath
    ) {
        this(llmModel, embeddingModel, apiKey, retrievalAlgo, summaryAlgo, configPath, null, null);
    }

    public TaskMemoryService(
            String llmModel,
            String embeddingModel,
            String apiKey,
            String retrievalAlgo,
            String summaryAlgo,
            String configPath,
            String persistType,
            String persistPath
    ) {
        this(llmModel, embeddingModel, apiKey, retrievalAlgo, summaryAlgo, configPath,
            persistType, persistPath, null, 19530, null);
    }

    public TaskMemoryService(
            String llmModel,
            String embeddingModel,
            String apiKey,
            String retrievalAlgo,
            String summaryAlgo,
            String configPath,
            String persistType,
            String persistPath,
            String milvusHost,
            int milvusPort,
            String milvusCollection
    ) {
        if (configPath != null && !configPath.isBlank()) {
            Config.restore(new LinkedHashMap<>());
            Config.load(configPath, null);
        } else {
            Config.load();
        }

        this.serviceContext = new ServiceContext();
        this.vectorStore = new MemoryVectorStore();

        String llm = llmModel != null
            ? llmModel
            : configString("MODEL_NAME", configString("LLM_MODEL", "gpt-5.2"));
        String embedding = embeddingModel != null
            ? embeddingModel
            : configString("EMBEDDING_MODEL", "text-embedding-3-small");
        String resolvedApiKey = apiKey != null ? apiKey : configString("API_KEY", null);

        serviceContext.registerService("llm", llm);
        serviceContext.registerService("embedding_model", embedding);
        serviceContext.registerService("vector_store", vectorStore);
        if (resolvedApiKey != null) {
            serviceContext.registerService("api_key", resolvedApiKey);
        }

        this.retrievalAlgorithm = normalizeAlgoName(
            retrievalAlgo != null ? retrievalAlgo : configString("RETRIEVAL_ALGO", "ACE")
        );
        this.summaryAlgorithm = normalizeAlgoName(
            summaryAlgo != null ? summaryAlgo : configString("SUMMARY_ALGO", "ACE")
        );
        this.persistType = normalizePersistType(
            persistType != null ? persistType : configString("PERSIST_TYPE", "json")
        );
        this.persistPath = persistPath != null && !persistPath.isBlank()
            ? persistPath
            : configString("PERSIST_PATH", "./memories/{algo_name}/{user_id}.json");
        this.milvusHost = milvusHost != null && !milvusHost.isBlank()
            ? milvusHost
            : configString("MILVUS_HOST", "localhost");
        this.milvusPort = milvusPort > 0 ? milvusPort : configInt("MILVUS_PORT", 19530);
        this.milvusCollection = milvusCollection != null && !milvusCollection.isBlank()
            ? milvusCollection
            : configString("MILVUS_COLLECTION", "vector_nodes");
        this.persistenceHelper = this.persistType != null
            ? new MemoryPersistenceHelper(
                this.persistType,
                this.persistPath,
                this.milvusHost,
                this.milvusPort,
                this.milvusCollection
            )
            : null;

        this.retrieveFlow = createRetrieveFlow();
        this.summaryFlow = createSummaryFlow();

        log.info(
            "TaskMemoryService initialized with retrieval={}, summary={}, persistence={}",
            retrievalAlgorithm,
            summaryAlgorithm,
            this.persistType != null ? this.persistType : "disabled"
        );
    }

    public static String normalizeAlgoName(String algo) {
        if (algo == null) {
            return "ACE";
        }
        String upper = algo.toUpperCase();
        if ("RB".equals(upper) || "REASONINGBANK".equals(upper)) {
            return "ReasoningBank";
        }
        if ("REME".equals(upper)) {
            return "ReMe";
        }
        if ("REFCON".equals(upper)) {
            return "RefCon";
        }
        if ("DIVCON".equals(upper)) {
            return "DivCon";
        }
        if ("ACE".equals(upper)) {
            return "ACE";
        }
        throw new IllegalArgumentException(
            "Invalid algorithm '" + algo + "'. Must be one of: ACE, ReasoningBank, ReMe, RefCon, DivCon."
        );
    }

    private String normalizePersistType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return type.toLowerCase(Locale.ROOT);
    }

    private BaseOp createRetrieveFlow() {
        return switch (retrievalAlgorithm) {
            case "ReasoningBank" ->
                new com.openjiuwen.extensions.context_evolver.retrieve.task.reasoning_bank.RecallMemoryOp(
                    configInt("TOPK_QUERY", 1)
                );
            case "ReMe", "RefCon", "DivCon" -> new SequentialOp(
                new com.openjiuwen.extensions.context_evolver.retrieve.task.reme.RecallMemoryOp(
                    configInt("TOPK_RETRIEVAL", 10)
                ),
                new ReMeRerankMemoryOp(
                    configBoolean("LLM_RERANK", true),
                    configInt("TOPK_RERANK", 5)
                ),
                new ReMeRewriteMemoryOp(configBoolean("LLM_REWRITE", true))
            );
            default -> new RecallMemoryOp();
        };
    }

    private BaseOp createSummaryFlow() {
        if ("ACE".equals(summaryAlgorithm)) {
            boolean useGroundTruth = configBoolean("USE_GROUNDTRUTH", false);
            int maxPlaybookSize = configInt("MAX_PLAYBOOK_SIZE", 50);
            return new SequentialOp(
                new LoadPlaybookOp(),
                new ReflectOp(useGroundTruth),
                new CurateOp(),
                new ApplyDeltaOp(maxPlaybookSize),
                new UpdateVectorStoreOp(vectorStore, persistenceHelper, summaryAlgorithm)
            );
        }
        if ("ReasoningBank".equals(summaryAlgorithm)) {
            return new SequentialOp(
                new ReasoningBankSummarizeMemoryOp(),
                new UpdateVectorStoreOp(vectorStore, persistenceHelper, summaryAlgorithm)
            );
        }
        if ("ReMe".equals(summaryAlgorithm) || "RefCon".equals(summaryAlgorithm)
                || "DivCon".equals(summaryAlgorithm)) {
            return new SequentialOp(
                new ReMeSummarizeMemoryOp(
                    configBoolean("EXTRACT_BEST_TRAJ", true),
                    configBoolean("EXTRACT_WORST_TRAJ", true),
                    configBoolean("EXTRACT_COMPARATIVE_TRAJ", true),
                    configBoolean("MEMORY_VALIDATION", true),
                    configBoolean("MEMORY_DEDUPLICATION", true)
                ),
                new UpdateVectorStoreOp(vectorStore, persistenceHelper, summaryAlgorithm)
            );
        }
        return new SequentialOp(new UpdateVectorStoreOp(vectorStore, persistenceHelper, summaryAlgorithm));
    }

    public String getRetrievalAlgorithm() {
        return retrievalAlgorithm;
    }

    public String getSummaryAlgorithm() {
        return summaryAlgorithm;
    }

    public String getPersistType() {
        return persistType;
    }

    public String getPersistPath() {
        return persistPath;
    }

    public String getMilvusHost() {
        return milvusHost;
    }

    public int getMilvusPort() {
        return milvusPort;
    }

    public String getMilvusCollection() {
        return milvusCollection;
    }

    public void reconfigure(String algorithm) {
        String normalized = normalizeAlgoName(algorithm);
        retrievalAlgorithm = normalized;
        summaryAlgorithm = normalized;
        retrieveFlow = createRetrieveFlow();
        summaryFlow = createSummaryFlow();
        log.info(
            "TaskMemoryService reconfigured: retrieval={}, summary={}",
            retrievalAlgorithm,
            summaryAlgorithm
        );
    }

    public MemoryPersistenceHelper getPersistenceHelper() {
        return persistenceHelper;
    }

    public MemoryVectorStore getVectorStore() {
        return vectorStore;
    }

    public BaseOp getRetrieveFlow() {
        return retrieveFlow;
    }

    public BaseOp getSummaryFlow() {
        return summaryFlow;
    }

    public void loadMemories(String userId) {
        if (persistenceHelper == null || userId == null || userId.isBlank()) {
            return;
        }

        String algoName = persistenceAlgoName(summaryAlgorithm);
        try {
            Map<String, Object> data = persistenceHelper.load(userId, algoName);
            if (data == null || data.isEmpty()) {
                return;
            }
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> nodeData)) {
                    continue;
                }
                VectorNode node = VectorNode.fromDict(SchemaUtils.mapValue(nodeData));
                if (node.getEmbedding() == null) {
                    node.setEmbedding(defaultEmbeddingFor(node.getContent()));
                }
                vectorStore.loadNode(entry.getKey(), node);
            }
            log.info("Loaded {} persisted {} memories for user={}", data.size(), algoName, userId);
        } catch (Exception error) {
            log.warn("Failed to load persisted memories for user={}: {}", userId, error.getMessage());
        }
    }

    public CompletableFuture<RetrieveResponse> retrieveResponse(String userId, String query) {
        log.info(
            "Retrieving task memory for user={}, query='{}'",
            userId,
            query != null && query.length() > 50 ? query.substring(0, 50) + "..." : query
        );

        RuntimeContext context = new RuntimeContext();
        context.set("user_id", userId);
        context.set("query", query);

        return retrieveFlow.execute(context).thenApply(ignored -> {
            List<Object> normalized = normalizeRetrievedMemories(contextList(context, "retrieved_memories"));
            String memoryString = switch (retrievalAlgorithm) {
                case "ReMe", "RefCon", "DivCon", "Our" -> {
                    String rewritten = contextString(context, "memory_string", "");
                    yield rewritten != null && !rewritten.isBlank() ? rewritten : formatMemoryString(normalized);
                }
                default -> formatMemoryString(normalized);
            };
            return new RetrieveResponse("success", memoryString, normalized);
        });
    }

    public CompletableFuture<Map<String, Object>> retrieve(String userId, String query) {
        return retrieveResponse(userId, query).thenApply(RetrieveResponse::toMap);
    }

    public CompletableFuture<SummarizeResponse> summarizeResponse(
            String userId,
            String matts,
            String query,
            List<?> trajectories
    ) {
        return summarizeResponse(userId, matts, query, trajectories, null, null);
    }

    public CompletableFuture<Map<String, Object>> summarize(
            String userId,
            String matts,
            String query,
            List<?> trajectories
    ) {
        return summarizeResponse(userId, matts, query, trajectories).thenApply(SummarizeResponse::toMap);
    }

    public CompletableFuture<SummarizeResponse> summarizeResponse(
            String userId,
            String matts,
            String query,
            List<?> trajectories,
            List<Boolean> labels,
            List<? extends Number> scores
    ) {
        log.info("Summarizing {} trajectories for user={}", trajectories != null ? trajectories.size() : 0, userId);

        RuntimeContext context = new RuntimeContext();
        context.set("user_id", userId);
        context.set("matts", matts != null ? matts : "none");
        context.set("query", query);
        context.set("trajectories", trajectories != null ? trajectories : List.of());
        if (labels != null) {
            context.set("label", labels);
        }
        if (scores != null) {
            context.set("score", normalizeScores(scores));
        }

        return summaryFlow.execute(context).thenApply(ignored -> {
            List<Object> normalized = normalizeSummarizedMemories(contextList(context, "memories"), userId);
            return new SummarizeResponse("success", normalized);
        });
    }

    public CompletableFuture<Map<String, Object>> summarize(
            String userId,
            String matts,
            String query,
            List<?> trajectories,
            List<Boolean> labels,
            List<? extends Number> scores
    ) {
        return summarizeResponse(userId, matts, query, trajectories, labels, scores)
            .thenApply(SummarizeResponse::toMap);
    }

    public CompletableFuture<Map<String, Object>> addMemory(String userId, AddMemoryRequest request) {
        try {
            VectorNode node = createManualMemoryNode(userId, request);
            node.setEmbedding(defaultEmbeddingFor(node.getContent()));

            return vectorStore.asyncUpsert(node).thenApply(ignored -> {
                persistNodes(userId, List.of(node));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "success");
                result.put("memory_id", node.getId());
                result.put("user_id", userId);
                result.put("algorithm", summaryAlgorithm);
                return result;
            });
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    public CompletableFuture<Map<String, Object>> getPlaybook(String userId) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("workspace_id", userId);

        List<Map<String, Object>> memories = new ArrayList<>();
        for (VectorNode node : vectorStore.getAll(filter)) {
            memories.add(payloadMap(decodeStoredMemory(node)));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", userId);
        result.put("memory_count", memories.size());
        result.put("memories", memories);
        return CompletableFuture.completedFuture(result);
    }

    public CompletableFuture<Map<String, Object>> clearPlaybook(String userId) {
        log.warn("Clearing playbook for user={}", userId);

        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("workspace_id", userId);
        List<VectorNode> existing = vectorStore.getAll(filter);

        List<CompletableFuture<Boolean>> deletions = new ArrayList<>();
        for (VectorNode node : existing) {
            deletions.add(vectorStore.asyncDelete(node.getId()));
        }

        return CompletableFuture.allOf(deletions.toArray(CompletableFuture[]::new)).thenApply(ignored -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success");
            result.put("message", "Cleared playbook for user " + userId);
            return result;
        });
    }

    private VectorNode createManualMemoryNode(String userId, AddMemoryRequest request) {
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("Memory content is required");
        }

        return switch (summaryAlgorithm) {
            case "ReasoningBank" -> createReasoningBankMemory(userId, request).toVectorNode();
            case "ReMe", "RefCon", "DivCon", "Our" -> createReMeMemory(userId, request).toVectorNode();
            default -> createAceMemory(userId, request).toVectorNode();
        };
    }

    private ACEMemory createAceMemory(String userId, AddMemoryRequest request) {
        String section = request.getSection();
        if (section == null || section.isBlank()) {
            throw new IllegalArgumentException("ACE algorithm requires 'content' and 'section' parameters");
        }

        Instant now = Instant.now();
        ACEMemory memory = new ACEMemory(
            ACEMemory.generateId(section, request.getContent()),
            section,
            request.getContent()
        );
        memory.setWorkspaceId(userId);
        memory.setHelpful(0);
        memory.setHarmful(0);
        memory.setNeutral(0);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        return memory;
    }

    private ReasoningBankMemory createReasoningBankMemory(String userId, AddMemoryRequest request) {
        if (request.getTitle() == null || request.getDescription() == null) {
            throw new IllegalArgumentException(
                "ReasoningBank requires 'title', 'description' and 'content' parameters"
            );
        }

        ReasoningBankMemory memory = new ReasoningBankMemory();
        memory.setWorkspaceId(userId);
        memory.setQuery(request.getQuery() != null ? request.getQuery() : request.getDescription());
        memory.setLabel(request.getLabel());
        memory.setMemory(List.of(
            new ReasoningBankMemoryItem(request.getTitle(), request.getDescription(), request.getContent())
        ));
        return memory;
    }

    private ReMeMemory createReMeMemory(String userId, AddMemoryRequest request) {
        if (request.getWhenToUse() == null || request.getWhenToUse().isBlank()) {
            throw new IllegalArgumentException("ReMe algorithm requires 'when_to_use' parameter");
        }

        Instant now = Instant.now();
        ReMeMemoryMetadata metadata = new ReMeMemoryMetadata();
        metadata.setTags(List.of());
        metadata.setStepType("manual");
        metadata.setToolsUsed(List.of());
        metadata.setConfidence(1.0d);
        metadata.setFreq(1);
        metadata.setUtility(1.0d);

        ReMeMemory memory = new ReMeMemory();
        memory.setWorkspaceId(userId);
        memory.setWhenToUse(request.getWhenToUse());
        memory.setContent(request.getContent());
        memory.setScore(1.0d);
        memory.setCreatedAt(now);
        memory.setUpdatedAt(now);
        memory.setMetadata(metadata);
        return memory;
    }

    private List<Double> normalizeScores(List<? extends Number> scores) {
        List<Double> normalized = new ArrayList<>();
        if (scores == null) {
            return normalized;
        }
        for (Number score : scores) {
            if (score != null) {
                normalized.add(score.doubleValue());
            }
        }
        return normalized;
    }

    private List<Object> normalizeRetrievedMemories(List<?> rawMemories) {
        List<Object> normalized = new ArrayList<>();
        if (rawMemories == null) {
            return normalized;
        }

        for (Object raw : rawMemories) {
            Object converted = switch (retrievalAlgorithm) {
                case "ReasoningBank" -> toReasoningBankRetrievedMemory(raw);
                case "ReMe", "RefCon", "DivCon", "Our" -> toReMeRetrievedMemory(raw);
                default -> toAceRetrievedMemory(raw);
            };
            if (converted != null) {
                normalized.add(converted);
            }
        }
        return normalized;
    }

    private List<Object> normalizeSummarizedMemories(List<?> rawMemories, String userId) {
        List<Object> normalized = new ArrayList<>();
        if (rawMemories == null) {
            return normalized;
        }

        for (Object raw : rawMemories) {
            Object converted = switch (summaryAlgorithm) {
                case "ReasoningBank" -> toReasoningBankMemory(raw, userId);
                case "ReMe", "RefCon", "DivCon", "Our" -> toReMeMemory(raw, userId);
                default -> toAceMemory(raw, userId);
            };
            if (converted != null) {
                normalized.add(converted);
            }
        }
        return normalized;
    }

    private ACERetrievedMemory toAceRetrievedMemory(Object raw) {
        if (raw instanceof ACERetrievedMemory aceRetrievedMemory) {
            return aceRetrievedMemory;
        }
        if (raw instanceof ACEMemory aceMemory) {
            return aceMemory.toRetrievedMemory();
        }
        if (raw instanceof VectorNode node) {
            return ACERetrievedMemory.fromVectorNode(node);
        }
        if (raw instanceof Map<?, ?>) {
            return ACERetrievedMemory.fromMap(SchemaUtils.mapValue(raw));
        }
        return null;
    }

    private ReasoningBankRetrievedMemory toReasoningBankRetrievedMemory(Object raw) {
        if (raw instanceof ReasoningBankRetrievedMemory reasoningBankRetrievedMemory) {
            return reasoningBankRetrievedMemory;
        }
        if (raw instanceof ReasoningBankMemory reasoningBankMemory) {
            List<ReasoningBankRetrievedMemory> items = reasoningBankMemory.toRetrievedMemories();
            return items.isEmpty() ? null : items.get(0);
        }
        if (raw instanceof VectorNode node) {
            return ReasoningBankRetrievedMemory.fromVectorNode(node);
        }
        if (raw instanceof Map<?, ?>) {
            return ReasoningBankRetrievedMemory.fromMap(SchemaUtils.mapValue(raw));
        }
        return null;
    }

    private ReMeRetrievedMemory toReMeRetrievedMemory(Object raw) {
        if (raw instanceof ReMeRetrievedMemory reMeRetrievedMemory) {
            return reMeRetrievedMemory;
        }
        if (raw instanceof ReMeMemory reMeMemory) {
            return reMeMemory.toRetrievedMemory();
        }
        if (raw instanceof VectorNode node) {
            return ReMeRetrievedMemory.fromVectorNode(node);
        }
        if (raw instanceof Map<?, ?>) {
            return ReMeRetrievedMemory.fromMap(SchemaUtils.mapValue(raw));
        }
        return null;
    }

    private ACEMemory toAceMemory(Object raw, String userId) {
        ACEMemory memory;
        if (raw instanceof ACEMemory aceMemory) {
            memory = aceMemory;
        } else if (raw instanceof VectorNode node) {
            memory = ACEMemory.fromVectorNode(node);
        } else if (raw instanceof ACERetrievedMemory aceRetrievedMemory) {
            memory = ACEMemory.fromMap(aceRetrievedMemory.toMap());
        } else if (raw instanceof Map<?, ?>) {
            Map<String, Object> payload = SchemaUtils.mapValue(raw);
            if (!payload.containsKey("content")) {
                payload.put("content", SchemaUtils.stringValue(payload.get("reflection"), payload.toString()));
            }
            memory = ACEMemory.fromMap(payload);
        } else {
            return null;
        }

        if (Objects.equals(memory.getWorkspaceId(), "default") && userId != null) {
            memory.setWorkspaceId(userId);
        }
        return memory;
    }

    private ReasoningBankMemory toReasoningBankMemory(Object raw, String userId) {
        ReasoningBankMemory memory;
        if (raw instanceof ReasoningBankMemory reasoningBankMemory) {
            memory = reasoningBankMemory;
        } else if (raw instanceof VectorNode node) {
            memory = ReasoningBankMemory.fromVectorNode(node);
        } else if (raw instanceof Map<?, ?>) {
            memory = ReasoningBankMemory.fromMap(SchemaUtils.mapValue(raw));
        } else {
            return null;
        }

        if (Objects.equals(memory.getWorkspaceId(), "default") && userId != null) {
            memory.setWorkspaceId(userId);
        }
        return memory;
    }

    private ReMeMemory toReMeMemory(Object raw, String userId) {
        ReMeMemory memory;
        if (raw instanceof ReMeMemory reMeMemory) {
            memory = reMeMemory;
        } else if (raw instanceof VectorNode node) {
            memory = ReMeMemory.fromVectorNode(node);
        } else if (raw instanceof ReMeRetrievedMemory reMeRetrievedMemory) {
            memory = ReMeMemory.fromMap(reMeRetrievedMemory.toMap());
        } else if (raw instanceof Map<?, ?>) {
            memory = ReMeMemory.fromMap(SchemaUtils.mapValue(raw));
        } else {
            return null;
        }

        if (Objects.equals(memory.getWorkspaceId(), "default") && userId != null) {
            memory.setWorkspaceId(userId);
        }
        return memory;
    }

    private Object decodeStoredMemory(VectorNode node) {
        String type = SchemaUtils.stringValue(node.getMetadata().get("type"), "");
        return switch (type) {
            case "ace_memory" -> ACEMemory.fromVectorNode(node);
            case "reasoning_bank_memory" -> ReasoningBankMemory.fromVectorNode(node);
            case "reme_memory" -> ReMeMemory.fromVectorNode(node);
            case "task_memory" -> TaskMemory.fromVectorNode(node);
            case "personal_memory" -> PersonalMemory.fromVectorNode(node);
            default -> node.toDict();
        };
    }

    private String formatMemoryString(List<?> memories) {
        if (memories == null || memories.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Object item : memories) {
            String formatted = switch (retrievalAlgorithm) {
                case "ReasoningBank" -> formatReasoningBankMemory(item);
                case "ReMe", "RefCon", "DivCon", "Our" -> formatReMeMemory(item);
                default -> formatAceMemory(item);
            };
            if (formatted == null || formatted.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(formatted);
        }
        return builder.toString();
    }

    private String formatAceMemory(Object item) {
        ACERetrievedMemory memory = toAceRetrievedMemory(item);
        if (memory == null) {
            return null;
        }
        return "[" + memory.getId() + "] helpful=" + memory.getHelpful()
            + " harmful=" + memory.getHarmful()
            + " neutral=" + memory.getNeutral()
            + "\nSection: " + memory.getSection()
            + "\nContent: " + memory.getContent();
    }

    private String formatReasoningBankMemory(Object item) {
        ReasoningBankRetrievedMemory memory = toReasoningBankRetrievedMemory(item);
        if (memory == null) {
            return null;
        }
        return "Title: " + memory.getTitle()
            + "\nDescription: " + memory.getDescription()
            + "\nContent: " + memory.getContent();
    }

    private String formatReMeMemory(Object item) {
        ReMeRetrievedMemory memory = toReMeRetrievedMemory(item);
        if (memory == null) {
            return null;
        }
        return "When to use: " + memory.getWhenToUse()
            + "\nContent: " + memory.getContent();
    }

    private void persistNodes(String userId, List<VectorNode> nodes) {
        if (persistenceHelper == null || nodes == null || nodes.isEmpty()) {
            return;
        }
        Map<String, Object> nodesDict = new LinkedHashMap<>();
        for (VectorNode node : nodes) {
            if (node != null) {
                nodesDict.put(node.getId(), node.toDict());
            }
        }
        if (!nodesDict.isEmpty()) {
            persistenceHelper.save(userId, persistenceAlgoName(summaryAlgorithm), nodesDict);
        }
    }

    static String persistenceAlgoName(String algorithm) {
        return switch (algorithm) {
            case "ReasoningBank" -> "rb";
            case "ReMe", "RefCon", "DivCon", "Our" -> "reme";
            default -> "ace";
        };
    }

    static String configString(String key, String defaultValue) {
        Object value = Config.get(key, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }

    static int configInt(String key, int defaultValue) {
        Object value = Config.get(key, defaultValue);
        return SchemaUtils.intValue(value, defaultValue);
    }

    static boolean configBoolean(String key, boolean defaultValue) {
        Object value = Config.get(key, defaultValue);
        Boolean bool = SchemaUtils.booleanValue(value);
        return bool != null ? bool : defaultValue;
    }

    static List<Object> contextList(RuntimeContext context, String key) {
        Object value = context == null ? null : context.get(key, List.of());
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return new ArrayList<>();
    }

    static String contextString(RuntimeContext context, String key, String defaultValue) {
        Object value = context == null ? null : context.get(key, defaultValue);
        return value == null ? defaultValue : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> payloadMap(Object value) {
        Object payload = SchemaUtils.toPayload(value);
        if (payload instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("value", payload);
        return result;
    }

    static List<Double> defaultEmbeddingFor(String value) {
        int dimensions = 32;
        double[] dense = new double[dimensions];
        String normalized = value != null ? value.toLowerCase(Locale.ROOT) : "";
        String[] tokens = normalized.split("[^a-z0-9]+");
        int previousSlot = -1;

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int slot = Math.floorMod(token.hashCode(), dimensions);
            dense[slot] += 1.0d;
            if (previousSlot >= 0) {
                dense[(previousSlot + slot) % dimensions] += 0.25d;
            }
            previousSlot = slot;
        }

        if (Arrays.stream(dense).allMatch(component -> component == 0.0d)) {
            dense[0] = 1.0d;
        }

        List<Double> result = new ArrayList<>(dimensions);
        for (double component : dense) {
            result.add(component);
        }
        return result;
    }
}

/**
 * Mirrors Python's {@code UpdateVectorStoreOp} in
 * {@code openjiuwen/extensions/context_evolver/service/task_memory_service.py}.
 */
class UpdateVectorStoreOp extends BaseOp {

    private final MemoryVectorStore vectorStore;
    private final MemoryPersistenceHelper persistenceHelper;
    private final String summaryAlgorithm;

    UpdateVectorStoreOp(MemoryVectorStore vectorStore) {
        this(vectorStore, null, "ACE");
    }

    UpdateVectorStoreOp(
            MemoryVectorStore vectorStore,
            MemoryPersistenceHelper persistenceHelper,
            String summaryAlgorithm) {
        this.vectorStore = vectorStore;
        this.persistenceHelper = persistenceHelper;
        this.summaryAlgorithm = summaryAlgorithm;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<?> memories = TaskMemoryService.contextList(context, "memories");
        String userId = TaskMemoryService.contextString(context, "user_id", "default");
        if (memories == null || memories.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Object memory : memories) {
            VectorNode node = toVectorNode(memory, userId);
            if (node == null) {
                continue;
            }
            if (node.getEmbedding() == null) {
                node.setEmbedding(defaultEmbeddingFor(node.getContent()));
            }
            futures.add(vectorStore.asyncUpsert(node));
        }

        CompletableFuture<Void> upserts = futures.isEmpty()
            ? CompletableFuture.completedFuture(null)
            : CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return upserts.thenRun(() -> persistAll(userId));
    }

    private void persistAll(String userId) {
        if (persistenceHelper == null || userId == null || userId.isBlank()) {
            return;
        }
        Map<String, Object> nodesDict = new LinkedHashMap<>();
        for (VectorNode node : vectorStore.getAll()) {
            nodesDict.put(node.getId(), node.toDict());
        }
        persistenceHelper.save(userId, TaskMemoryService.persistenceAlgoName(summaryAlgorithm), nodesDict);
    }

    private VectorNode toVectorNode(Object memory, String userId) {
        if (memory instanceof VectorNode node) {
            return node;
        }
        if (memory instanceof ACEMemory aceMemory) {
            return aceMemory.toVectorNode();
        }
        if (memory instanceof ReasoningBankMemory reasoningBankMemory) {
            return reasoningBankMemory.toVectorNode();
        }
        if (memory instanceof ReMeMemory reMeMemory) {
            return reMeMemory.toVectorNode();
        }
        if (memory instanceof Map<?, ?>) {
            Map<String, Object> payload = SchemaUtils.mapValue(memory);
            payload.putIfAbsent("workspace_id", userId);
            if (payload.containsKey("memory")) {
                return ReasoningBankMemory.fromMap(payload).toVectorNode();
            }
            if (payload.containsKey("when_to_use")) {
                return ReMeMemory.fromMap(payload).toVectorNode();
            }
            if (!payload.containsKey("content")) {
                payload.put("content", SchemaUtils.stringValue(payload.get("reflection"), payload.toString()));
            }
            return ACEMemory.fromMap(payload).toVectorNode();
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("workspace_id", userId);
        metadata.put("content", String.valueOf(memory));
        return new VectorNode("memory-" + UUID.randomUUID(), String.valueOf(memory), null, metadata);
    }

    private static List<Double> defaultEmbeddingFor(String value) {
        return TaskMemoryService.defaultEmbeddingFor(value);
    }
}

/**
 * Mirrors Python's {@code ReMeRerankMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/service/task_memory_service.py}.
 */
class ReMeRerankMemoryOp extends BaseOp {

    private final boolean llmRerank;
    private final int topKRerank;

    ReMeRerankMemoryOp(boolean llmRerank, int topKRerank) {
        this.llmRerank = llmRerank;
        this.topKRerank = topKRerank;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        if (!llmRerank) {
            return CompletableFuture.completedFuture(null);
        }

        List<ReMeRetrievedMemory> retrievedMemories = new ArrayList<>();
        List<?> rawMemories = TaskMemoryService.contextList(context, "retrieved_memories");
        if (rawMemories == null || rawMemories.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        for (Object rawMemory : rawMemories) {
            if (rawMemory instanceof ReMeRetrievedMemory retrievedMemory) {
                retrievedMemories.add(retrievedMemory);
            }
        }

        String query = TaskMemoryService.contextString(context, "query", "");
        retrievedMemories.sort(Comparator.comparingDouble((ReMeRetrievedMemory memory) -> score(query, memory)).reversed());
        if (topKRerank > 0 && retrievedMemories.size() > topKRerank) {
            retrievedMemories = new ArrayList<>(retrievedMemories.subList(0, topKRerank));
        }

        context.set("retrieved_memories", retrievedMemories);
        return CompletableFuture.completedFuture(null);
    }

    private static double score(String query, ReMeRetrievedMemory memory) {
        Set<String> queryTokens = tokenize(query);
        Set<String> whenTokens = tokenize(memory.getWhenToUse());
        Set<String> contentTokens = tokenize(memory.getContent());

        double score = 0.0d;
        for (String token : queryTokens) {
            if (whenTokens.contains(token)) {
                score += 2.0d;
            }
            if (contentTokens.contains(token)) {
                score += 1.0d;
            }
        }

        if (!queryTokens.isEmpty() && !whenTokens.isEmpty()) {
            score += overlapRatio(queryTokens, whenTokens);
        }
        if (!queryTokens.isEmpty() && !contentTokens.isEmpty()) {
            score += overlapRatio(queryTokens, contentTokens) / 2.0d;
        }

        return score;
    }

    private static double overlapRatio(Set<String> first, Set<String> second) {
        int overlap = 0;
        for (String token : first) {
            if (second.contains(token)) {
                overlap++;
            }
        }
        return (double) overlap / Math.max(1, first.size());
    }

    private static Set<String> tokenize(String value) {
        Set<String> tokens = new HashSet<>();
        String normalized = value != null ? value.toLowerCase(Locale.ROOT) : "";
        for (String token : normalized.split("[^a-z0-9]+")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}

/**
 * Mirrors Python's {@code ReMeRewriteMemoryOp} in
 * {@code openjiuwen/extensions/context_evolver/service/task_memory_service.py}.
 */
class ReMeRewriteMemoryOp extends BaseOp {

    private final boolean llmRewrite;

    ReMeRewriteMemoryOp(boolean llmRewrite) {
        this.llmRewrite = llmRewrite;
    }

    @Override
    public CompletableFuture<Void> asyncExecute(RuntimeContext context) {
        List<?> rawMemories = TaskMemoryService.contextList(context, "retrieved_memories");
        if (rawMemories == null || rawMemories.isEmpty()) {
            context.set("memory_string", "");
            return CompletableFuture.completedFuture(null);
        }

        List<ReMeRetrievedMemory> retrievedMemories = new ArrayList<>();
        for (Object rawMemory : rawMemories) {
            if (rawMemory instanceof ReMeRetrievedMemory retrievedMemory) {
                retrievedMemories.add(retrievedMemory);
            }
        }

        String originalContext = formatOriginalMemories(retrievedMemories);
        if (!llmRewrite) {
            context.set("memory_string", originalContext);
            return CompletableFuture.completedFuture(null);
        }

        String query = TaskMemoryService.contextString(context, "query", "");
        StringBuilder builder = new StringBuilder("For the current query");
        if (query != null && !query.isBlank()) {
            builder.append(" (").append(query).append(")");
        }
        builder.append(", apply these relevant experiences:");
        for (ReMeRetrievedMemory memory : retrievedMemories) {
            builder.append("\n- When to use: ")
                .append(memory.getWhenToUse())
                .append(". Guidance: ")
                .append(memory.getContent());
        }
        context.set("memory_string", builder.toString());
        return CompletableFuture.completedFuture(null);
    }

    private static String formatOriginalMemories(List<ReMeRetrievedMemory> memories) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < memories.size(); index++) {
            ReMeRetrievedMemory memory = memories.get(index);
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("Memory ").append(index + 1).append(":\n")
                .append("  When to use: ").append(memory.getWhenToUse()).append("\n")
                .append("  Content: ").append(memory.getContent()).append("\n");
        }
        return builder.toString().trim();
    }
}
