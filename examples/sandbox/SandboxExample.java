/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package examples.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.base.TagMatchStrategy;
import com.openjiuwen.core.singleagent.agents.ReActAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.sysop.BaseFsOperation;
import com.openjiuwen.core.sysop.BaseShellOperation;
import com.openjiuwen.core.sysop.BaseCodeOperation;
import com.openjiuwen.core.sysop.OperationMode;
import com.openjiuwen.core.sysop.SysOperation;
import com.openjiuwen.core.sysop.SysOperationCard;
import com.openjiuwen.core.sysop.config.LocalWorkConfig;
import com.openjiuwen.core.sysop.config.SandboxGatewayConfig;
import com.openjiuwen.core.sysop.result.ExecuteCmdBackgroundResult;
import com.openjiuwen.core.sysop.result.ExecuteCmdResult;
import com.openjiuwen.core.sysop.result.ExecuteCodeResult;
import com.openjiuwen.core.sysop.result.ReadFileResult;
import com.openjiuwen.core.sysop.result.WriteFileResult;
import com.openjiuwen.core.sysop.result.UploadFileResult;
import com.openjiuwen.core.sysop.result.DownloadFileResult;
import com.openjiuwen.core.sysop.result.ListFilesResult;
import com.openjiuwen.core.sysop.result.ListDirsResult;
import com.openjiuwen.core.sysop.result.SearchFilesResult;
import com.openjiuwen.core.sysop.sandbox.SandboxGatewayClient;
import com.openjiuwen.core.sysop.sandbox.SandboxOperationSupport;
import com.openjiuwen.extensions.sys_operation.sandbox.JiuwenBoxSandboxProfile;
import com.openjiuwen.harness.deep_agent.DeepAgent;
import com.openjiuwen.harness.factory.HarnessFactory;
import com.openjiuwen.harness.schema.config.DeepAgentConfig;
import com.openjiuwen.harness.workspace.Workspace;

