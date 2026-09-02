
package com.openjiuwen.agentevolving.agent_rl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.AdaConfig;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.AgentRuntimeConfig;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.PersistenceConfig;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.RLConfig;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.RolloutConfig;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.TrainingConfig;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.VerlActorRolloutRefHydraOverlay;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.VerlDataHydraOverlay;
import com.openjiuwen.agentevolving.agent_rl.config.offlineconfig.VerlHydraOverlay;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class OfflineConfigSliceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void trainingConfigDefaultsMatchPythonBaseline() {
        TrainingConfig config = new TrainingConfig();

        assertEquals("OpenJiuwenAgentRL", config.getProject_name());
        assertEquals("grpo_experiment", config.getExperiment_name());
        assertEquals("grpo", config.getAlgorithm_adv_estimator());
        assertTrue(config.isAlgorithm_norm_adv_by_std_in_grpo());
        assertEquals(2, config.getEpoch_num());
        assertEquals(32, config.getTrain_batch_size());
        assertEquals(40, config.getRollout_concurrency());
        assertEquals("0,1,2,3", config.getVisible_device());
        assertEquals(3072, config.getMax_prompt_length());
        assertEquals(3072, config.getMax_response_length());
        assertEquals("truncate", config.getTruncation());
        assertTrue(config.isVal_before_train());
        assertEquals(List.of("tensorboard"), config.getLogger());
        assertTrue(config.isLog_rollout_details());
        assertFalse(config.isLog_reward_distribution());
        assertTrue(config.getVerl_extra().isEmpty());
        assertNull(config.getVerl_config_path());
    }

    @Test
    void trainingConfigResolvedAliasesPreferLegacyFields() {
        TrainingConfig config = new TrainingConfig();
        config.setTrain_data_path("/data/train.jsonl");
        config.setVal_data_path("/data/val.jsonl");

        assertEquals("/data/train.jsonl", config.resolvedTrainFiles());
        assertEquals("/data/val.jsonl", config.resolvedValFiles());

        config.setTrain_files("/legacy/train.jsonl");
        config.setVal_files("/legacy/val.jsonl");

        assertEquals("/legacy/train.jsonl", config.resolvedTrainFiles());
        assertEquals("/legacy/val.jsonl", config.resolvedValFiles());
    }

    @Test
    void rlConfigRequiresTrainingAndBuildsDefaultNestedConfigs() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> new RLConfig(null));
        assertEquals("training is required", exception.getMessage());

        RLConfig config = new RLConfig(new TrainingConfig());
        assertInstanceOf(RolloutConfig.class, config.getRollout());
        assertInstanceOf(AgentRuntimeConfig.class, config.getRuntime());
        assertInstanceOf(PersistenceConfig.class, config.getPersistence());
        assertNull(config.getAda());
    }

    @Test
    void nestedConfigDefaultsMatchPythonBaseline() {
        PersistenceConfig persistence = new PersistenceConfig();
        assertFalse(persistence.isEnabled());
        assertEquals(100, persistence.getFlush_interval());
        assertTrue(persistence.isSave_rollouts());
        assertTrue(persistence.isSave_step_summaries());

        RolloutConfig rollout = new RolloutConfig();
        assertEquals(1e-6, rollout.getActor_optimizer_lr());
        assertFalse(rollout.isActor_use_kl_loss());
        assertEquals(0.02, rollout.getActor_kl_loss_coef());
        assertEquals(0.2, rollout.getActor_clip_ratio_low());
        assertEquals(0.3, rollout.getActor_clip_ratio_high());
        assertEquals("seq-mean-token-mean", rollout.getActor_loss_agg_mode());
        assertEquals(8, rollout.getRollout_n());

        AgentRuntimeConfig runtime = new AgentRuntimeConfig();
        assertEquals("You are a helpful assistant.", runtime.getSystem_prompt());
        assertEquals(0.7, runtime.getTemperature());
        assertEquals(0.9, runtime.getTop_p());
        assertEquals(512, runtime.getMax_new_tokens());

        AdaConfig ada = new AdaConfig();
        assertEquals(2, ada.getRollout_max_round());
        assertEquals(8, ada.getFinal_keep_per_prompt());
    }

    @Test
    void offlineConfigSerializesWithPythonFieldNames() throws Exception {
        RLConfig config = new RLConfig(new TrainingConfig());
        config.getTraining().setTrain_files("/tmp/train.jsonl");
        config.getTraining().setVal_files("/tmp/val.jsonl");

        Map<String, Object> encoded =
            OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsBytes(config), new TypeReference<>() {
            });

        @SuppressWarnings("unchecked")
        Map<String, Object> training = (Map<String, Object>) encoded.get("training");
        assertEquals("/tmp/train.jsonl", training.get("train_files"));
        assertEquals("/tmp/val.jsonl", training.get("val_files"));
        assertEquals("OpenJiuwenAgentRL", training.get("project_name"));
        assertTrue(encoded.containsKey("rollout"));
        assertTrue(encoded.containsKey("runtime"));
        assertTrue(encoded.containsKey("persistence"));
    }

    @Test
    void verlHydraOverlayDefaultsMatchPythonBaseline() {
        VerlHydraOverlay overlay = new VerlHydraOverlay();

        VerlDataHydraOverlay data = overlay.getData();
        assertFalse(data.isFilter_overlong_prompts());
        assertFalse(overlay.getAlgorithm().isFilter_groups());

        VerlActorRolloutRefHydraOverlay actorRolloutRef = overlay.getActor_rollout_ref();
        assertFalse(actorRolloutRef.getModel().isUse_remove_padding());
        assertTrue(actorRolloutRef.getModel().isEnable_gradient_checkpointing());
        assertEquals(16, actorRolloutRef.getActor().getPpo_mini_batch_size());
        assertTrue(actorRolloutRef.getActor().getFsdp_config().isParam_offload());
        assertTrue(actorRolloutRef.getActor().getFsdp_config().isOptimizer_offload());
        assertTrue(actorRolloutRef.getRef().getFsdp_config().isParam_offload());
        assertEquals("async", actorRolloutRef.getRollout().getMode());
        assertEquals("vllm", actorRolloutRef.getRollout().getName());
        assertEquals(0.7, actorRolloutRef.getRollout().getGpu_memory_utilization());
        assertFalse(actorRolloutRef.getRollout().isEnable_chunked_prefill());
        assertEquals("hermes", actorRolloutRef.getRollout().getMulti_turn().getFormat());
        assertTrue(actorRolloutRef.getRollout().getEngine_kwargs().getVllm().isEnable_auto_tool_choice());
        assertEquals("hermes", actorRolloutRef.getRollout().getEngine_kwargs().getVllm().getTool_call_parser());
        assertEquals("agentrl", actorRolloutRef.getRollout().getEngine_kwargs().getVllm().getServed_model_name());
        assertEquals("npu", overlay.getTrainer().getDevice());
        assertNull(overlay.getTrainer().getRuntime_parallel_num());
        assertNull(overlay.getTrainer().getRollout_max_round());
        assertEquals("naive", overlay.getReward_model().getReward_manager());
        assertFalse(overlay.getJiuwenRL().isWhole_trajectory());
        assertNull(overlay.getJiuwenRL().getFinal_keep_per_prompt());
        assertEquals("default_classify_rollouts", overlay.getJiuwenRL().getCustom_fn().getClassifier());
        assertEquals("default_validate_stop", overlay.getJiuwenRL().getCustom_fn().getValidator());
        assertEquals("default_sampling", overlay.getJiuwenRL().getCustom_fn().getSampler());
    }

    @Test
    void verlHydraOverlaySerializesNestedPythonFieldNames() throws Exception {
        VerlHydraOverlay overlay = new VerlHydraOverlay();

        Map<String, Object> encoded =
            OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsBytes(overlay), new TypeReference<>() {
            });

        @SuppressWarnings("unchecked")
        Map<String, Object> actorRolloutRef = (Map<String, Object>) encoded.get("actor_rollout_ref");
        @SuppressWarnings("unchecked")
        Map<String, Object> rollout = (Map<String, Object>) actorRolloutRef.get("rollout");
        @SuppressWarnings("unchecked")
        Map<String, Object> multiTurn = (Map<String, Object>) rollout.get("multi_turn");
        @SuppressWarnings("unchecked")
        Map<String, Object> engineKwargs = (Map<String, Object>) rollout.get("engine_kwargs");
        @SuppressWarnings("unchecked")
        Map<String, Object> vllm = (Map<String, Object>) engineKwargs.get("vllm");
        @SuppressWarnings("unchecked")
        Map<String, Object> jiuwenRl = (Map<String, Object>) encoded.get("JiuwenRL");
        @SuppressWarnings("unchecked")
        Map<String, Object> customFn = (Map<String, Object>) jiuwenRl.get("custom_fn");

        assertTrue(encoded.containsKey("data"));
        assertTrue(encoded.containsKey("algorithm"));
        assertEquals(Boolean.FALSE, ((Map<?, ?>) encoded.get("data")).get("filter_overlong_prompts"));
        assertTrue(actorRolloutRef.containsKey("model"));
        assertTrue(actorRolloutRef.containsKey("actor"));
        assertTrue(actorRolloutRef.containsKey("ref"));
        assertEquals("async", rollout.get("mode"));
        assertEquals("hermes", multiTurn.get("format"));
        assertEquals(Boolean.TRUE, vllm.get("enable_auto_tool_choice"));
        assertEquals("agentrl", vllm.get("served_model_name"));
        assertTrue(encoded.containsKey("reward_model"));
        assertEquals("default_validate_stop", customFn.get("validator"));
    }
}
