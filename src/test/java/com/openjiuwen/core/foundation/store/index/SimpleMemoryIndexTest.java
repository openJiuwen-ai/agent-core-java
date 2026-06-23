/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.security.AesGcmCrypt;
import com.openjiuwen.core.common.security.CryptUtils;
import com.openjiuwen.core.common.utils.Singleton;
import com.openjiuwen.core.foundation.store.BaseKVStore;
import com.openjiuwen.core.foundation.store.BaseMemoryIndex;
import com.openjiuwen.core.foundation.store.BaseVectorStore;
import com.openjiuwen.core.foundation.store.BasedKVStorePipeline;
import com.openjiuwen.core.foundation.store.CollectionSchema;
import com.openjiuwen.core.foundation.store.Embedding;
import com.openjiuwen.core.foundation.store.FieldSchema;
import com.openjiuwen.core.foundation.store.MemoryDoc;
import com.openjiuwen.core.foundation.store.VectorDataType;
import com.openjiuwen.core.foundation.store.VectorSearchResult;
import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.memory.codec.AesStorageCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code SimpleMemoryIndex} test surface in
 * {@code openjiuwen/core/foundation/store/index/simple_memory_index.py}.
 *
 * <p>Also mirrors Python's {@code test_simple_memory_index} in
 * {@code tests/unit_tests/core/foundation/store/test_simple_memory_index.py}.</p>
 */
class SimpleMemoryIndexTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final String USER_ID = "test_user";

    private static final String SCOPE_ID = "test_scope";

    private static final String MEM_TYPE = "user_profile";

    private static final byte[] CRYPTO_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    private static final List<RecordSeed> OLD_RECORDS = List.of(
            new RecordSeed(makeId(1), "Alice likes Python"),
            new RecordSeed(makeId(2), "Bob prefers Go"),
            new RecordSeed(makeId(3), "Charlie works on AI")
    );

    @BeforeEach
    void resetCryptState() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
    }

    @AfterEach
    void clearCryptState() {
        Singleton.clearAll();
        CryptUtils.unregisterCrypt(CryptUtils.AES_GCM_CRYPT_NAME);
    }

    @Test
    void getByIdReadsOldFrameworkData() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        MemoryDoc doc = index.getById(USER_ID, SCOPE_ID, makeId(1)).join();

        assertThat(doc).isNotNull();
        assertThat(doc.getText()).isEqualTo("Alice likes Python");
        assertThat(doc.getType()).isEqualTo(MEM_TYPE);
        assertThat(doc.getTimestamp()).isNotNull();
    }

    @Test
    void searchWithoutMemTypeDiscoversCollections() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        List<BaseMemoryIndex.MemorySearchResult> results = index.search(USER_ID, SCOPE_ID, "AI", null, 5).join();

        assertThat(results).hasSize(3);
        assertThat(results)
                .extracting(result -> result.document().getText())
                .containsExactlyInAnyOrder("Alice likes Python", "Bob prefers Go", "Charlie works on AI");
    }

    @Test
    void listMemoriesSupportsPagination() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        List<MemoryDoc> pageOne = index.listMemories(USER_ID, SCOPE_ID, 0, 2, null).join();
        List<MemoryDoc> pageTwo = index.listMemories(USER_ID, SCOPE_ID, 2, 2, null).join();

        assertThat(pageOne).hasSize(2);
        assertThat(pageTwo).hasSize(1);
        assertThat(pageOne).extracting(MemoryDoc::getId).doesNotContain(pageTwo.getFirst().getId());
    }

    @Test
    void addUpdateAndDeletePreserveLegacyLayout() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        MemoryDoc newDoc = new MemoryDoc(
                makeId(4),
                "Diana studies Rust",
                MEM_TYPE,
                ZonedDateTime.now(ZoneOffset.UTC),
                Map.of("source_id", "msg_4")
        );
        index.addMemories(USER_ID, SCOPE_ID, List.of(newDoc)).join();

        MemoryDoc added = index.getById(USER_ID, SCOPE_ID, makeId(4)).join();
        assertThat(added.getText()).isEqualTo("Diana studies Rust");
        assertThat(added.getFields()).containsEntry("source_id", "msg_4");

        MemoryDoc updated = new MemoryDoc(
                makeId(1),
                "Alice now prefers Rust",
                MEM_TYPE,
                ZonedDateTime.now(ZoneOffset.UTC),
                Map.of()
        );
        index.updateMemories(USER_ID, SCOPE_ID, List.of(updated)).join();

        MemoryDoc refreshed = index.getById(USER_ID, SCOPE_ID, makeId(1)).join();
        assertThat(refreshed.getText()).isEqualTo("Alice now prefers Rust");

        index.deleteMemories(USER_ID, SCOPE_ID, List.of(makeId(2), makeId(4))).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, makeId(2)).join()).isNull();
        assertThat(index.getById(USER_ID, SCOPE_ID, makeId(4)).join()).isNull();
        assertThat(index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join()).hasSize(2);
    }

    @Test
    void deleteByUserAndScopeRemovesKvAndCollections() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.deleteByUserAndScope(USER_ID, SCOPE_ID).join();

        assertThat(index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join()).isEmpty();
        assertThat(vectorStore.listCollectionNames().join()).isEmpty();
    }

    @Test
    void listUserScopesReflectsTrackedKvKeys() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        List<BaseMemoryIndex.UserScopeKey> scopes = index.listUserScopes().join();

        assertThat(scopes).contains(new BaseMemoryIndex.UserScopeKey(USER_ID, SCOPE_ID));
    }

    @Test
    void getByIdReturnsNullWhenOldFrameworkDataIsMissing() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        MemoryDoc missing = index.getById(USER_ID, SCOPE_ID, makeId(99)).join();

        assertThat(missing).isNull();
    }

    @Test
    void searchFindsRelevantOldFrameworkMemory() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        List<BaseMemoryIndex.MemorySearchResult> results = index.search(
                USER_ID,
                SCOPE_ID,
                "Alice likes Python",
                null,
                3
        ).join();

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().document().getText()).contains("Alice");
        assertThat(results.getFirst().score()).isPositive();
    }

    @Test
    void searchWithMemTypeFilterReturnsOnlyThatType() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        List<BaseMemoryIndex.MemorySearchResult> results = index.search(
                USER_ID,
                SCOPE_ID,
                "programming",
                List.of(MEM_TYPE),
                5
        ).join();

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> assertThat(result.document().getType()).isEqualTo(MEM_TYPE));
    }

    @Test
    void listMemoriesReturnsEveryOldFrameworkRecord() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        List<MemoryDoc> docs = index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join();

        assertThat(docs).hasSize(3);
        assertThat(docs)
                .extracting(MemoryDoc::getText)
                .containsExactly("Alice likes Python", "Bob prefers Go", "Charlie works on AI");
    }

    @Test
    void timestampStringIsConvertedToTypedTimestamp() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        MemoryDoc doc = index.getById(USER_ID, SCOPE_ID, makeId(1)).join();

        assertThat(doc.getTimestamp()).isNotNull();
        assertThat(doc.getTimestamp().getYear()).isEqualTo(2026);
        assertThat(doc.getTimestamp().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void addNewMemoryCanBeReadBack() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        MemoryDoc newDoc = new MemoryDoc(
                makeId(4),
                "Diana studies Rust",
                MEM_TYPE,
                ZonedDateTime.now(ZoneOffset.UTC),
                Map.of("source_id", "msg_4")
        );

        index.addMemories(USER_ID, SCOPE_ID, List.of(newDoc)).join();
        MemoryDoc added = index.getById(USER_ID, SCOPE_ID, makeId(4)).join();

        assertThat(added).isNotNull();
        assertThat(added.getText()).isEqualTo("Diana studies Rust");
        assertThat(added.getFields()).containsEntry("source_id", "msg_4");
    }

    @Test
    void addMemoryDoesNotCorruptExistingOldFrameworkRecords() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc(makeId(4), "New", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        MemoryDoc old = index.getById(USER_ID, SCOPE_ID, makeId(1)).join();

        assertThat(old).isNotNull();
        assertThat(old.getText()).isEqualTo("Alice likes Python");
    }

    @Test
    void deleteSingleMemoryRemovesOnlyThatRecord() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.deleteMemories(USER_ID, SCOPE_ID, List.of(makeId(1))).join();
        List<MemoryDoc> remaining = index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, makeId(1)).join()).isNull();
        assertThat(remaining).hasSize(2);
        assertThat(remaining).extracting(MemoryDoc::getId).doesNotContain(makeId(1));
    }

    @Test
    void deleteMultipleMemoriesLeavesUntouchedRecords() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.deleteMemories(USER_ID, SCOPE_ID, List.of(makeId(1), makeId(2))).join();
        List<MemoryDoc> remaining = index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join();

        assertThat(remaining).hasSize(1);
        assertThat(remaining.getFirst().getText()).isEqualTo("Charlie works on AI");
    }

    @Test
    void deleteByUserRemovesKvRowsAndVectorCollections() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.deleteByUser(USER_ID).join();

        assertThat(index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join()).isEmpty();
        assertThat(vectorStore.listCollectionNames().join()).noneMatch(name -> name.contains(USER_ID));
    }

    @Test
    void deleteByScopeRemovesKvRowsAndVectorCollections() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.deleteByScope(SCOPE_ID).join();

        assertThat(index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join()).isEmpty();
        assertThat(vectorStore.listCollectionNames().join()).noneMatch(name -> name.contains(SCOPE_ID));
    }

    @Test
    void updateMemoryDeletesThenAddsWithoutChangingTotalCount() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        writeViaOldFramework(kvStore, vectorStore, embedding);
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        MemoryDoc updated = new MemoryDoc(
                makeId(1),
                "Alice now prefers Rust",
                MEM_TYPE,
                ZonedDateTime.now(ZoneOffset.UTC),
                Map.of()
        );

        index.deleteMemories(USER_ID, SCOPE_ID, List.of(makeId(1))).join();
        index.addMemories(USER_ID, SCOPE_ID, List.of(updated)).join();
        List<MemoryDoc> allDocs = index.listMemories(USER_ID, SCOPE_ID, 0, 100, null).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, makeId(1)).join().getText()).isEqualTo("Alice now prefers Rust");
        assertThat(allDocs).hasSize(3);
    }

    @Test
    void codecEncryptsStoredTextButReturnsPlaintextOnRead() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        MemoryDoc doc = new MemoryDoc("m1", "top secret", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of("tag", "keep"));
        index.addMemories(USER_ID, SCOPE_ID, List.of(doc)).join();

        MemoryDoc stored = index.getById(USER_ID, SCOPE_ID, "m1").join();
        Map<String, Object> rawEntry = firstStoredEntry(kvStore);

        assertThat(stored.getText()).isEqualTo("top secret");
        assertThat(rawEntry.get("mem")).isNotEqualTo("top secret");
        assertThat(rawEntry.get("mem_type")).isEqualTo(MEM_TYPE);
        assertThat(rawEntry.get("tag")).isEqualTo("keep");
    }

    @Test
    void addThenSearchWithCodecReturnsPlaintext() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "sensitive data", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        List<BaseMemoryIndex.MemorySearchResult> results = index.search(USER_ID, SCOPE_ID, "sensitive", null, 1).join();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().document().getText()).isEqualTo("sensitive data");
    }

    @Test
    void addThenGetByIdWithCodecReturnsPlaintext() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "confidential", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        MemoryDoc result = index.getById(USER_ID, SCOPE_ID, "m1").join();

        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("confidential");
    }

    @Test
    void addThenListMemoriesWithCodecReturnsPlaintext() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));
        List<MemoryDoc> docs = List.of(
                new MemoryDoc("test0000000000000000000000", "data_0", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()),
                new MemoryDoc("test0000000000000000000001", "data_1", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()),
                new MemoryDoc("test0000000000000000000002", "data_2", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of())
        );

        index.addMemories(USER_ID, SCOPE_ID, docs).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, "test0000000000000000000000").join().getText()).isEqualTo("data_0");
        assertThat(index.getById(USER_ID, SCOPE_ID, "test0000000000000000000001").join().getText()).isEqualTo("data_1");
        assertThat(index.getById(USER_ID, SCOPE_ID, "test0000000000000000000002").join().getText()).isEqualTo("data_2");
    }

    @Test
    void codecLeavesNonMemoryFieldsPlaintext() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "secret", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of("source_id", "src_1")))
        ).join();
        Map<String, Object> rawEntry = firstStoredEntry(kvStore);

        assertThat(rawEntry).containsEntry("id", "m1").containsEntry("mem_type", MEM_TYPE).containsEntry("source_id", "src_1");
        assertThat(rawEntry.get("mem")).isNotEqualTo("secret");
    }

    @Test
    void codecKeepsIdTrackingPlaintext() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "data", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        Object idsRaw = kvStore.get("UMD/" + USER_ID + "/" + SCOPE_ID + "/ids").join();

        assertThat(idsRaw).isNotNull();
        assertThat(new String((byte[]) idsRaw, StandardCharsets.UTF_8)).isEqualTo("m1");
    }

    @Test
    void withoutCodecStoresPlaintextMemory() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "plain data", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        Map<String, Object> rawEntry = firstStoredEntry(kvStore);

        assertThat(rawEntry.get("mem")).isEqualTo("plain data");
    }

    @Test
    void searchWithoutCodecStillWorks() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "open data", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        List<BaseMemoryIndex.MemorySearchResult> results = index.search(USER_ID, SCOPE_ID, "open", null, 1).join();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().document().getText()).isEqualTo("open data");
    }

    @Test
    void updateMemoriesWithCodecKeepsReadPathPlaintext() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "old text", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        index.updateMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "new text", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, "m1").join().getText()).isEqualTo("new text");
    }

    @Test
    void deleteMemoriesWithCodecRemovesEncryptedRecord() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "to delete", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        index.deleteMemories(USER_ID, SCOPE_ID, List.of("m1")).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, "m1").join()).isNull();
    }

    @Test
    void deleteMemoriesWithCodecUsesPlaintextMemTypeForTracking() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        registerAesGcm();
        SimpleMemoryIndex index = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        index.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        index.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "delete me", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        index.deleteMemories(USER_ID, SCOPE_ID, List.of("m1")).join();

        assertThat(index.getById(USER_ID, SCOPE_ID, "m1").join()).isNull();
    }

    @Test
    void codecDecodeFailureFallsBackToStoredText() {
        InMemoryKVStore kvStore = new InMemoryKVStore();
        TestVectorStore vectorStore = new TestVectorStore();
        TestEmbedding embedding = new TestEmbedding();
        SimpleMemoryIndex writer = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        writer.addMemories(
                USER_ID,
                SCOPE_ID,
                List.of(new MemoryDoc("m1", "legacy plain", MEM_TYPE, ZonedDateTime.now(ZoneOffset.UTC), Map.of()))
        ).join();
        registerAesGcm();
        SimpleMemoryIndex reader = new SimpleMemoryIndex(kvStore, vectorStore, embedding);
        reader.setStorageCodec(new AesStorageCodec(CRYPTO_KEY));

        MemoryDoc result = reader.getById(USER_ID, SCOPE_ID, "m1").join();

        assertThat(result).isNotNull();
        assertThat(result.getText()).isEqualTo("legacy plain");
    }

    @Test
    void createAndRestoreBackupTracksSchemaVersion() {
        SimpleMemoryIndex index = new SimpleMemoryIndex(new InMemoryKVStore(), new TestVectorStore(), new TestEmbedding());
        index.updateSchemaVersion(7);

        String backupId = index.createBackup().join();
        index.updateSchemaVersion(9);
        index.restoreBackup(backupId).join();

        assertThat(index.getSchemaVersion()).isEqualTo(7);
    }

    private static void writeViaOldFramework(InMemoryKVStore kvStore, TestVectorStore vectorStore, TestEmbedding embedding) {
        String timestamp = "2026-06-10 08:00:00";
        String collectionName = "uid_" + USER_ID + "_gid_" + SCOPE_ID + "_mtype_" + MEM_TYPE;
        vectorStore.createCollection(
                collectionName,
                new CollectionSchema(
                        List.of(
                                new FieldSchema("id", VectorDataType.VARCHAR, true, false, 256, null, null, null, null, null),
                                new FieldSchema("embedding", VectorDataType.FLOAT_VECTOR, false, false, null, embedding.getDimension(), null, null, null, null)
                        ),
                        "legacy",
                        false
                ),
                Map.of()
        ).join();

        StringBuilder idsBuilder = new StringBuilder();
        for (RecordSeed record : OLD_RECORDS) {
            idsBuilder.append(record.id());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", record.id());
            payload.put("user_id", USER_ID);
            payload.put("scope_id", SCOPE_ID);
            payload.put("mem", record.text());
            payload.put("source_id", "");
            payload.put("mem_type", MEM_TYPE);
            payload.put("timestamp", timestamp);
            kvStore.set(
                    "UMD/" + USER_ID + "/" + SCOPE_ID + "/" + record.id(),
                    writeJson(payload).getBytes(StandardCharsets.UTF_8)
            ).join();
            vectorStore.addDocs(
                    collectionName,
                    List.of(Map.of("id", record.id(), "embedding", embedding.embedForTest(record.text()))),
                    Map.of()
            ).join();
        }
        kvStore.set("UMD/" + USER_ID + "/" + SCOPE_ID + "/ids", idsBuilder.toString().getBytes(StandardCharsets.UTF_8)).join();
        kvStore.set("UMD/" + USER_ID + "/" + SCOPE_ID + "/" + MEM_TYPE + "/ids", idsBuilder.toString().getBytes(StandardCharsets.UTF_8)).join();
    }

    private static Map<String, Object> firstStoredEntry(InMemoryKVStore kvStore) {
        for (Map.Entry<String, Object> entry : kvStore.getByPrefix("UMD/").join().entrySet()) {
            if (entry.getKey().endsWith("/ids")) {
                continue;
            }
            Object raw = entry.getValue();
            String text = raw instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : String.valueOf(raw);
            return readJson(text);
        }
        throw new AssertionError("Expected at least one stored KV entry");
    }

    private static AesGcmCrypt registerAesGcm() {
        AesGcmCrypt crypt = AesGcmCrypt.getInstance();
        CryptUtils.registerCrypt(CryptUtils.AES_GCM_CRYPT_NAME, crypt);
        return crypt;
    }

    private static String writeJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, Object> readJson(String payload) {
        try {
            return OBJECT_MAPPER.readValue(payload, MAP_TYPE);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String makeId(int value) {
        return "%024d".formatted(value);
    }

    private record RecordSeed(String id, String text) {
    }

    /**
     * Mirrors Python's test-only fake embedding in
     * {@code openjiuwen/core/foundation/store/index/simple_memory_index.py}.
     */
    private static final class TestEmbedding extends Embedding {

        private static final int DIM = 8;

        @Override
        public CompletableFuture<List<Double>> embedQuery(String text, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(embedForTest(text));
        }

        @Override
        public CompletableFuture<List<List<Double>>> embedDocuments(
                List<String> texts,
                Integer batchSize,
                Map<String, Object> kwargs
        ) {
            List<List<Double>> embeddings = new ArrayList<>();
            for (String text : texts) {
                embeddings.add(embedForTest(text));
            }
            return CompletableFuture.completedFuture(embeddings);
        }

        @Override
        public int getDimension() {
            return DIM;
        }

        private List<Double> embedForTest(String text) {
            double[] values = new double[DIM];
            String safeText = text == null ? "" : text;
            for (int index = 0; index < safeText.length(); index++) {
                values[index % DIM] += safeText.charAt(index) * 0.01d;
            }
            double norm = 0.0d;
            for (double value : values) {
                norm += value * value;
            }
            norm = Math.sqrt(norm);
            List<Double> embedding = new ArrayList<>(DIM);
            for (double value : values) {
                embedding.add(norm == 0.0d ? value : value / norm);
            }
            return embedding;
        }
    }

    /**
     * Mirrors Python's test-only in-memory vector store semantics around
     * {@code SimpleMemoryIndex} in
     * {@code openjiuwen/core/foundation/store/index/simple_memory_index.py}.
     */
    private static final class TestVectorStore extends BaseVectorStore {

        private final Map<String, Map<String, Object>> collections = new LinkedHashMap<>();

        private final Map<String, List<Map<String, Object>>> docs = new LinkedHashMap<>();

        @Override
        public CompletableFuture<Void> createCollection(String collectionName, Object schema, Map<String, Object> kwargs) {
            collections.put(collectionName, new LinkedHashMap<>());
            docs.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteCollection(String collectionName, Map<String, Object> kwargs) {
            collections.remove(collectionName);
            docs.remove(collectionName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> collectionExists(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(collections.containsKey(collectionName));
        }

        @Override
        public CompletableFuture<CollectionSchema> getSchema(String collectionName, Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> addDocs(String collectionName, List<Map<String, Object>> docsToAdd, Map<String, Object> kwargs) {
            List<Map<String, Object>> bucket = docs.computeIfAbsent(collectionName, ignored -> new ArrayList<>());
            for (Map<String, Object> doc : docsToAdd) {
                String id = String.valueOf(doc.get("id"));
                int existingIndex = -1;
                for (int index = 0; index < bucket.size(); index++) {
                    if (Objects.equals(bucket.get(index).get("id"), id)) {
                        existingIndex = index;
                        break;
                    }
                }
                if (existingIndex >= 0) {
                    bucket.set(existingIndex, new LinkedHashMap<>(doc));
                } else {
                    bucket.add(new LinkedHashMap<>(doc));
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<VectorSearchResult>> search(
                String collectionName,
                List<Double> queryVector,
                String vectorField,
                int topK,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            List<VectorSearchResult> results = new ArrayList<>();
            for (Map<String, Object> doc : docs.getOrDefault(collectionName, List.of())) {
                if (filters != null && !filters.isEmpty()) {
                    boolean match = true;
                    for (Map.Entry<String, Object> filter : filters.entrySet()) {
                        if (!Objects.equals(doc.get(filter.getKey()), filter.getValue())) {
                            match = false;
                            break;
                        }
                    }
                    if (!match) {
                        continue;
                    }
                }
                @SuppressWarnings("unchecked")
                List<Double> embedding = (List<Double>) doc.getOrDefault(vectorField, List.of());
                double score = cosine(queryVector, embedding);
                Map<String, Object> fields = new LinkedHashMap<>(doc);
                fields.remove(vectorField);
                results.add(new VectorSearchResult(score, fields));
            }
            results.sort(Comparator.comparingDouble(VectorSearchResult::getScore).reversed());
            if (results.size() > topK) {
                results = new ArrayList<>(results.subList(0, topK));
            }
            return CompletableFuture.completedFuture(results);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByIds(String collectionName, List<String> ids, Map<String, Object> kwargs) {
            List<Map<String, Object>> bucket = docs.get(collectionName);
            if (bucket != null) {
                bucket.removeIf(doc -> ids.contains(String.valueOf(doc.get("id"))));
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> deleteDocsByFilters(
                String collectionName,
                Map<String, Object> filters,
                Map<String, Object> kwargs
        ) {
            List<Map<String, Object>> bucket = docs.get(collectionName);
            if (bucket != null) {
                bucket.removeIf(doc -> {
                    for (Map.Entry<String, Object> filter : filters.entrySet()) {
                        if (!Objects.equals(doc.get(filter.getKey()), filter.getValue())) {
                            return false;
                        }
                    }
                    return true;
                });
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<List<String>> listCollectionNames() {
            return CompletableFuture.completedFuture(new ArrayList<>(collections.keySet()));
        }

        @Override
        public CompletableFuture<Void> updateSchema(String collectionName, List<com.openjiuwen.core.memory.migration.operation.BaseOperation> operations) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> updateCollectionMetadata(String collectionName, Map<String, Object> metadata) {
            collections.computeIfAbsent(collectionName, ignored -> new LinkedHashMap<>()).putAll(metadata);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Map<String, Object>> getCollectionMetadata(String collectionName) {
            return CompletableFuture.completedFuture(collections.getOrDefault(collectionName, Map.of()));
        }

        private double cosine(List<Double> left, List<Double> right) {
            double dot = 0.0d;
            double leftNorm = 0.0d;
            double rightNorm = 0.0d;
            for (int index = 0; index < Math.min(left.size(), right.size()); index++) {
                dot += left.get(index) * right.get(index);
            }
            for (double value : left) {
                leftNorm += value * value;
            }
            for (double value : right) {
                rightNorm += value * value;
            }
            if (leftNorm == 0.0d || rightNorm == 0.0d) {
                return 0.0d;
            }
            return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
        }
    }
}
