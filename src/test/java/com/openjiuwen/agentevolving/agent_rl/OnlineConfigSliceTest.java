package com.openjiuwen.agentevolving.agent_rl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.GatewayServiceConfig;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.JiuwenConfig;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.JudgeConfig;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.OnlineConfigDefaults;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.OnlineRLConfig;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.TrajectoryConfig;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.TrainingConfig;
import com.openjiuwen.agentevolving.agent_rl.config.onlineconfig.VLLMServiceConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineConfigSliceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void nestedOnlineConfigDefaultsMatchPythonBaseline() {
        VLLMServiceConfig inference = new VLLMServiceConfig();
        assertEquals("/path/to/your/model", inference.getModel_path());
        assertEquals("Qwen3-4B-Thinking-2507", inference.getModel_name());
        assertEquals("127.0.0.1", inference.getHost());
        assertNull(inference.getPort());
        assertEquals("0,1", inference.getGpu_ids());
        assertEquals(2, inference.getTp());
        assertNull(inference.getExisting_url());
        assertEquals(300.0, inference.getHealth_timeout());
        assertEquals(Map.of("VLLM_ALLOW_RUNTIME_LORA_UPDATING", "1"), inference.getEnv());
        assertEquals(List.of(
                "--enable-lora",
                "--max-loras",
                "4",
                "--max-lora-rank",
                "32",
                "--enable-auto-tool-choice",
                "--tool-call-parser",
                "hermes",
                "--max-model-len",
                "32768",
                "--gpu-memory-utilization",
                "0.85"
        ), inference.getExtra_args());

        JudgeConfig judge = new JudgeConfig();
        assertEquals("2,3", judge.getGpu_ids());
        assertEquals(600.0, judge.getHealth_timeout());
        assertTrue(judge.isReuse_inference_if_same_model());
        assertTrue(judge.getEnv().isEmpty());
        assertEquals(List.of(
                "--max-model-len",
                "8192",
                "--gpu-memory-utilization",
                "0.85",
                "--max-num-seqs",
                "16"
        ), judge.getExtra_args());

        GatewayServiceConfig gateway = new GatewayServiceConfig();
        assertEquals("127.0.0.1", gateway.getHost());
        assertNull(gateway.getPort());
        assertNull(gateway.getRedis_url());
        assertEquals("records", gateway.getRecord_dir());
        assertEquals("info", gateway.getLog_level());
        assertEquals(30.0, gateway.getHealth_timeout());
        assertTrue(gateway.isDisable_trajectory_collection());
        assertTrue(gateway.getEnv().isEmpty());

        TrajectoryConfig trajectory = new TrajectoryConfig();
        assertEquals(4, trajectory.getBatch_size());
        assertEquals("feedback_level", trajectory.getMode());

        TrainingConfig training = new TrainingConfig();
        assertEquals("4,5", training.getGpu_ids());
        assertEquals(4, training.getThreshold());
        assertEquals(30, training.getScan_interval());
        assertNull(training.getPpo_config());
        assertNull(training.getLora_repo());

        JiuwenConfig jiuwen = new JiuwenConfig();
        assertTrue(jiuwen.isEnabled());
        assertNull(jiuwen.getAgent_server_port());
        assertEquals("127.0.0.1", jiuwen.getApp_host());
        assertNull(jiuwen.getWs_port());
        assertEquals("127.0.0.1", jiuwen.getWeb_host());
        assertNull(jiuwen.getWeb_port());
    }

    @Test
    void mutableDefaultsStayIndependentAcrossInstances() {
        VLLMServiceConfig firstInference = new VLLMServiceConfig();
        VLLMServiceConfig secondInference = new VLLMServiceConfig();
        assertNotSame(firstInference.getEnv(), secondInference.getEnv());
        assertNotSame(firstInference.getExtra_args(), secondInference.getExtra_args());

        firstInference.getEnv().put("NEW_KEY", "x");
        firstInference.getExtra_args().add("--new-flag");
        assertFalse(secondInference.getEnv().containsKey("NEW_KEY"));
        assertFalse(secondInference.getExtra_args().contains("--new-flag"));

        JudgeConfig firstJudge = new JudgeConfig();
        JudgeConfig secondJudge = new JudgeConfig();
        assertNotSame(firstJudge.getEnv(), secondJudge.getEnv());
        assertNotSame(firstJudge.getExtra_args(), secondJudge.getExtra_args());

        GatewayServiceConfig firstGateway = new GatewayServiceConfig();
        GatewayServiceConfig secondGateway = new GatewayServiceConfig();
        assertNotSame(firstGateway.getEnv(), secondGateway.getEnv());
    }

    @Test
    void onlineConfigSyncsJudgeModelFromInferenceAndValidatesRequiredLaunchFields() {
        OnlineRLConfig config = new OnlineRLConfig();
        config.getInference().setModel_path("/models/inference");
        config.getInference().setModel_name("model-a");
        config.getInference().setPort(18000);
        config.getJudge().setModel_path("/models/custom-judge");
        config.getJudge().setModel_name("model-b");
        config.getJudge().setPort(18001);
        config.getGateway().setPort(18080);
        config.getGateway().setRedis_url("redis://127.0.0.1:6379/0");
        config.getJiuwen().setAgent_server_port(18092);
        config.getJiuwen().setWs_port(19000);
        config.getJiuwen().setWeb_port(5173);

        OnlineRLConfig validated = config.syncAndValidateLaunch();

        assertEquals(config, validated);
        assertEquals("/models/inference", config.getJudge().getModel_path());
        assertEquals("model-a", config.getJudge().getModel_name());
    }

    @Test
    void onlineConfigAllowsDisabledJiuwenAndExistingServiceUrls() {
        OnlineRLConfig config = new OnlineRLConfig();
        config.getInference().setExisting_url("http://127.0.0.1:18000");
        config.getJudge().setExisting_url("http://127.0.0.1:18001");
        config.getGateway().setPort(18080);
        config.getGateway().setRedis_url("redis://127.0.0.1:6379/0");
        config.getJiuwen().setEnabled(false);

        config.syncAndValidateLaunch();

        assertFalse(config.getJiuwen().isEnabled());
    }

    @Test
    void onlineConfigValidationRejectsMissingRequiredFieldsAndInvalidNumericRanges() {
        OnlineRLConfig config = new OnlineRLConfig();
        config.getInference().setPort(18000);
        config.getJudge().setPort(18001);

        IllegalArgumentException gatewayPortError = assertThrows(
                IllegalArgumentException.class,
                config::syncAndValidateLaunch
        );
        assertEquals("gateway.port is required (--gateway-port or YAML).", gatewayPortError.getMessage());

        config.getGateway().setPort(18080);
        IllegalArgumentException redisError = assertThrows(
                IllegalArgumentException.class,
                config::syncAndValidateLaunch
        );
        assertEquals("gateway.redis_url is required (--redis-url or YAML).", redisError.getMessage());

        config.getGateway().setRedis_url("redis://127.0.0.1:6379/0");
        IllegalArgumentException jiuwenError = assertThrows(
                IllegalArgumentException.class,
                config::syncAndValidateLaunch
        );
        assertEquals(
                "jiuwen.agent_server_port is required when jiuwen.enabled is true.",
                jiuwenError.getMessage()
        );

        config.getJiuwen().setEnabled(false);
        config.getInference().setPort(70000);
        IllegalArgumentException portRangeError = assertThrows(
                IllegalArgumentException.class,
                config::syncAndValidateLaunch
        );
        assertEquals("inference.port must be between 1 and 65535", portRangeError.getMessage());

        config.getInference().setPort(18000);
        config.getTrajectory().setBatch_size(0);
        IllegalArgumentException batchSizeError = assertThrows(
                IllegalArgumentException.class,
                config::syncAndValidateLaunch
        );
        assertEquals("trajectory.batch_size must be >= 1", batchSizeError.getMessage());
    }

    @Test
    void onlineConfigSerializesWithPythonFieldNames() throws Exception {
        OnlineRLConfig config = new OnlineRLConfig();
        config.getInference().setPort(18000);
        config.getJudge().setPort(18001);
        config.getGateway().setPort(18080);
        config.getGateway().setRedis_url("redis://127.0.0.1:6379/0");
        config.getJiuwen().setAgent_server_port(18092);
        config.getJiuwen().setWs_port(19000);
        config.getJiuwen().setWeb_port(5173);
        config.syncAndValidateLaunch();

        Map<String, Object> encoded = OBJECT_MAPPER.readValue(
                OBJECT_MAPPER.writeValueAsBytes(config),
                new TypeReference<>() {
                }
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> inference = (Map<String, Object>) encoded.get("inference");
        @SuppressWarnings("unchecked")
        Map<String, Object> judge = (Map<String, Object>) encoded.get("judge");
        @SuppressWarnings("unchecked")
        Map<String, Object> gateway = (Map<String, Object>) encoded.get("gateway");
        @SuppressWarnings("unchecked")
        Map<String, Object> training = (Map<String, Object>) encoded.get("training");
        @SuppressWarnings("unchecked")
        Map<String, Object> jiuwen = (Map<String, Object>) encoded.get("jiuwen");

        assertEquals(18000, inference.get("port"));
        assertTrue(judge.containsKey("model_name"));
        assertEquals("Qwen3-4B-Thinking-2507", judge.get("model_name"));
        assertEquals(Boolean.TRUE, judge.get("reuse_inference_if_same_model"));
        assertEquals("redis://127.0.0.1:6379/0", gateway.get("redis_url"));
        assertEquals("4,5", training.get("gpu_ids"));
        assertEquals(18092, jiuwen.get("agent_server_port"));
    }

    @Test
    void onlinePpoOverlayMatchesPythonBaselineShape() {
        assertTrue(OnlineConfigDefaults.BUILTIN_ONLINE_RL_CONFIG.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) OnlineConfigDefaults.ONLINE_PPO_VERL_HYDRA_OVERLAY.get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> actorRolloutRef = (Map<String, Object>) OnlineConfigDefaults.ONLINE_PPO_VERL_HYDRA_OVERLAY.get("actor_rollout_ref");
        @SuppressWarnings("unchecked")
        Map<String, Object> trainer = (Map<String, Object>) OnlineConfigDefaults.ONLINE_PPO_VERL_HYDRA_OVERLAY.get("trainer");
        @SuppressWarnings("unchecked")
        Map<String, Object> jiuwenRl = (Map<String, Object>) OnlineConfigDefaults.ONLINE_PPO_VERL_HYDRA_OVERLAY.get("JiuwenRL");

        assertEquals("/dev/null", data.get("train_files"));
        assertEquals(8, data.get("train_batch_size"));
        assertTrue(actorRolloutRef.containsKey("actor"));
        assertTrue(actorRolloutRef.containsKey("rollout"));
        assertEquals(List.of("console"), trainer.get("logger"));
        assertEquals("online-ppo", trainer.get("experiment_name"));
        assertEquals(Boolean.FALSE, jiuwenRl.get("whole_trajectory"));
        assertTrue(jiuwenRl.containsKey("final_keep_per_prompt"));
    }
}
