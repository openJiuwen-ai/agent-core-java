/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import com.openjiuwen.spi.store.object.BaseObjectStorageClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Local-filesystem implementation of the object storage contract.
 * 
 * @since 0.1.7
 */
public class LocalObjectStorageClient extends BaseObjectStorageClient {
    private final Path rootDirectory;

    /**
     * LocalObjectStorageClient.
     * 
     * @param rootDirectory rootDirectory
     * @throws IllegalArgumentException if the storage root cannot be created or resolved
     * @since 0.1.7
     */
    public LocalObjectStorageClient(Path rootDirectory) {
        if (rootDirectory == null) {
            throw new IllegalArgumentException("Root directory must not be null.");
        }
        try {
            Files.createDirectories(rootDirectory);
            this.rootDirectory = rootDirectory.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to resolve object storage root.", e);
        }
    }

    /**
     * uploadFile.
     * 
     * @param bucketName bucketName
     * @param objectName objectName
     * @param filePath filePath
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception {
        Path target = resolveObjectPath(bucketName, objectName);
        Files.createDirectories(target.getParent());
        Files.copy(filePath, target, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /**
     * downloadFile.
     * 
     * @param bucketName bucketName
     * @param objectName objectName
     * @param filePath filePath
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception {
        Path source = resolveObjectPath(bucketName, objectName);
        Files.createDirectories(filePath.getParent());
        Files.copy(source, filePath, StandardCopyOption.REPLACE_EXISTING);
        return true;
    }

    /**
     * deleteObject.
     * 
     * @param bucketName bucketName
     * @param objectName objectName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean deleteObject(String bucketName, String objectName) throws Exception {
        return Files.deleteIfExists(resolveObjectPath(bucketName, objectName));
    }

    /**
     * createBucket.
     * 
     * @param bucketName bucketName
     * @param location location
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean createBucket(String bucketName, String location) throws Exception {
        Files.createDirectories(resolveBucketPath(bucketName));
        return true;
    }

    /**
     * deleteBucket.
     * 
     * @param bucketName bucketName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public boolean deleteBucket(String bucketName) throws Exception {
        Path bucketPath = resolveBucketPath(bucketName);
        if (!Files.exists(bucketPath)) {
            return true;
        }
        try (Stream<Path> stream = Files.walk(bucketPath)) {
            stream.sorted((left, right) -> right.getNameCount() - left.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return true;
    }

    /**
     * listObjects.
     * 
     * @param bucketName bucketName
     * @param objectPrefix objectPrefix
     * @param maxObjects maxObjects
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    @Override
    public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects)
            throws Exception {
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

    /**
     * resolveBucketPath.
     * 
     * @param bucketName bucketName
     * @return the result
     * @throws IOException if the bucket path cannot be resolved
     * @since 0.1.7
     */
    private Path resolveBucketPath(String bucketName) throws IOException {
        validateStorageName(bucketName, "Bucket name");
        return ensureWithinRoot(rootDirectory.resolve(bucketName).normalize(), bucketName);
    }

    /**
     * resolveObjectPath.
     * 
     * @param bucketName bucketName
     * @param objectName objectName
     * @return the result
     * @throws IOException if the object path cannot be resolved
     * @since 0.1.7
     */
    private Path resolveObjectPath(String bucketName, String objectName) throws IOException {
        validateStorageName(objectName, "Object name");
        Path objectPath = resolveBucketPath(bucketName).resolve(objectName).normalize();
        return ensureWithinRoot(objectPath, objectName);
    }

    private void validateStorageName(String name, String label) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        if (".".equals(name) || name.contains("..") || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException(label + " contains an invalid path sequence.");
        }
        Path namePath = Path.of(name);
        if (namePath.isAbsolute() || namePath.getNameCount() != 1) {
            throw new IllegalArgumentException(label + " must be a single relative name.");
        }
    }

    private Path ensureWithinRoot(Path candidate, String originalName) throws IOException {
        if (!candidate.startsWith(rootDirectory)) {
            throw new SecurityException("Storage path is outside the configured root: " + originalName);
        }

        Path existingAncestor = candidate;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null || !existingAncestor.toRealPath().startsWith(rootDirectory)) {
            throw new SecurityException("Storage path is outside the configured root: " + originalName);
        }

        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            Path realCandidate = candidate.toRealPath();
            if (!realCandidate.startsWith(rootDirectory)) {
                throw new SecurityException("Storage path is outside the configured root: " + originalName);
            }
            return realCandidate;
        }
        return candidate;
    }
}
