/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.designer.DesignerJson;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerGraphNormalizer;
import com.openjiuwen.designer.schema.DesignerRunNormalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * File-backed designer graph and run storage.
 *
 * <p>Documents are normalized before writing and written to a temporary file,
 * flushed, and atomically moved into place. Access to one graph id is serialized
 * inside this process; cross-process locking is added by the host adapter.</p>
 *
 * @since 2026-11-06
 */
public final class DesignerGraphStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(DesignerGraphStore.class);
    private static final ObjectMapper MAPPER = DesignerJson.mapper();

    private final Path root;
    private final DesignerGraphNormalizer graphNormalizer;
    private final DesignerRunNormalizer runNormalizer;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Create a store rooted at the supplied designer directory.
     *
     * @param root designer data root, normally {@code <data>/agent/designer}
     */
    public DesignerGraphStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        this.graphNormalizer = new DesignerGraphNormalizer();
        this.runNormalizer = new DesignerRunNormalizer();
    }

    /**
     * Save a graph document.
     *
     * @param graph graph to save
     * @return normalized graph that was written
     */
    public DesignerGraph saveGraph(DesignerGraph graph) {
        DesignerGraph normalized = graphNormalizer.normalize(graph);
        Path file = graphPath(normalized.graphId());
        ReentrantLock lock = lock(normalized.graphId());
        lock.lock();
        try {
            writeAtomic(file, normalized);
        } finally {
            lock.unlock();
        }
        return normalized;
    }

    /**
     * Load a graph document.
     *
     * @param graphId graph id
     * @return graph when present
     */
    public Optional<DesignerGraph> loadGraph(String graphId) {
        Path file = graphPath(graphId);
        ReentrantLock lock = lock(graphId);
        lock.lock();
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of(graphNormalizer.normalize(MAPPER.readValue(file.toFile(), DesignerGraph.class)));
        } catch (IOException ex) {
            throw new DesignerStoreException("failed to read graph " + graphId, ex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Save a run document.
     *
     * @param run run to save
     * @return normalized run that was written
     */
    public DesignerExecutionRun saveRun(DesignerExecutionRun run) {
        DesignerExecutionRun normalized = runNormalizer.normalize(run);
        Path file = runPath(normalized.runId());
        ReentrantLock lock = lock(normalized.runId());
        lock.lock();
        try {
            writeAtomic(file, normalized);
        } finally {
            lock.unlock();
        }
        return normalized;
    }

    /**
     * Load a run document.
     *
     * @param runId run id
     * @return run when present
     */
    public Optional<DesignerExecutionRun> loadRun(String runId) {
        Path file = runPath(runId);
        ReentrantLock lock = lock(runId);
        lock.lock();
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return Optional.of(runNormalizer.normalize(
                    MAPPER.readValue(file.toFile(), DesignerExecutionRun.class)));
        } catch (IOException ex) {
            throw new DesignerStoreException("failed to read run " + runId, ex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Load every graph document.
     *
     * @return graphs ordered by file name, never null
     */
    public List<DesignerGraph> listGraphs() {
        List<DesignerGraph> graphs = new ArrayList<>();
        Path directory = root.resolve("graphs");
        if (!Files.isDirectory(directory)) {
            return graphs;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        loadGraph(name.substring(0, name.length() - ".json".length()))
                                .ifPresent(graphs::add);
                    });
        } catch (IOException ex) {
            throw new DesignerStoreException("failed to list designer graphs", ex);
        }
        return graphs;
    }

    /**
     * Resolve the graph document path.
     *
     * @param graphId graph id
     * @return graph file path
     */
    public Path graphPath(String graphId) {
        String safeId = DesignerGraphNormalizer.storageComponent(graphId, "graph_id");
        return root.resolve("graphs").resolve(safeId + ".json");
    }

    /**
     * Resolve the run document path.
     *
     * @param runId run id
     * @return run file path
     */
    public Path runPath(String runId) {
        String safeId = DesignerGraphNormalizer.storageComponent(runId, "run_id");
        return root.resolve("runs").resolve(safeId + ".json");
    }

    private void writeAtomic(Path file, Object value) {
        Path directory = file.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".designer-", ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            force(temporary);
            moveIntoPlace(temporary, file);
        } catch (IOException ex) {
            LOGGER.error("Failed to write designer document: {}", file.getFileName());
            throw new DesignerStoreException("failed to write designer document " + file.getFileName(), ex);
        } finally {
            deleteTemporary(temporary);
        }
    }

    private void moveIntoPlace(Path temporary, Path file) throws IOException {
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void force(Path file) throws IOException {
        OpenOption[] options = {StandardOpenOption.WRITE};
        try (FileChannel channel = FileChannel.open(file, options)) {
            channel.force(true);
        }
    }

    private static void deleteTemporary(Path temporary) {
        if (temporary == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ex) {
            LOGGER.warn("Failed to delete temporary designer file: {}", temporary.getFileName());
        }
    }

    private ReentrantLock lock(String id) {
        return locks.computeIfAbsent(id, ignored -> new ReentrantLock());
    }
}
