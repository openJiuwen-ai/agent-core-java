/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Base class for Object Storage client.
 * <p>
 * Mirrors Python's {@code BaseObjectStorageClient} ABC from
 * <code>foundation/store/object/base_storage_client.py</code>.
 *
 * <p>Provides the interface for basic bucket and object operations such as
 * creating buckets, uploading/downloading files, listing objects, and deleting objects.
 */
public abstract class BaseObjectStorageClient {

    /**
     * Upload a local file to an object storage bucket.
     *
     * @param bucketName name of the target bucket
     * @param objectName object key (path/name)
     * @param filePath   local file path to upload
     * @return true if upload succeeded
     */
    public abstract boolean uploadFile(String bucketName, String objectName, String filePath);

    /**
     * Download an object from object storage.
     *
     * @param bucketName name of the bucket
     * @param objectName object key to download
     * @param filePath   local file path where the object will be saved
     * @return true if download succeeded
     */
    public abstract boolean downloadFile(String bucketName, String objectName, String filePath);

    /**
     * Delete an object from an object storage bucket.
     *
     * @param bucketName name of the bucket
     * @param objectName object key to delete
     * @return true if deletion succeeded
     */
    public abstract boolean deleteObject(String bucketName, String objectName);

    /**
     * Create a new object storage bucket.
     *
     * @param bucketName name of the bucket to be created
     * @param location   region/location where the bucket will be created
     * @return true if creation succeeded
     */
    public abstract boolean createBucket(String bucketName, String location);

    /**
     * Delete an existing object storage bucket.
     *
     * @param bucketName name of the bucket to delete
     * @return true if deletion succeeded
     */
    public abstract boolean deleteBucket(String bucketName);

    /**
     * List objects in a bucket with a given prefix.
     *
     * @param bucketName  name of the bucket
     * @param objectPrefix prefix to filter objects
     * @param maxObjects  maximum number of objects to return
     * @return list of object metadata maps, or null on failure
     */
    public abstract List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects);

    /**
     * Check if a bucket exists.
     *
     * @param bucketName name of the bucket
     * @return true if the bucket exists
     */
    public abstract boolean bucketExists(String bucketName);
}
