/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.retrieval.vector_store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.retrieval.vector_store.ChromaVectorStore.ChromaClientAdapter;
import com.openjiuwen.retrieval.vector_store.ChromaVectorStore.ChromaCollectionAdapter;
import com.openjiuwen.retrieval.vector_store.ChromaVectorStore.InMemoryChromaCollectionAdapter;
import com.openjiuwen.retrieval.vector_store.ChromaVectorStore.InMemoryChromaCollectionAdapter.StoredDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Stores the Java Chroma adapter's collections in a versioned JSON file.
 * This format is independent of Python Chroma's SQLite files.
 */
final class PersistentChromaClientAdapter implements ChromaClientAdapter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Object[] FILE_LOCKS = createLocks();
    private static final int FORMAT_VERSION = 1;

    private final Path dataFile;
    private final Path lockFile;
    private final Object processLock;

    PersistentChromaClientAdapter(String databaseName, String directory) {
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("A Chroma persistence directory is required");
        }
        try {
            Path root = Files.createDirectories(Path.of(directory).toAbsolutePath().normalize()).toRealPath();
            dataFile = root.resolve("jiuwen-chroma-" + databaseId(databaseName) + ".json");
            lockFile = root.resolve(dataFile.getFileName() + ".lock");
            processLock = FILE_LOCKS[Math.floorMod(dataFile.hashCode(), FILE_LOCKS.length)];
        } catch (IOException error) {
            throw new UncheckedIOException("Cannot open Chroma persistence directory", error);
        }
    }

    @Override
    public ChromaCollectionAdapter getCollection(String name) {
        transaction(false, state -> requiredCollection(state, name));
        return new PersistentCollection(name);
    }

    @Override
    public ChromaCollectionAdapter getOrCreateCollection(String name, Map<String, Object> configuration) {
        Objects.requireNonNull(name, "name");
        transaction(true, state -> state.collections().computeIfAbsent(name,
                ignored -> new CollectionState(new LinkedHashMap<>(configuration), new ArrayList<>())));
        return new PersistentCollection(name);
    }

    @Override
    public void deleteCollection(String name) {
        transaction(true, state -> state.collections().remove(name));
    }

    @Override
    public List<ChromaCollectionAdapter> listCollections() {
        return transaction(false, state -> state.collections().keySet().stream()
                .map(name -> (ChromaCollectionAdapter) new PersistentCollection(name)).toList());
    }

    private <T> T transaction(boolean write, Function<DatabaseState, T> operation) {
        synchronized (processLock) {
            try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                 FileLock fileLock = channel.lock()) {
                if (!fileLock.isValid() || Files.isSymbolicLink(dataFile)) {
                    throw new IOException("Invalid Chroma persistence file or lock");
                }
                DatabaseState state = load();
                if (state == null || state.version() != FORMAT_VERSION || state.collections() == null) {
                    throw new IOException("Unsupported Chroma persistence format");
                }
                T result = operation.apply(state);
                if (write) {
                    save(state);
                }
                return result;
            } catch (IOException error) {
                throw new UncheckedIOException("Chroma persistence operation failed", error);
            }
        }
    }

    private DatabaseState load() throws IOException {
        if (!Files.exists(dataFile, LinkOption.NOFOLLOW_LINKS)) {
            return new DatabaseState(FORMAT_VERSION, new LinkedHashMap<>());
        }
        try (InputStream input = Files.newInputStream(dataFile, LinkOption.NOFOLLOW_LINKS)) {
            return OBJECT_MAPPER.readValue(input, DatabaseState.class);
        }
    }

    private void save(DatabaseState state) throws IOException {
        Path temporary = Files.createTempFile(dataFile.getParent(), ".jiuwen-chroma-", ".tmp");
        try {
            try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = ByteBuffer.wrap(OBJECT_MAPPER.writeValueAsBytes(state));
                while (bytes.hasRemaining()) {
                    output.write(bytes);
                }
                output.force(true);
            }
            try {
                Files.move(temporary, dataFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                // Readers also hold the sidecar lock on filesystems without atomic replacement.
                Files.move(temporary, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static CollectionState requiredCollection(DatabaseState state, String name) {
        CollectionState collection = state.collections().get(name);
        if (collection == null) {
            throw new IllegalArgumentException("Collection does not exist: " + name);
        }
        return collection;
    }

    private static String databaseId(String name) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static Object[] createLocks() {
        Object[] locks = new Object[64];
        for (int index = 0; index < locks.length; index++) {
            locks[index] = new Object();
        }
        return locks;
    }

    private record DatabaseState(int version, Map<String, CollectionState> collections) {
    }

    private record CollectionState(Map<String, Object> configuration, List<StoredDocument> rows) {
    }

    private final class PersistentCollection implements ChromaCollectionAdapter {
        private final String name;

        private PersistentCollection(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Map<String, Object> configuration() {
            return access(false, InMemoryChromaCollectionAdapter::configuration);
        }

        @Override
        public void add(List<String> ids, List<List<Double>> embeddings, List<String> documents,
                List<Map<String, Object>> metadatas) {
            access(true, collection -> {
                collection.add(ids, embeddings, documents, metadatas);
                return null;
            });
        }

        @Override
        public Map<String, Object> query(List<Double> embedding, String text, int count, Map<String, Object> arguments) {
            return access(false, collection -> collection.query(embedding, text, count, arguments));
        }

        @Override
        public void delete(List<String> ids, Map<String, Object> arguments) {
            access(true, collection -> {
                collection.delete(ids, arguments);
                return null;
            });
        }

        private <T> T access(boolean write, Function<InMemoryChromaCollectionAdapter, T> operation) {
            return transaction(write, state -> {
                CollectionState saved = requiredCollection(state, name);
                InMemoryChromaCollectionAdapter collection = new InMemoryChromaCollectionAdapter(
                        name, saved.configuration());
                collection.restoreRows(saved.rows());
                T result = operation.apply(collection);
                if (write) {
                    state.collections().put(name,
                            new CollectionState(collection.configuration(), collection.snapshotRows()));
                }
                return result;
            });
        }
    }
}
