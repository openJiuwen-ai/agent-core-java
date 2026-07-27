package com.openjiuwen.core.sysop.sandbox;

import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.local.LocalCodeOperation;
import com.openjiuwen.core.sysop.local.LocalFsOperation;
import com.openjiuwen.core.sysop.local.LocalShellOperation;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundData;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.ReadFileStreamResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Flow;

/**
 * Test-only local providers mirroring Python's test-local sandbox provider pattern.
 */
final class SandboxTestLocalProviders {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private SandboxTestLocalProviders() {
    }

    static void ensureRegistered() {
        if (INITIALIZED.compareAndSet(false, true)) {
            SandboxRegistry.registerProvider("local", "fs", TestLocalFsProvider.class);
            SandboxRegistry.registerProvider("local", "shell", TestLocalShellProvider.class);
            SandboxRegistry.registerProvider("local", "code", TestLocalCodeProvider.class);
        }
    }

    public static final class TestLocalFsProvider extends LocalFsOperation {

        private final com.openjiuwen.core.sys_operation.local.LocalFsOperation newDelegate;

        public TestLocalFsProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(SandboxOperationSupport.toLocalWorkConfig(config));
            this.newDelegate = new com.openjiuwen.core.sys_operation.local.LocalFsOperation(
                    "fs",
                    com.openjiuwen.core.sys_operation.OperationMode.LOCAL,
                    "local fs operation",
                    SandboxOperationSupport.toLocalWorkConfig(config));
        }

        /**
         * Write file - delegates to new implementation and unwraps CompletableFuture.
         */
        public WriteFileResult writeFile(String path, Object content, String mode,
                                         boolean isPrependNewline, boolean isAppendNewline,
                                         boolean isCreateIfMissing, String permissions,
                                         String encoding, Map<String, Object> options) {
            com.openjiuwen.core.sys_operation.result.WriteFileResult newResult =
                    newDelegate.writeFile(
                            path,
                            content == null ? null : String.valueOf(content),
                            com.openjiuwen.core.sys_operation.BaseFsOperation.FileMode.fromValue(mode),
                            isPrependNewline,
                            isAppendNewline,
                            false,
                            isCreateIfMissing,
                            permissions,
                            encoding,
                            options).join();
            WriteFileResult result = new WriteFileResult();
            result.setCode(newResult.getCode());
            result.setMessage(newResult.getMessage());
            if (newResult.getData() != null) {
                com.openjiuwen.core.sysop.result.WriteFileData data = new com.openjiuwen.core.sysop.result.WriteFileData();
                data.setPath(newResult.getData().getPath());
                data.setSize(newResult.getData().getSize());
                data.setMode(newResult.getData().getMode());
                result.setData(data);
            }
            return result;
        }

