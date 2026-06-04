/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S3-compatible object storage client implementation using AWS SDK v2.
 * <p>
 * Mirrors Python's {@code AioBotoClient} from
 * {@code foundation/store/object/aioboto_storage_client.py}.
 *
 * <p>Note: Python async methods are translated to synchronous blocking calls
 * suitable for execution on virtual threads.
 */
public class S3ObjectStorageClient extends BaseObjectStorageClient {

    private static final Logger logger = LoggerFactory.getLogger(S3ObjectStorageClient.class);

    private final S3Client s3Client;

    public S3ObjectStorageClient(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Construct S3 client with configuration from environment or explicit parameters.
     *
     * @param server          S3 endpoint URL (null uses OBS_SERVER env or AWS default)
     * @param accessKeyId     access key (null uses OBS_ACCESS_KEY_ID env)
     * @param secretAccessKey secret key (null uses OBS_SECRET_ACCESS_KEY env)
     * @param regionName      region name (null uses OBS_REGION env or default)
     */
    public S3ObjectStorageClient(String server, String accessKeyId, String secretAccessKey, String regionName) {
        // Resolve configuration from environment variables if not provided
        String resolvedAccessKeyId = accessKeyId != null ? accessKeyId :
                System.getenv("OBS_ACCESS_KEY_ID");
        String resolvedSecretAccessKey = secretAccessKey != null ? secretAccessKey :
                System.getenv("OBS_SECRET_ACCESS_KEY");
        String resolvedServer = server != null ? server :
                System.getenv("OBS_SERVER");
        String resolvedRegion = regionName != null ? regionName :
                System.getenv("OBS_REGION");

        // Configure checksum calculation settings for compatibility
        System.setProperty("software.amazon.awssdk.http.request.checksum.calculation", "WHEN_REQUIRED");
        System.setProperty("software.amazon.awssdk.http.response.checksum.validation", "WHEN_REQUIRED");

        // Build S3 client
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(false)
                .checksumValidationEnabled(false)
                .build();

        Region region = resolvedRegion != null ?
                Region.of(resolvedRegion) : Region.US_EAST_1;

        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                resolvedAccessKeyId != null ? resolvedAccessKeyId : "",
                resolvedSecretAccessKey != null ? resolvedSecretAccessKey : "");

        this.s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(region)
                .endpointOverride(resolvedServer != null ? java.net.URI.create(resolvedServer) : null)
                .serviceConfiguration(s3Config)
                .build();
    }

    @Override
    public boolean uploadFile(String bucketName, String objectName, String filePath) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build(),
                    Path.of(filePath));

            logger.info("Upload '{}' to bucket '{}' succeeded", objectName, bucketName);
            return true;
        } catch (S3Exception e) {
            logger.error("Upload '{}' failed: {}", objectName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean downloadFile(String bucketName, String objectName, String filePath) {
        try {
            s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build(),
                    Path.of(filePath));

            logger.info("Download '{}' from bucket '{}' saved to '{}'", objectName, bucketName, filePath);
            return true;
        } catch (S3Exception e) {
            logger.error("Download '{}' failed: {}", objectName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteObject(String bucketName, String objectName) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build());

            logger.info("Delete object '{}' in bucket '{}' succeeded", objectName, bucketName);
            return true;
        } catch (S3Exception e) {
            logger.error("Delete object '{}' failed: {}", objectName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean createBucket(String bucketName, String location) {
        try {
            CreateBucketRequest.Builder builder = CreateBucketRequest.builder()
                    .bucket(bucketName);

            if (location != null && !location.equals("us-east-1")) {
                builder.createBucketConfiguration(
                        CreateBucketConfiguration.builder()
                                .locationConstraint(location)
                                .build());
            }

            s3Client.createBucket(builder.build());

            logger.info("Bucket '{}' created successfully in region '{}'", bucketName, location);
            return true;
        } catch (S3Exception e) {
            logger.error("Create bucket '{}' failed: {}", bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        try {
            s3Client.deleteBucket(
                    DeleteBucketRequest.builder()
                            .bucket(bucketName)
                            .build());

            logger.info("Bucket '{}' deleted successfully", bucketName);
            return true;
        } catch (S3Exception e) {
            logger.error("Delete bucket '{}' failed: {}", bucketName, e.getMessage());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) {
        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(objectPrefix)
                            .maxKeys(maxObjects)
                            .build());

            List<Map<String, Object>> contents = new ArrayList<>();
            for (S3Object obj : response.contents()) {
                Map<String, Object> objectInfo = new HashMap<>();
                objectInfo.put("Key", obj.key());
                objectInfo.put("Size", obj.size());
                objectInfo.put("LastModified", obj.lastModified());
                objectInfo.put("ETag", obj.eTag());
                contents.add(objectInfo);

                logger.info("Listed object: Key={}, Size={}", obj.key(), obj.size());
            }

            logger.info("Successfully listed {} objects in '{}'", contents.size(), bucketName);
            return contents;
        } catch (S3Exception e) {
            logger.error("List objects in '{}' failed: {}", bucketName, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean bucketExists(String bucketName) {
        try {
            s3Client.headBucket(
                    HeadBucketRequest.builder()
                            .bucket(bucketName)
                            .build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        } catch (S3Exception e) {
            logger.error("Check bucket '{}' existence failed: {}", bucketName, e.getMessage());
            return false;
        }
    }

    /**
     * Close the S3 client and release resources.
     */
    public void close() {
        s3Client.close();
    }
}
