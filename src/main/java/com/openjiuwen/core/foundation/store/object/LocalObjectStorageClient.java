/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import com.openjiuwen.spi.store.object.BaseObjectStorageClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Local-filesystem implementation of the object storage contract.
 */
public class LocalObjectStorageClient extends BaseObjectStorageClient {

    private final Path rootDirectory;

    public LocalObjectStorageClient(Path rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    @Override
    public boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception {
        Path target = resolveObjectPath(bucketName, objectName);
        Files.createDirectories(target.getParent());
        Files.copy(filePath, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    @Override
    public boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception {
        Path source = resolveObjectPath(bucketName, objectName);
        Files.createDirectories(filePath.getParent());
        Files.copy(source, filePath, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    @Override
    public boolean deleteObject(String bucketName, String objectName) throws Exception {
        return Files.deleteIfExists(resolveObjectPath(bucketName, objectName));
    }

    @Override
    public boolean createBucket(String bucketName, String location) throws Exception {
        Files.createDirectories(resolveBucketPath(bucketName));
        return true;
    }

    @Override
    public boolean deleteBucket(String bucketName) throws Exception {
        Path bucketPath = resolveBucketPath(bucketName);
        if (!Files.exists(bucketPath)) {
            return true;
        }
        try (Stream<Path> stream = Files.walk(bucketPath)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return true;
    }

    @Override
    public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) throws Exception {
        Path bucketPath = resolveBucketPath(bucketName);
        if (!Files.exists(bucketPath)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(bucketPath)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                if (result.size() >= maxObjects) {
                    return;
                }
                String objectName = bucketPath.relativize(path).toString().replace('\\', '/');
                if (objectPrefix == null || objectName.startsWith(objectPrefix)) {
                    try {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("bucket", bucketName);
                        item.put("object_name", objectName);
                        item.put("size", Files.size(path));
                        result.add(item);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        return result;
    }

    private Path resolveBucketPath(String bucketName) {
        return rootDirectory.resolve(bucketName);
    }

    private Path resolveObjectPath(String bucketName, String objectName) {
        return resolveBucketPath(bucketName).resolve(objectName);
    }
}
