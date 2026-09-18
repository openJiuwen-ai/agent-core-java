/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class LocalObjectStorageClientTest {
    @TempDir
    Path tempDir;

    @Test
    void storesSingleLevelNamesWithinConfiguredRoot() throws Exception {
        Path root = tempDir.resolve("storage");
        Path source = tempDir.resolve("source.txt");
        Path download = tempDir.resolve("download").resolve("copy.txt");
        byte[] content = "safe object".getBytes(StandardCharsets.UTF_8);
        Files.write(source, content);
        LocalObjectStorageClient client = new LocalObjectStorageClient(root);

        assertTrue(client.createBucket("safe-bucket", null));
        assertTrue(client.uploadFile("safe-bucket", "object.txt", source));
        assertTrue(client.downloadFile("safe-bucket", "object.txt", download));

        assertArrayEquals(content, Files.readAllBytes(download));
        assertTrue(client.deleteObject("safe-bucket", "object.txt"));
        assertTrue(client.deleteBucket("safe-bucket"));
    }

    @Test
    void rejectsBucketAndObjectPathTraversal() throws Exception {
        Path root = tempDir.resolve("storage");
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "safe");
        LocalObjectStorageClient client = new LocalObjectStorageClient(root);

        assertThrows(IllegalArgumentException.class, () -> client.createBucket("../outside", null));
        assertThrows(IllegalArgumentException.class, () -> client.createBucket("nested/bucket", null));
        assertThrows(IllegalArgumentException.class,
                () -> client.uploadFile("safe-bucket", "../outside.txt", source));
        assertThrows(IllegalArgumentException.class,
                () -> client.uploadFile("safe-bucket", "nested/object.txt", source));

        assertFalse(Files.exists(tempDir.resolve("outside")));
        assertFalse(Files.exists(tempDir.resolve("outside.txt")));
    }

    @Test
    void rejectsSymbolicLinksOutsideStorageRoot() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("storage"));
        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "safe");
        Files.createSymbolicLink(root.resolve("linked-bucket"), outside);
        LocalObjectStorageClient client = new LocalObjectStorageClient(root);

        assertThrows(SecurityException.class, () -> client.createBucket("linked-bucket", null));

        Path safeBucket = Files.createDirectories(root.resolve("safe-bucket"));
        Path outsideFile = outside.resolve("escaped.txt");
        Files.writeString(outsideFile, "outside");
        Files.createSymbolicLink(safeBucket.resolve("linked-object"), outsideFile);
        assertThrows(SecurityException.class,
                () -> client.uploadFile("safe-bucket", "linked-object", source));
        assertTrue(Files.exists(outsideFile));
    }
}
