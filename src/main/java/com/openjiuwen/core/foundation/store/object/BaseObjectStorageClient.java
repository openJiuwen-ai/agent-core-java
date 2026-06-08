/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import java.util.List;
import java.util.Map;

/**
 * Base class for Object Storage client.
 * <p>
 * Mirrors Python's {@code BaseObjectStorageClient} in
 * {@code openjiuwen/core/foundation/store/object/base_storage_client.py}.
 *
 * <p>Provides the interface for basic bucket and object operations such as
 * creating buckets, uploading/downloading files, listing objects, and deleting objects.
 */
public abstract class BaseObjectStorageClient {

    public abstract boolean uploadFile(String bucketName, String objectName, String filePath);

    public abstract boolean downloadFile(String bucketName, String objectName, String filePath);

    public abstract boolean deleteObject(String bucketName, String objectName);

    public abstract boolean createBucket(String bucketName, String location);

    public abstract boolean deleteBucket(String bucketName);

    public abstract List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects);
}