        /**
         * Read file stream - delegates to new implementation and converts Flow.Publisher to Iterator.
         */
        public Iterator<ReadFileStreamResult> readFileStream(String path, String mode,
                                                              Integer head, Integer tail,
                                                              int[] lineRange, String encoding,
                                                              int chunkSize, Map<String, Object> options) {
            com.openjiuwen.core.sys_operation.BaseFsOperation.FileMode fileMode =
                    com.openjiuwen.core.sys_operation.BaseFsOperation.FileMode.fromValue(mode);
            com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal.LineRange lr =
                    lineRange != null && lineRange.length >= 2
                            ? new com.openjiuwen.core.sys_operation.protocal.BaseFsProtocal.LineRange(lineRange[0], lineRange[1])
                            : null;
            Flow.Publisher<com.openjiuwen.core.sys_operation.result.ReadFileStreamResult> publisher =
                    newDelegate.readFileStream(path, fileMode, head, tail, lr, encoding, chunkSize, options);
            java.util.List<ReadFileStreamResult> items = new java.util.ArrayList<>();
            publisher.subscribe(new Flow.Subscriber<>() {
                Flow.Subscription subscription;
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                }
                @Override
                public void onNext(com.openjiuwen.core.sys_operation.result.ReadFileStreamResult item) {
                    ReadFileStreamResult legacy = new ReadFileStreamResult();
                    legacy.setCode(item.getCode());
                    legacy.setMessage(item.getMessage());
                    if (item.getData() != null) {
                        com.openjiuwen.core.sysop.result.ReadFileChunkData data = new com.openjiuwen.core.sysop.result.ReadFileChunkData();
                        data.setPath(item.getData().getPath());
                        data.setChunkContent(item.getData().getChunkContent());
                        data.setMode(item.getData().getMode());
                        data.setChunkSize(item.getData().getChunkSize());
                        data.setChunkIndex(item.getData().getChunkIndex());
                        data.setLastChunk(item.getData().isLastChunk());
                        legacy.setData(data);
                    }
                    items.add(legacy);
                }
                @Override
                public void onError(Throwable t) {
                    throw new RuntimeException(t);
                }
                @Override
                public void onComplete() {
                }
            });
            return items.iterator();
        }

        /**
         * Search files - delegates to new implementation and unwraps CompletableFuture.
         */
        public SearchFilesResult searchFiles(String path, String pattern, List<String> excludePatterns) {
            com.openjiuwen.core.sys_operation.result.SearchFilesResult newResult =
                    newDelegate.searchFiles(path, pattern, excludePatterns).join();
            SearchFilesResult result = new SearchFilesResult();
            result.setCode(newResult.getCode());
            result.setMessage(newResult.getMessage());
            if (newResult.getData() != null) {
                com.openjiuwen.core.sysop.result.SearchFilesData data = new com.openjiuwen.core.sysop.result.SearchFilesData();
                data.setTotalMatches(newResult.getData().getTotalMatches());
                data.setSearchPath(newResult.getData().getSearchPath());
                data.setSearchPattern(newResult.getData().getSearchPattern());
                data.setExcludePatterns(newResult.getData().getExcludePatterns());
                if (newResult.getData().getMatchingFiles() != null) {
                    data.setMatchingFiles(newResult.getData().getMatchingFiles().stream().map(item -> {
                        com.openjiuwen.core.sysop.result.FileSystemItem fi = new com.openjiuwen.core.sysop.result.FileSystemItem();
                        fi.setName(item.getName());
                        fi.setPath(item.getPath());
                        fi.setSize(item.getSize());
                        fi.setModifiedTime(item.getModifiedTime());
                        fi.setDirectory(item.isDirectory());
                        fi.setType(item.getType());
                        return fi;
                    }).toList());
                }
                result.setData(data);
            }
            return result;
        }
    }

    public static final class TestLocalShellProvider extends LocalShellOperation {

        private final com.openjiuwen.core.sys_operation.local.LocalShellOperation newDelegate;

        public TestLocalShellProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(SandboxOperationSupport.toLocalWorkConfig(config));
            this.newDelegate = new com.openjiuwen.core.sys_operation.local.LocalShellOperation(
                    "shell",
                    com.openjiuwen.core.sys_operation.OperationMode.LOCAL,
                    "local shell operation",
                    SandboxOperationSupport.toLocalWorkConfig(config));
        }

        /**
         * Execute command in background - delegates to new implementation and unwraps CompletableFuture.
         */
        public ExecuteCmdBackgroundResult executeCmdBackground(String command, String cwd,
                                                                Map<String, String> environment,
                                                                double grace, Map<String, Object> options) {
            com.openjiuwen.core.sys_operation.result.ExecuteCmdBackgroundResult newResult =
                    newDelegate.executeCmdBackground(
                            command,
                            cwd,
                            environment,
                            grace,
                            com.openjiuwen.core.sys_operation.BaseShellOperation.ShellType.AUTO).join();
            ExecuteCmdBackgroundData legacyData = null;
            if (newResult.getData() != null) {
                legacyData = ExecuteCmdBackgroundData.builder()
                        .command(newResult.getData().getCommand())
                        .cwd(newResult.getData().getCwd())
                        .pid(newResult.getData().getPid() != null
                                ? newResult.getData().getPid().longValue() : null)
                        .build();
            }
            return new ExecuteCmdBackgroundResult(newResult.getCode(), newResult.getMessage(), legacyData);
        }

        /**
         * Execute command stream - delegates to new implementation and converts Flow.Publisher to Iterator.
         */
        public Iterator<com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult> executeCmdStream(
                String command, String cwd, int timeout,
                Map<String, String> environment, Map<String, Object> options) {
            Flow.Publisher<com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult> publisher =
                    newDelegate.executeCmdStream(
                            command,
                            cwd,
                            timeout,
                            environment,
                            options,
                            com.openjiuwen.core.sys_operation.BaseShellOperation.ShellType.AUTO);
            java.util.List<com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult> items = new java.util.ArrayList<>();
            publisher.subscribe(new Flow.Subscriber<>() {
                Flow.Subscription subscription;
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    this.subscription = s;
                    s.request(Long.MAX_VALUE);
                }
                @Override
                public void onNext(com.openjiuwen.core.sys_operation.result.ExecuteCmdStreamResult item) {
                    com.openjiuwen.core.sysop.result.ExecuteCmdChunkData legacyData = null;
                    if (item.getData() != null) {
                        legacyData = com.openjiuwen.core.sysop.result.ExecuteCmdChunkData.builder()
                                .text(item.getData().getText())
                                .type(item.getData().getType())
                                .chunkIndex(item.getData().getChunkIndex())
                                .exitCode(item.getData().getExitCode())
                                .metadata(item.getData().getMetadata())
                                .build();
                    }
                    items.add(new com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult(
                            item.getCode(), item.getMessage(), legacyData));
                }
                @Override
                public void onError(Throwable t) {
                    throw new RuntimeException(t);
                }
                @Override
                public void onComplete() {
                }
            });
            return items.iterator();
        }
    }

    public static final class TestLocalCodeProvider extends LocalCodeOperation {

        private final SandboxGatewayConfig config;

        public TestLocalCodeProvider(SandboxEndpoint endpoint, SandboxGatewayConfig config) {
            super(SandboxOperationSupport.toLocalWorkConfig(config));
            this.config = config;
        }

        @Override
        public ExecuteCodeResult executeCode(String code, String language, int timeout,
                                             Map<String, String> environment, Map<String, Object> options) {
            return super.executeCode(
                    SandboxOperationSupport.wrapCodeWithSandboxCwd(code, language, config),
                    language,
                    timeout,
                    environment,
                    options
            );
        }

        @Override
        public Iterator<ExecuteCodeStreamResult> executeCodeStream(String code, String language, int timeout,
                                                                   Map<String, String> environment,
                                                                   Map<String, Object> options) {
            return super.executeCodeStream(
                    SandboxOperationSupport.wrapCodeWithSandboxCwd(code, language, config),
                    language,
                    timeout,
                    environment,
                    options
            );
        }
    }
}
