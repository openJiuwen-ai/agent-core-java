/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.spi.store.object;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for Object Storage clients.
 * <p>
 * Provides the interface for basic bucket and object operations.
 * Mirrors Python's {@code BaseObjectStorageClient} ABC.
 */
public abstract class BaseObjectStorageClient {

    /**
     * Upload a local file to an object storage bucket.
     *
     * @param bucketName target bucket
     * @param objectName object key (path/name)
     * @param filePath   local file path
     * @return true if upload succeeded
     */
    public abstract boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception;

    /**
     * Download an object from object storage.
     *
     * @param bucketName bucket name
     * @param objectName object key
     * @param filePath   local destination path
     * @return true if download succeeded
     */
    public abstract boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception;

    /**
     * Delete an object from a bucket.
     *
     * @param bucketName bucket name
     * @param objectName object key
     * @return true if deletion succeeded
     */
    public abstract boolean deleteObject(String bucketName, String objectName) throws Exception;

    /**
     * Create a new bucket.
     *
     * @param bucketName bucket name
     * @param location   region/location
     * @return true if creation succeeded
     */
    public abstract boolean createBucket(String bucketName, String location) throws Exception;

    /**
     * Delete an existing bucket.
     *
     * @param bucketName bucket name
     * @return true if deletion succeeded
     */
    public abstract boolean deleteBucket(String bucketName) throws Exception;

    /**
     * List objects in a bucket with the given prefix.
     *
     * @param bucketName   bucket name
     * @param objectPrefix prefix filter
     * @param maxObjects   max number of objects to list
     * @return list of object metadata maps, or null
     */
    public abstract List<Map<String, Object>> listObjects(String bucketName,
                                                          String objectPrefix,
                                                          int maxObjects) throws Exception;
}