import examples.utils.SharedExampleApiConfigLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class SandboxExample {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SANDBOX_URL = resolveStringConfig("SANDBOX_URL", "http://127.0.0.1:8321");
    private static final String AGENT_ID = "sandbox_example_agent";
    private static final String SYS_OP_SANDBOX_ID = "sandbox_sysop";
    private static final String SYS_OP_LOCAL_ID = "local_sysop";
    private static final String SYS_OP_FALLBACK_ID = "sandbox_fallback_sysop";
    private static final String SYS_OP_VERIFY_ID = "sandbox_verify_sysop";

    private enum AgentType {
        DEEP,
        REACT;

        String label() {
            return this == DEEP ? "DeepAgent" : "ReActAgent";
        }
    }

    private static class SandboxAgent {
        final AgentType type;
        final String sysOpId;
        final String conversationId;
        final DeepAgent deepAgent;
        final ReActAgent reactAgent;

        SandboxAgent(AgentType type, String sysOpId, String conversationId,
                     DeepAgent deepAgent, ReActAgent reactAgent) {
            this.type = type;
            this.sysOpId = sysOpId;
            this.conversationId = conversationId;
            this.deepAgent = deepAgent;
            this.reactAgent = reactAgent;
        }

        void registerSkill(String skillPath) {
            getInnerAgent().registerSkill(skillPath);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> run(String query) {
            Map<String, Object> inputs = Map.of(
                    "query", query,
                    "conversation_id", conversationId
            );
            if (type == AgentType.DEEP) {
                return (Map<String, Object>) deepAgent.run(inputs);
            }
            return (Map<String, Object>) Runner.runAgent(reactAgent, inputs, null, null);
        }

        void release() {
            Runner.release(conversationId);
            Runner.resourceMgr().removeSysOperation(sysOpId, null, TagMatchStrategy.ALL, true);
        }

        private ReActAgent getInnerAgent() {
            return type == AgentType.DEEP ? deepAgent.getAgent() : reactAgent;
        }
    }

    private SandboxExample() {
    }

    public static void main(String[] args) throws Exception {
        AgentType agentType = parseAgentType(args);
        System.out.println("=== Sandbox Execution Demo (Agent: " + agentType.label() + ") ===");

        Path moduleDir = resolveModuleDir();
        Path skillsDir = resolvePathConfig("SKILLS_DIR",
                moduleDir.resolve("examples").resolve("sandbox").resolve("skills"));
        int maxIterations = Integer.parseInt(resolveStringConfig("MAX_ITERATIONS", "15"));

        com.openjiuwen.core.sysop.sandbox.SandboxRegistryBootstrap.ensureInitialized();

        // System.out.println("\n--- Scenario 1: Normal command using sandbox ---");
        // scenario1_normalCommand();

        // System.out.println("\n--- Scenario 2: Skill commands using sandbox ---");
        // scenario2_skillCommands(skillsDir, maxIterations, agentType);

        // System.out.println("\n--- Scenario 3: Sandbox failure -> fallback to local ---");
        // scenario3_fallbackToLocal();

        // System.out.println("\n--- Scenario 4: Skill with complex Python script ---");
        // scenario4_complexSkillWithDeps(skillsDir, maxIterations, agentType);

        // System.out.println("\n--- Scenario 5: Delete sandbox on completion ---");
        // scenario5_deleteSandbox();

        // System.out.println("\n--- Scenario 5b: Agent reads file with lineRange in sandbox ---");
        // scenario5b_agentFsReadRange(skillsDir, maxIterations, agentType);

        System.out.println("\n--- Scenario 6: FS readFile / writeFile ---");
        scenario6_fsReadAndWrite();

        System.out.println("\n--- Scenario 7: FS listFiles / listDirectories / searchFiles ---");
        scenario7_fsListAndSearch();

        System.out.println("\n--- Scenario 8: FS uploadFile / downloadFile ---");
        scenario8_fsUploadAndDownload();

        System.out.println("\n--- Scenario 9: Shell executeCmdStream / executeCmdBackground ---");
        scenario9_shellStreamAndBackground();

        System.out.println("\n--- Scenario 10: Code executeCode (python/javascript) / executeCodeStream ---");
        scenario10_codeExecution();

        Runner.stop();
        System.out.println("=== Demo Complete ===");
    }

    private static AgentType parseAgentType(String[] args) {
        if (args == null || args.length == 0) {
            return AgentType.DEEP;
        }
        String type = args[0].toLowerCase().trim();
        if ("react".equals(type) || "-react".equals(type) || "--react".equals(type)) {
            return AgentType.REACT;
        }
        return AgentType.DEEP;
    }

    private static SysOperation createVerifySysOp() {
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(SYS_OP_VERIFY_ID)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(JiuwenBoxSandboxProfile.config(SANDBOX_URL))
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);
        return (SysOperation) Runner.resourceMgr().getSysOperation(
                SYS_OP_VERIFY_ID, null, TagMatchStrategy.ALL);
    }

    private static void removeVerifySysOp() {
        Runner.resourceMgr().removeSysOperation(SYS_OP_VERIFY_ID, null, TagMatchStrategy.ALL, true);
    }

    private static void scenario1_normalCommand() {
        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(SYS_OP_SANDBOX_ID)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(JiuwenBoxSandboxProfile.config(SANDBOX_URL))
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);

        SysOperation sysOp = (SysOperation) Runner.resourceMgr().getSysOperation(
                SYS_OP_SANDBOX_ID, null, TagMatchStrategy.ALL);

        ExecuteCmdResult result = sysOp.shell().executeCmd(
                "echo hello_from_sandbox", ".", 30, null, null);

        System.out.println("[Sandbox] Scenario 1: Normal command execution");
        System.out.println("[Sandbox] Command: echo hello_from_sandbox");
        if (result.getData() != null) {
            System.out.println("[Sandbox] Output: " + result.getData().getStdout());
        } else {
            System.out.println("[Sandbox] Result: " + result.getMessage());
        }

        Runner.resourceMgr().removeSysOperation(SYS_OP_SANDBOX_ID, null, TagMatchStrategy.ALL, true);
    }

    private static void scenario2_skillCommands(Path skillsDir, int maxIterations, AgentType agentType) {
        SandboxAgent agent = createSandboxAgent(skillsDir, maxIterations, agentType, "scenario_2");
        agent.registerSkill(skillsDir.resolve("disk_analyzer").toString());

        System.out.println("[Sandbox] " + agentType.label() + " executing in sandbox mode");

        Map<String, Object> result = agent.run(
                "Check the disk usage in the sandbox environment using df -h");

        System.out.println("[Sandbox] " + agentType.label() + " response: " + extractOutput(result));

        agent.release();
    }

    private static void scenario3_fallbackToLocal() {
        SandboxGatewayConfig fallbackConfig = JiuwenBoxSandboxProfile.config(SANDBOX_URL,
                SandboxOperationSupport.paramsOf(
                        "fallback_on_failure", true,
                        "excluded_commands", List.of("dangerous_*")
                ));

        SysOperationCard fallbackCard = SysOperationCard.builder()
                .id(SYS_OP_FALLBACK_ID)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(fallbackConfig)
                .build();
        Runner.resourceMgr().addSysOperation(fallbackCard, null);

        SysOperationCard localCard = SysOperationCard.builder()
                .id(SYS_OP_LOCAL_ID)
                .mode(OperationMode.LOCAL)
                .workConfig(LocalWorkConfig.builder().workDir(null).build())
                .build();
        Runner.resourceMgr().addSysOperation(localCard, null);

        SysOperation fallbackSysOp = (SysOperation) Runner.resourceMgr().getSysOperation(
                SYS_OP_FALLBACK_ID, null, TagMatchStrategy.ALL);
        SysOperation localSysOp = (SysOperation) Runner.resourceMgr().getSysOperation(
                SYS_OP_LOCAL_ID, null, TagMatchStrategy.ALL);

        ExecuteCmdResult sandboxResult = fallbackSysOp.shell().executeCmd(
                "echo sandbox_success", ".", 30, null, null);

        System.out.println("[Sandbox] Scenario 3: Sandbox + fallback configuration");
        System.out.println("[Sandbox] Normal command in sandbox:");
        if (sandboxResult.getData() != null) {
            System.out.println("[Sandbox] Output: " + sandboxResult.getData().getStdout());
        }

        ExecuteCmdResult localResult = localSysOp.shell().executeCmd(
                "echo local_fallback_success", ".", 30, null, null);

        System.out.println("[Sandbox] Command routed to local due to fallback");
        if (localResult.getData() != null) {
            System.out.println("[Sandbox] Local output: " + localResult.getData().getStdout());
        }

        Runner.resourceMgr().removeSysOperation(SYS_OP_FALLBACK_ID, null, TagMatchStrategy.ALL, true);
        Runner.resourceMgr().removeSysOperation(SYS_OP_LOCAL_ID, null, TagMatchStrategy.ALL, true);
    }

    private static void scenario4_complexSkillWithDeps(Path skillsDir, int maxIterations, AgentType agentType) {
        SandboxAgent agent = createSandboxAgent(skillsDir, maxIterations, agentType, "scenario_4");
        agent.registerSkill(skillsDir.resolve("data_processor").toString());

        System.out.println("[Sandbox] " + agentType.label() + " installing dependencies in sandbox");
        System.out.println("[Sandbox] " + agentType.label() + " running Python analysis in sandbox");

        Map<String, Object> result = agent.run(
                "Install numpy to /tmp/pylibs in the sandbox (use: pip install numpy --target=/tmp/pylibs) and analyze the data array [1,2,3,4,5]");

        System.out.println("[Sandbox] " + agentType.label() + " response: " + extractOutput(result));

        agent.release();
    }

    private static void scenario5_deleteSandbox() {
        SandboxGatewayConfig sandboxConfig = JiuwenBoxSandboxProfile.config(SANDBOX_URL);
        String isolationKey = SandboxOperationSupport.resolveIsolationKey(sandboxConfig);

        SandboxGatewayClient.release(isolationKey, "delete");

        System.out.println("[Sandbox] Sandbox released with delete policy");

        try {
            SandboxGatewayClient client = new SandboxGatewayClient(sandboxConfig, isolationKey);
            client.getEndpoint();
            System.out.println("[Sandbox] Warning: sandbox still accessible after release");
        } catch (Exception e) {
            System.out.println("[Sandbox] Sandbox successfully deleted - no longer accessible");
        }
    }

    /**
     * 通过 Agent 在 sandbox 中写入文件并按行范围读取，验证 Agent 能否正确调用
     * writeFile 和 readFile(lineRange) 工具完成 FS 操作。
     * 与 scenario 6（直接调用 SysOperation FS API）形成对照：scenario 6 验证底层能力，
     * 此场景验证 Agent 工具调用链路能否触发相同功能。
     */
    private static void scenario5b_agentFsReadRange(Path skillsDir, int maxIterations, AgentType agentType) {
        SandboxAgent agent = createSandboxAgent(skillsDir, maxIterations, agentType, "scenario_5b");

        System.out.println("[Sandbox] " + agentType.label() + " writing and reading file in sandbox");

        Map<String, Object> result = agent.run(
                "Write a file /tmp/sandbox_agent_test.txt with content 'line1\\nline2\\nline3\\nline4\\nline5' "
                + "in the sandbox, then read the same file with lineRange [1,3] to get only lines 1 to 3.");

        System.out.println("[Sandbox] " + agentType.label() + " response: " + extractOutput(result));

        agent.release();
    }

    private static void scenario6_fsReadAndWrite() {
        SysOperation sysOp = createVerifySysOp();
        BaseFsOperation fs = sysOp.fs();

        System.out.println("[Sandbox] Scenario 6: FS readFile / writeFile verification");

        WriteFileResult writeResult = fs.writeFile(
                "/tmp/sandbox_verify_test.txt",
                "line1\nline2\nline3\nline4\nline5",
                "text",
                false, false, true, null, null, null);
        System.out.println("[Sandbox] writeFile: path=" + writeResult.getData().getPath()
                + " size=" + writeResult.getData().getSize());

        ReadFileResult readFull = fs.readFile(
                "/tmp/sandbox_verify_test.txt", "text", null, null, null, null, 0, null);
        System.out.println("[Sandbox] readFile (full): " + readFull.getData().getContentAsString());

        ReadFileResult readHead = fs.readFile(
                "/tmp/sandbox_verify_test.txt", "text", 2, null, null, null, 0, null);
        System.out.println("[Sandbox] readFile (head=2): " + readHead.getData().getContentAsString());

        ReadFileResult readTail = fs.readFile(
                "/tmp/sandbox_verify_test.txt", "text", null, 2, null, null, 0, null);
        System.out.println("[Sandbox] readFile (tail=2): " + readTail.getData().getContentAsString());

        ReadFileResult readRange = fs.readFile(
                "/tmp/sandbox_verify_test.txt", "text", null, null, new int[]{1, 3}, null, 0, null);
        System.out.println("[Sandbox] readFile (lineRange=[1,3]): " + readRange.getData().getContentAsString());

        WriteFileResult appendResult = fs.writeFile(
                "/tmp/sandbox_verify_test.txt",
                "\nline6_appended",
                "text",
                false, false, true, null, null, null);
        System.out.println("[Sandbox] writeFile (append): size=" + appendResult.getData().getSize());

        ReadFileResult readAfterAppend = fs.readFile(
                "/tmp/sandbox_verify_test.txt", "text", null, null, null, null, 0, null);
        System.out.println("[Sandbox] readFile after append: " + readAfterAppend.getData().getContentAsString());

        ReadFileResult readBytes = fs.readFile(
                "/tmp/sandbox_verify_test.txt", "bytes", null, null, null, null, 0, null);
        System.out.println("[Sandbox] readFile (bytes mode): content type="
                + readBytes.getData().getContent().getClass().getSimpleName()
                + " length=" + (readBytes.getData().getContentAsBytes() != null
                ? readBytes.getData().getContentAsBytes().length : "null"));

        removeVerifySysOp();
    }

    private static void scenario7_fsListAndSearch() {
        SysOperation sysOp = createVerifySysOp();
        BaseFsOperation fs = sysOp.fs();
        BaseShellOperation shell = sysOp.shell();

        System.out.println("[Sandbox] Scenario 7: FS listFiles / listDirectories / searchFiles verification");

        shell.executeCmd("mkdir -p /tmp/sandbox_verify_dir/sub1/sub2", ".", 30, null, null);
        shell.executeCmd("touch /tmp/sandbox_verify_dir/file_a.txt", ".", 30, null, null);
        shell.executeCmd("touch /tmp/sandbox_verify_dir/sub1/file_b.py", ".", 30, null, null);
        shell.executeCmd("touch /tmp/sandbox_verify_dir/sub1/sub2/file_c.log", ".", 30, null, null);

        ListFilesResult listFiles = fs.listFiles(
                "/tmp/sandbox_verify_dir", true, 3, null, false, null, null);
        System.out.println("[Sandbox] listFiles (recursive): totalCount="
                + listFiles.getData().getTotalCount());
        for (var item : listFiles.getData().getListItems()) {
            System.out.println("[Sandbox]   file: " + item.getPath()
                    + " size=" + item.getSize() + " isDir=" + item.isDirectory());
        }

        ListDirsResult listDirs = fs.listDirectories(
                "/tmp/sandbox_verify_dir", true, 3, null, false, null);
        System.out.println("[Sandbox] listDirectories (recursive): totalCount="
                + listDirs.getData().getTotalCount());
        for (var item : listDirs.getData().getListItems()) {
            System.out.println("[Sandbox]   dir: " + item.getPath());
        }

        SearchFilesResult searchTxt = fs.searchFiles(
                "/tmp/sandbox_verify_dir", "*.txt", null);
        System.out.println("[Sandbox] searchFiles (*.txt): totalMatches="
                + searchTxt.getData().getTotalMatches());
        for (var item : searchTxt.getData().getMatchingFiles()) {
            System.out.println("[Sandbox]   match: " + item.getPath());
        }

        SearchFilesResult searchPy = fs.searchFiles(
                "/tmp/sandbox_verify_dir", "*.py", List.of("*.log"));
        System.out.println("[Sandbox] searchFiles (*.py, exclude *.log): totalMatches="
                + searchPy.getData().getTotalMatches());
        for (var item : searchPy.getData().getMatchingFiles()) {
            System.out.println("[Sandbox]   match: " + item.getPath());
        }

        removeVerifySysOp();
    }

    private static void scenario8_fsUploadAndDownload() {
        SysOperation sysOp = createVerifySysOp();
        BaseFsOperation fs = sysOp.fs();

        System.out.println("[Sandbox] Scenario 8: FS uploadFile / downloadFile verification");

        Path tempUploadFile = null;
        Path tempDownloadFile = null;
        try {
            tempUploadFile = Files.createTempFile("sandbox_upload_test", ".txt");
            Files.writeString(tempUploadFile, "upload_test_content_from_local");

            UploadFileResult uploadResult = fs.uploadFile(
                    tempUploadFile.toString(),
                    "/tmp/sandbox_upload_target.txt",
                    true, true, false, 0, null);
            System.out.println("[Sandbox] uploadFile: localPath=" + uploadResult.getData().getLocalPath()
                    + " targetPath=" + uploadResult.getData().getTargetPath()
                    + " size=" + uploadResult.getData().getSize());

            ReadFileResult verifyUpload = fs.readFile(
                    "/tmp/sandbox_upload_target.txt", "text", null, null, null, null, 0, null);
            System.out.println("[Sandbox] readFile after upload: " + verifyUpload.getData().getContentAsString());

            tempDownloadFile = Files.createTempFile("sandbox_download_test", ".txt");

            DownloadFileResult downloadResult = fs.downloadFile(
                    "/tmp/sandbox_upload_target.txt",
                    tempDownloadFile.toString(),
                    true, true, false, 0, null);
            System.out.println("[Sandbox] downloadFile: sourcePath=" + downloadResult.getData().getSourcePath()
                    + " localPath=" + downloadResult.getData().getLocalPath()
                    + " size=" + downloadResult.getData().getSize());

            String downloadedContent = Files.readString(tempDownloadFile);
            System.out.println("[Sandbox] local file content after download: " + downloadedContent);
        } catch (Exception e) {
            System.out.println("[Sandbox] Error in scenario 8: " + e.getMessage());
        } finally {
            try {
                if (tempUploadFile != null) Files.deleteIfExists(tempUploadFile);
                if (tempDownloadFile != null) Files.deleteIfExists(tempDownloadFile);
            } catch (Exception ignored) {
            }
        }

        removeVerifySysOp();
    }

    private static void scenario9_shellStreamAndBackground() {
        SysOperation sysOp = createVerifySysOp();
        BaseShellOperation shell = sysOp.shell();

        System.out.println("[Sandbox] Scenario 9: Shell executeCmdStream / executeCmdBackground verification");

        Iterator<com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult> streamIter =
                shell.executeCmdStream("echo stream_line1; echo stream_line2; echo stream_line3",
                        ".", 30, null, null);
        System.out.println("[Sandbox] executeCmdStream output:");
        int chunkCount = 0;
        while (streamIter.hasNext()) {
            com.openjiuwen.core.sysop.result.ExecuteCmdStreamResult chunk = streamIter.next();
            chunkCount++;
            String text = chunk.getData() != null ? chunk.getData().getText() : "";
            Integer exitCode = chunk.getData() != null ? chunk.getData().getExitCode() : null;
            System.out.println("[Sandbox]   chunk " + chunkCount + ": text=\"" + text
                    + "\" exitCode=" + exitCode);
        }
        System.out.println("[Sandbox] executeCmdStream total chunks: " + chunkCount);

        ExecuteCmdBackgroundResult bgResult = shell.executeCmdBackground(
                "echo background_task_done; sleep 0.1",
                ".", null, 5.0, null);
        System.out.println("[Sandbox] executeCmdBackground: command=" + bgResult.getData().getCommand()
                + " shellType=" + bgResult.getData().getShellType()
                + " code=" + bgResult.getCode());

        removeVerifySysOp();
    }

    private static void scenario10_codeExecution() {
        SysOperation sysOp = createVerifySysOp();
        BaseCodeOperation code = sysOp.code();

        System.out.println("[Sandbox] Scenario 10: Code executeCode / executeCodeStream verification");

        ExecuteCodeResult pyResult = code.executeCode(
                "print('hello_python_sandbox')",
                "python", 30, null, null);
        System.out.println("[Sandbox] executeCode (python): stdout="
                + (pyResult.getData() != null ? pyResult.getData().getStdout() : "null")
                + " exitCode=" + (pyResult.getData() != null ? pyResult.getData().getExitCode() : "null"));

        ExecuteCodeResult jsResult = code.executeCode(
                "console.log('hello_javascript_sandbox')",
                "javascript", 30, null, null);
        System.out.println("[Sandbox] executeCode (javascript): stdout="
                + (jsResult.getData() != null ? jsResult.getData().getStdout() : "null")
                + " exitCode=" + (jsResult.getData() != null ? jsResult.getData().getExitCode() : "null"));

        ExecuteCodeResult pyMultiLine = code.executeCode(
                "import sys\nfor i in range(3):\n    print(f'python_line_{i}')\nprint('python_done')",
                "python", 30, null, null);
        System.out.println("[Sandbox] executeCode (python multiline): stdout="
                + (pyMultiLine.getData() != null ? pyMultiLine.getData().getStdout() : "null"));

        Iterator<com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult> codeStreamIter =
                code.executeCodeStream(
                        "for i in range(5): print(f'code_stream_{i}')",
                        "python", 30, null, null);
        System.out.println("[Sandbox] executeCodeStream output:");
        int codeChunks = 0;
        while (codeStreamIter.hasNext()) {
            com.openjiuwen.core.sysop.result.ExecuteCodeStreamResult chunk = codeStreamIter.next();
            codeChunks++;
            String text = chunk.getData() != null ? chunk.getData().getText() : "";
            Integer exitCode = chunk.getData() != null ? chunk.getData().getExitCode() : null;
            System.out.println("[Sandbox]   code chunk " + codeChunks + ": text=\"" + text
                    + "\" exitCode=" + exitCode);
        }
        System.out.println("[Sandbox] executeCodeStream total chunks: " + codeChunks);

        removeVerifySysOp();
    }

    private static SandboxAgent createSandboxAgent(Path skillsDir, int maxIterations,
                                                   AgentType agentType, String scenarioSuffix) {
        String sysOpId = SYS_OP_SANDBOX_ID + "_" + agentType.name().toLowerCase();
        String conversationId = AGENT_ID + "_" + scenarioSuffix + "_" + agentType.name().toLowerCase();

        SysOperationCard sysOpCard = SysOperationCard.builder()
                .id(sysOpId)
                .mode(OperationMode.SANDBOX)
                .gatewayConfig(JiuwenBoxSandboxProfile.config(SANDBOX_URL))
                .build();
        Runner.resourceMgr().addSysOperation(sysOpCard, null);

        String systemPrompt = "You are an intelligent assistant running in a sandbox environment.\n"
                + "All commands will be executed in the sandbox at " + SANDBOX_URL + "\n"
                + "You may use tools when necessary.\n";

        if (agentType == AgentType.DEEP) {
            DeepAgent deepAgent = buildDeepAgent(skillsDir, maxIterations, sysOpId, systemPrompt);
            addSysOpTools(deepAgent.getAgent(), sysOpId);
            return new SandboxAgent(agentType, sysOpId, conversationId, deepAgent, null);
        }

        ReActAgent reactAgent = buildReActAgent(maxIterations, sysOpId, systemPrompt);
        addSysOpTools(reactAgent, sysOpId);
        return new SandboxAgent(agentType, sysOpId, conversationId, null, reactAgent);
    }

    private static DeepAgent buildDeepAgent(Path skillsDir, int maxIterations,
                                            String sysOpId, String systemPrompt) {
        String id = AGENT_ID + "_" + sysOpId;
        AgentCard agentCard = AgentCard.builder()
                .id(id).name(id)
                .description("Sandbox Example DeepAgent")
                .build();

        Workspace workspace = Workspace.builder()
                .rootPath(skillsDir.toString())
                .language("cn")
                .build();

        DeepAgentConfig config = DeepAgentConfig.builder()
                .systemPrompt(systemPrompt)
                .maxIterations(maxIterations)
                .language("cn")
                .workspacePath(skillsDir.toString())
                .skillDirectories(List.of(skillsDir.toString()))
                .skillMode("all")
                .build();

        configureDeepAgentModel(config);

        return HarnessFactory.createDeepAgent(agentCard, config, workspace);
    }

    private static ReActAgent buildReActAgent(int maxIterations, String sysOpId, String systemPrompt) {
        String id = AGENT_ID + "_" + sysOpId;
        AgentCard agentCard = AgentCard.builder()
                .id(id).name(id)
                .description("Sandbox Example ReActAgent")
                .build();

        ReActAgent agent = new ReActAgent(agentCard);

        ReActAgentConfig config = ReActAgentConfig.builder()
                .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
                .maxIterations(maxIterations)
                .build()
                .configureModelClient(
                        SharedExampleApiConfigLoader.getModelProvider(),
                        SharedExampleApiConfigLoader.getApiKey(),
                        SharedExampleApiConfigLoader.getApiBase(),
                        SharedExampleApiConfigLoader.getModelName(),
                        SharedExampleApiConfigLoader.getSslVerify()
                )
                .configureContextEngine(null, null, false);

        config.setSysOperationId(sysOpId);
        agent.configure(config);

        return agent;
    }

    private static void configureDeepAgentModel(DeepAgentConfig config) {
        config.setModel(Map.of("model", SharedExampleApiConfigLoader.getModelName()));
        config.setBackend(Map.of(
                "client_provider", SharedExampleApiConfigLoader.getModelProvider(),
                "api_key", SharedExampleApiConfigLoader.getApiKey(),
                "api_base", SharedExampleApiConfigLoader.getApiBase(),
                "verify_ssl", SharedExampleApiConfigLoader.getSslVerify()
        ));
    }

    private static void addSysOpTools(com.openjiuwen.core.singleagent.BaseAgent agent, String sysOperationId) {
        for (String[] tool : new String[][]{
                {"fs", "readFile"}, {"code", "executeCode"}, {"shell", "executeCmd"}}) {
            Object toolCard = Runner.resourceMgr().getSysOpToolCards(sysOperationId, tool[0], tool[1]);
            if (toolCard != null) {
                agent.getAbilityManager().add(toolCard);
            }
        }
    }

    private static String extractOutput(Map<String, Object> result) {
        if (result == null) return "null";
        Object output = result.get("output");
        if (output != null) return output.toString();
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

        private static Path resolveModuleDir() {
        Path cwd = Path.of("").toAbsolutePath();
        if (Files.exists(cwd.resolve("pom.xml"))) {
            return cwd;
        }
        Path parent = cwd.getParent();
        if (parent != null && Files.exists(parent.resolve("pom.xml"))) {
            return parent;
        }
        Path grandparent = (parent != null) ? parent.getParent() : null;
        if (grandparent != null && Files.exists(grandparent.resolve("pom.xml"))) {
            return grandparent;
        }
        return cwd;
    }

    private static Path resolvePathConfig(String key, Path defaultPath) {
        String configured = resolveStringConfig(key, "");
        Path path = configured.isBlank() ? defaultPath : Path.of(configured);
        return path.toAbsolutePath().normalize();
    }

    private static String resolveStringConfig(String key, String defaultValue) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String property = System.getProperty(key);
        if (property != null && !property.isBlank()) {
            return property;
        }
        return defaultValue;
    }
}
