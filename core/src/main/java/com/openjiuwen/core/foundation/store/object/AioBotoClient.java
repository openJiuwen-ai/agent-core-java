/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.store.object;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.logging.Loggers;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Async S3 client implementation using aioboto3 semantics.
 * <p>
 * Mirrors Python's {@code AioBotoClient} in
 * {@code openjiuwen/core/foundation/store/object/aioboto_storage_client.py}.
 *
 * <p>Python async methods are translated to synchronous AWS SDK calls.</p>
 */
public class AioBotoClient extends BaseObjectStorageClient {

    private static final LoggerProtocol LOGGER = Loggers.COMMON;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final S3Client s3Client;

    public AioBotoClient() {
        this(null, null, null, null);
    }

    public AioBotoClient(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public AioBotoClient(
            String server,
            String accessKeyId,
            String secretAccessKey,
            String regionName) {
        System.setProperty("software.amazon.awssdk.http.request.checksum.calculation", "WHEN_REQUIRED");
        System.setProperty("software.amazon.awssdk.http.response.checksum.validation", "WHEN_REQUIRED");

        String resolvedAccessKeyId = accessKeyId != null ? accessKeyId : System.getenv("OBS_ACCESS_KEY_ID");
        String resolvedSecretAccessKey = secretAccessKey != null ? secretAccessKey : System.getenv("OBS_SECRET_ACCESS_KEY");
        String resolvedServer = server != null ? server : System.getenv("OBS_SERVER");
        String resolvedRegion = regionName != null ? regionName : System.getenv("OBS_REGION");

        S3Configuration s3Configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(false)
                .checksumValidationEnabled(false)
                .build();
        var builder = S3Client.builder().serviceConfiguration(s3Configuration);

        if (resolvedAccessKeyId != null && resolvedSecretAccessKey != null) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(resolvedAccessKeyId, resolvedSecretAccessKey)));
        }
        if (resolvedRegion != null) {
            builder.region(Region.of(resolvedRegion));
        }
        if (resolvedServer != null) {
            builder.endpointOverride(URI.create(resolvedServer));
        }

        this.s3Client = builder.build();
    }

    protected S3Client createClient() {
        return s3Client;
    }

    @Override
    public boolean createBucket(String bucketName, String location) {
        try {
            Map<String, String> locationConstraint = new LinkedHashMap<>();
            locationConstraint.put("LocationConstraint", location);
            createClient().createBucket(
                    CreateBucketRequest.builder()
                            .bucket(bucketName)
                            .createBucketConfiguration(
                                    CreateBucketConfiguration.builder()
                                            .locationConstraint(location)
                                            .build())
                            .build());
            LOGGER.info("Bucket \"" + bucketName + "\" created successfully in region \"" + location + "\"");
            return true;
        } catch (S3Exception exception) {
            LOGGER.error("Create Bucket \"" + bucketName + "\" failed: " + exception.awsErrorDetails());
            return false;
        }
    }

    @Override
    public boolean deleteBucket(String bucketName) {
        try {
            createClient().deleteBucket(DeleteBucketRequest.builder().bucket(bucketName).build());
            LOGGER.info("Bucket \"" + bucketName + "\" deleted successfully");
            return true;
        } catch (S3Exception exception) {
            LOGGER.error("Delete Bucket \"" + bucketName + "\" failed: " + exception.awsErrorDetails());
            return false;
        }
    }

    @Override
    public boolean uploadFile(String bucketName, String objectName, String filePath) {
        try {
            createClient().putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build(),
                    Path.of(filePath));
            LOGGER.info("Upload \"" + objectName + "\" file \"" + filePath + "\" to bucket \"" + bucketName + "\" succeeded");
            return true;
        } catch (S3Exception exception) {
            LOGGER.error("Upload \"" + objectName + "\" failed: " + exception.awsErrorDetails());
            return false;
        }
    }

    @Override
    public boolean downloadFile(String bucketName, String objectName, String filePath) {
        try {
            createClient().getObject(
                    GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build(),
                    Path.of(filePath));
            LOGGER.info("Download \"" + objectName + "\" from bucket \"" + bucketName + "\" saved to \"" + filePath + "\"");
            return true;
        } catch (S3Exception exception) {
            LOGGER.error("Download \"" + objectName + "\" failed: " + exception.awsErrorDetails());
            return false;
        }
    }

    @Override
    public boolean deleteObject(String bucketName, String objectName) {
        try {
            createClient().deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .build());
            LOGGER.info("Delete file \"" + objectName + "\" in bucket \"" + bucketName + "\" succeeded");
            return true;
        } catch (S3Exception exception) {
            LOGGER.error("Delete file \"" + objectName + "\" failed: " + exception.awsErrorDetails());
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> listObjects(String bucketName, String objectPrefix, int maxObjects) {
        try {
            ListObjectsV2Response response = createClient().listObjectsV2(
                    ListObjectsV2Request.builder()
                            .bucket(bucketName)
                            .prefix(objectPrefix)
                            .maxKeys(maxObjects)
                            .build());
            List<Map<String, Object>> contents = new ArrayList<>();
            for (S3Object object : response.contents()) {
                Map<String, Object> objectInfo = new LinkedHashMap<>();
                objectInfo.put("Key", object.key());
                if (object.size() != null) {
                    objectInfo.put("Size", object.size());
                }
                if (object.lastModified() != null) {
                    objectInfo.put("LastModified", object.lastModified());
                }
                if (object.eTag() != null) {
                    objectInfo.put("ETag", object.eTag());
                }
                contents.add(objectInfo);
                logObjectInfo(objectInfo);
            }
            LOGGER.info("Successfully listed " + contents.size() + " objects in \"" + bucketName + "\".");
            return contents;
        } catch (S3Exception exception) {
            LOGGER.error("List objects in \"" + bucketName + "\" failed: " + exception.awsErrorDetails());
            return null;
        }
    }

    private void logObjectInfo(Map<String, Object> objectInfo) {
        try {
            LOGGER.info(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(objectInfo));
        } catch (JsonProcessingException exception) {
            LOGGER.info(objectInfo.toString());
        }
    }
}
