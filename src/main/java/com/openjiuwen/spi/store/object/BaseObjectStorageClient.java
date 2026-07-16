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
 * 
 * @since 0.1.7
 */
public abstract class BaseObjectStorageClient {
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
    public abstract boolean uploadFile(String bucketName, String objectName, Path filePath) throws Exception;

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
    public abstract boolean downloadFile(String bucketName, String objectName, Path filePath) throws Exception;

    /**
     * deleteObject.
     * 
     * @param bucketName bucketName
     * @param objectName objectName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract boolean deleteObject(String bucketName, String objectName) throws Exception;

    /**
     * createBucket.
     * 
     * @param bucketName bucketName
     * @param location location
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract boolean createBucket(String bucketName, String location) throws Exception;

    /**
     * deleteBucket.
     * 
     * @param bucketName bucketName
     * @return the result
     * @throws Exception Exception
     * @since 0.1.7
     */
    public abstract boolean deleteBucket(String bucketName) throws Exception;

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
    public abstract List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects)
            throws Exception;
}
