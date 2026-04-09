/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.store;

import com.openjiuwen.core.foundation.store.object.LocalObjectStorageClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class StoreShowcaseExample {

    @FunctionalInterface
    private interface StepAction {
        boolean run() throws Exception;
    }

    private StoreShowcaseExample() {
    }

    public static void main(String[] args) throws Exception {
        StoreExampleSupport.ensureRuntimeDirectories();
        requireSourceFile();
        StoreExampleSupport.deleteIfExists(StoreExampleSupport.getDownloadFile());

        LocalObjectStorageClient client = StoreExampleSupport.createClient();
        Map<String, Boolean> operationResults = new LinkedHashMap<>();
        AtomicReference<List<Map<String, Object>>> objectsBefore = new AtomicReference<>(List.of());
        AtomicReference<List<Map<String, Object>>> objectsAfter = new AtomicReference<>(List.of());

        printConfiguration();

        runStep(
                "create_bucket",
                "Step 1: Ensure demo bucket exists",
                operationResults,
                () -> {
                    boolean created = client.createBucket(StoreExampleSupport.getBucketName(), "local");
                    boolean exists = Files.isDirectory(StoreExampleSupport.getBucketPath());
                    StoreExampleSupport.keyValue("Bucket", StoreExampleSupport.getBucketName());
                    StoreExampleSupport.keyValue("Bucket path", StoreExampleSupport.describePath(StoreExampleSupport.getBucketPath()));
                    StoreExampleSupport.line("Bucket ready: %s", exists ? "yes" : "no");
                    return created && exists;
                }
        );

        runStep(
                "list_objects_before",
                "Step 2: List objects before operations",
                operationResults,
                () -> {
                    List<Map<String, Object>> objects = client.listObjects(StoreExampleSupport.getBucketName(), "", 100);
                    objectsBefore.set(objects);
                    StoreExampleSupport.keyValue("Object count", objects.size());
                    StoreExampleSupport.printObjectList(objects);
                    return true;
                }
        );

        runStep(
                "delete_stale_object",
                "Step 3: Delete stale demo object if it exists",
                operationResults,
                () -> {
                    Path objectPath = StoreExampleSupport.getObjectPath();
                    boolean existedBefore = Files.exists(objectPath);
                    boolean deleted = !existedBefore || client.deleteObject(
                            StoreExampleSupport.getBucketName(),
                            StoreExampleSupport.getObjectName()
                    );
                    boolean existsAfter = Files.exists(objectPath);
                    StoreExampleSupport.keyValue("Object path", StoreExampleSupport.describePath(objectPath));
                    StoreExampleSupport.keyValue("Existed before", existedBefore);
                    StoreExampleSupport.keyValue("Exists after", existsAfter);
                    return deleted && !existsAfter;
                }
        );

        runStep(
                "upload_file",
                "Step 4: Upload the sample file",
                operationResults,
                () -> {
                    boolean uploaded = client.uploadFile(
                            StoreExampleSupport.getBucketName(),
                            StoreExampleSupport.getObjectName(),
                            StoreExampleSupport.getSourceFile()
                    );
                    boolean exists = Files.isRegularFile(StoreExampleSupport.getObjectPath());
                    StoreExampleSupport.keyValue("Source file", StoreExampleSupport.describePath(StoreExampleSupport.getSourceFile()));
                    StoreExampleSupport.keyValue("Stored object", StoreExampleSupport.describePath(StoreExampleSupport.getObjectPath()));
                    if (exists) {
                        StoreExampleSupport.keyValue("Uploaded bytes", Files.size(StoreExampleSupport.getObjectPath()));
                    }
                    return uploaded && exists;
                }
        );

        runStep(
                "download_file",
                "Step 5: Download the object back to a local file",
                operationResults,
                () -> {
                    boolean downloaded = client.downloadFile(
                            StoreExampleSupport.getBucketName(),
                            StoreExampleSupport.getObjectName(),
                            StoreExampleSupport.getDownloadFile()
                    );
                    boolean exists = Files.isRegularFile(StoreExampleSupport.getDownloadFile());
                    StoreExampleSupport.keyValue("Download target", StoreExampleSupport.describePath(StoreExampleSupport.getDownloadFile()));
                    if (exists) {
                        StoreExampleSupport.keyValue("Downloaded bytes", Files.size(StoreExampleSupport.getDownloadFile()));
                    }
                    return downloaded && exists;
                }
        );

        runStep(
                "verify_download",
                "Step 6: Verify downloaded file",
                operationResults,
                () -> {
                    long sourceSize = Files.size(StoreExampleSupport.getSourceFile());
                    long downloadedSize = Files.size(StoreExampleSupport.getDownloadFile());
                    boolean sameSize = sourceSize == downloadedSize;
                    boolean sameContent = StoreExampleSupport.filesMatch(
                            StoreExampleSupport.getSourceFile(),
                            StoreExampleSupport.getDownloadFile()
                    );
                    StoreExampleSupport.keyValue("Source bytes", sourceSize);
                    StoreExampleSupport.keyValue("Downloaded bytes", downloadedSize);
                    StoreExampleSupport.keyValue("Same size", sameSize);
                    StoreExampleSupport.keyValue("Same content", sameContent);
                    return sameSize && sameContent;
                }
        );

        runStep(
                "list_objects_after",
                "Step 7: List objects after upload",
                operationResults,
                () -> {
                    List<Map<String, Object>> objects = client.listObjects(StoreExampleSupport.getBucketName(), "", 100);
                    objectsAfter.set(objects);
                    boolean foundDemoObject = objects.stream().anyMatch(item -> StoreExampleSupport.getObjectName().equals(item.get("object_name")));
                    StoreExampleSupport.keyValue("Object count", objects.size());
                    StoreExampleSupport.keyValue("Demo object found", foundDemoObject);
                    StoreExampleSupport.printObjectList(objects);
                    return foundDemoObject;
                }
        );

        runStep(
                "delete_uploaded_object",
                "Step 8: Delete the uploaded object",
                operationResults,
                () -> {
                    Path objectPath = StoreExampleSupport.getObjectPath();
                    boolean existedBefore = Files.exists(objectPath);
                    boolean deleted = !existedBefore || client.deleteObject(
                            StoreExampleSupport.getBucketName(),
                            StoreExampleSupport.getObjectName()
                    );
                    boolean existsAfter = Files.exists(objectPath);
                    StoreExampleSupport.keyValue("Existed before", existedBefore);
                    StoreExampleSupport.keyValue("Exists after", existsAfter);
                    return deleted && !existsAfter;
                }
        );

        runStep(
                "cleanup_artifacts",
                "Step 9: Final cleanup",
                operationResults,
                () -> cleanupArtifacts(client)
        );

        printSummary(operationResults, objectsBefore.get(), objectsAfter.get());
    }

    private static void requireSourceFile() {
        Path sourceFile = StoreExampleSupport.getSourceFile();
        if (!Files.isRegularFile(sourceFile)) {
            throw new IllegalStateException("Source file not found: " + sourceFile);
        }
    }

    private static void printConfiguration() throws Exception {
        StoreExampleSupport.section("Java Store Example");
        StoreExampleSupport.keyValue("Example root", StoreExampleSupport.describePath(StoreExampleSupport.getExampleRoot()));
        StoreExampleSupport.keyValue("Source file", StoreExampleSupport.describePath(StoreExampleSupport.getSourceFile()));
        StoreExampleSupport.keyValue("Download file", StoreExampleSupport.describePath(StoreExampleSupport.getDownloadFile()));
        StoreExampleSupport.keyValue("Storage root", StoreExampleSupport.describePath(StoreExampleSupport.getStorageRoot()));
        StoreExampleSupport.keyValue("Bucket", StoreExampleSupport.getBucketName());
        StoreExampleSupport.keyValue("Object", StoreExampleSupport.getObjectName());
        StoreExampleSupport.keyValue("Keep artifacts", StoreExampleSupport.keepArtifacts());
        StoreExampleSupport.keyValue("Source bytes", Files.size(StoreExampleSupport.getSourceFile()));
    }

    private static boolean runStep(
            String key,
            String title,
            Map<String, Boolean> operationResults,
            StepAction action) {
        StoreExampleSupport.section(title);
        boolean success;
        try {
            success = action.run();
        } catch (Exception e) {
            success = false;
            StoreExampleSupport.line("Error: %s", e.getMessage());
        }
        operationResults.put(key, success);
        StoreExampleSupport.line("Result: %s", success ? "SUCCESS" : "FAILURE");
        return success;
    }

    private static boolean cleanupArtifacts(LocalObjectStorageClient client) throws Exception {
        if (StoreExampleSupport.keepArtifacts()) {
            StoreExampleSupport.line("Artifacts were kept for inspection.");
            StoreExampleSupport.keyValue("Bucket path", StoreExampleSupport.describePath(StoreExampleSupport.getBucketPath()));
            StoreExampleSupport.keyValue("Download file", StoreExampleSupport.describePath(StoreExampleSupport.getDownloadFile()));
            return true;
        }

        StoreExampleSupport.deleteIfExists(StoreExampleSupport.getDownloadFile());
        boolean bucketDeleted = client.deleteBucket(StoreExampleSupport.getBucketName());
        boolean downloadMissing = !Files.exists(StoreExampleSupport.getDownloadFile());
        boolean bucketMissing = !Files.exists(StoreExampleSupport.getBucketPath());

        StoreExampleSupport.pruneEmptyDirectories(
                StoreExampleSupport.getDownloadFile().getParent(),
                StoreExampleSupport.getExampleRoot()
        );
        StoreExampleSupport.pruneEmptyDirectories(
                StoreExampleSupport.getStorageRoot(),
                StoreExampleSupport.getExampleRoot()
        );
        StoreExampleSupport.pruneEmptyDirectories(
                StoreExampleSupport.getOutputDir(),
                StoreExampleSupport.getExampleRoot()
        );

        StoreExampleSupport.keyValue("Download removed", downloadMissing);
        StoreExampleSupport.keyValue("Bucket removed", bucketMissing);
        return bucketDeleted && downloadMissing && bucketMissing;
    }

    private static void printSummary(
            Map<String, Boolean> operationResults,
            List<Map<String, Object>> objectsBefore,
            List<Map<String, Object>> objectsAfter) {
        StoreExampleSupport.section("Summary");
        for (Map.Entry<String, Boolean> entry : operationResults.entrySet()) {
            StoreExampleSupport.line(
                    "%-24s %s",
                    entry.getKey() + ":",
                    entry.getValue() ? "SUCCESS" : "FAILURE"
            );
        }
        StoreExampleSupport.subsection("Listing delta");
        StoreExampleSupport.keyValue("Objects before", objectsBefore.size());
        StoreExampleSupport.keyValue("Objects after", objectsAfter.size());
    }
}