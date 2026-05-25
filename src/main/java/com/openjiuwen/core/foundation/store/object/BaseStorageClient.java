/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for Object Storage client.
 * <p>
 * Mirrors Python's {@code BaseObjectStorageClient} class from
 * <code>foundation/store/object/base_storage_client.py</code>.
 *
 * <p>This class provides the interface for basic bucket and object operations
 * such as creating buckets, uploading/downloading files, listing objects,
 * and deleting objects.
 */
public abstract class BaseStorageClient {

    /**
     * Upload a local file to an object storage bucket.
     *
     * @param bucketName Name of the target bucket
     * @param objectName Object key (path/name)
     * @param filePath Local file path to upload
     * @return CompletableFuture containing true if upload succeeded
     */
    public abstract CompletableFuture<Boolean> uploadFile(String bucketName, 
        String objectName, Path filePath);

    /**
     * Download an object from Object Storage server.
     *
     * @param bucketName Name of the bucket
     * @param objectName Object key to download
     * @param filePath Local file path where the object will be saved
     * @return CompletableFuture containing true if download succeeded
     */
    public abstract CompletableFuture<Boolean> downloadFile(String bucketName, 
        String objectName, Path filePath);

    /**
     * Delete an object from an object storage bucket.
     *
     * @param bucketName Name of the bucket
     * @param objectName Object key to delete
     * @return CompletableFuture containing true if deletion succeeded
     */
    public abstract CompletableFuture<Boolean> deleteObject(String bucketName, 
        String objectName);

    /**
     * Create a new object storage bucket.
     *
     * @param bucketName Name of the bucket to be created
     * @param location Region/location where the bucket will be created
     * @return CompletableFuture containing true if creation succeeded
     */
    public abstract CompletableFuture<Boolean> createBucket(String bucketName, 
        String location);

    /**
     * Deletes an existing object storage bucket.
     *
     * @param bucketName Name of the bucket to delete
     * @return CompletableFuture containing true if deletion succeeded
     */
    public abstract CompletableFuture<Boolean> deleteBucket(String bucketName);

    /**
     * List objects in a bucket with optional prefix filter.
     *
     * @param bucketName Name of the bucket
     * @param objectPrefix Prefix to filter objects
     * @param maxObjects Maximum number of objects to return
     * @return CompletableFuture containing list of object metadata
     */
    public abstract CompletableFuture<List<Map<String, Object>>> listObjects(
        String bucketName, String objectPrefix, int maxObjects);

    /**
     * Close the client and release resources.
     */
    public abstract void close();

    /**
     * Check if the client is healthy.
     *
     * @return true if the client is healthy
     */
    public abstract boolean isHealthy();
}