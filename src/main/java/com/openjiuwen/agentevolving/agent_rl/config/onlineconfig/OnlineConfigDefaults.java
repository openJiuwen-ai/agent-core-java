/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.agent_rl.config.onlineconfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors module-level defaults from Python's onlineconfig.py.
 */
public final class OnlineConfigDefaults {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Map<String, Object> BUILTIN_ONLINE_RL_CONFIG =
            Collections.unmodifiableMap(new LinkedHashMap<>());

    /**
     * Auto-generated for codecheck compliance.
     */
    public static final Map<String, Object> ONLINE_PPO_VERL_HYDRA_OVERLAY =
            freezeMap(createOnlinePpoVerlHydraOverlay());

    private OnlineConfigDefaults() {
    }

    private static Map<String, Object> createOnlinePpoVerlHydraOverlay() {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("train_files", "/dev/null");
        data.put("val_files", "/dev/null");
        data.put("train_batch_size", 8);
        data.put("max_prompt_length", 2048);
        data.put("max_response_length", 2048);
        data.put("truncation", "truncate");
        data.put("filter_overlong_prompts", false);
        root.put("data", data);

        LinkedHashMap<String, Object> algorithm = new LinkedHashMap<>();
        algorithm.put("adv_estimator", "reinforce_plus_plus");
        algorithm.put("gamma", 1.0);
        algorithm.put("lam", 1.0);
        algorithm.put("use_kl_in_reward", true);
        algorithm.put("kl_penalty", "kl");
        LinkedHashMap<String, Object> klCtrl = new LinkedHashMap<>();
        klCtrl.put("type", "fixed");
        klCtrl.put("kl_coef", 0.001);
        algorithm.put("kl_ctrl", klCtrl);
        algorithm.put("filter_groups", false);
        root.put("algorithm", algorithm);

        LinkedHashMap<String, Object> actorRolloutRef = new LinkedHashMap<>();
        actorRolloutRef.put("hybrid_engine", true);

        LinkedHashMap<String, Object> model = new LinkedHashMap<>();
        model.put("use_remove_padding", true);
        model.put("enable_gradient_checkpointing", true);
        model.put("lora_rank", 16);
        model.put("lora_alpha", 32);
        model.put("target_modules", "all-linear");
        actorRolloutRef.put("model", model);

        LinkedHashMap<String, Object> actor = new LinkedHashMap<>();
        actor.put("strategy", "fsdp");
        actor.put("ppo_mini_batch_size", 4);
        actor.put("ppo_micro_batch_size_per_gpu", 2);
        actor.put("ppo_epochs", 1);
        actor.put("use_kl_loss", false);
        actor.put("kl_loss_coef", 0.02);
        actor.put("entropy_coeff", 0.01);
        actor.put("clip_ratio", 0.2);
        actor.put("clip_ratio_low", 0.2);
        actor.put("clip_ratio_high", 0.28);
        actor.put("loss_agg_mode", "token-mean");
        LinkedHashMap<String, Object> actorFsdpConfig = new LinkedHashMap<>();
        actorFsdpConfig.put("param_offload", true);
        actorFsdpConfig.put("optimizer_offload", true);
        actor.put("fsdp_config", actorFsdpConfig);
        LinkedHashMap<String, Object> actorOptim = new LinkedHashMap<>();
        actorOptim.put("lr", 1e-5);
        actorOptim.put("lr_scheduler_type", "constant");
        actor.put("optim", actorOptim);
        actorRolloutRef.put("actor", actor);

        LinkedHashMap<String, Object> ref = new LinkedHashMap<>();
        LinkedHashMap<String, Object> refFsdpConfig = new LinkedHashMap<>();
        refFsdpConfig.put("param_offload", true);
        ref.put("fsdp_config", refFsdpConfig);
        ref.put("log_prob_micro_batch_size_per_gpu", 2);
        actorRolloutRef.put("ref", ref);

        LinkedHashMap<String, Object> rollout = new LinkedHashMap<>();
        rollout.put("mode", "async");
        rollout.put("name", "vllm");
        rollout.put("tensor_model_parallel_size", 1);
        rollout.put("enforce_eager", true);
        rollout.put("gpu_memory_utilization", 0.05);
        rollout.put("max_model_len", 512);
        rollout.put("max_num_seqs", 1);
        rollout.put("n", 1);
        rollout.put("log_prob_micro_batch_size_per_gpu", 2);
        actorRolloutRef.put("rollout", rollout);
        root.put("actor_rollout_ref", actorRolloutRef);

        LinkedHashMap<String, Object> trainer = new LinkedHashMap<>();
        trainer.put("total_epochs", 1);
        trainer.put("total_training_steps", null);
        trainer.put("nnodes", 1);
        trainer.put("n_gpus_per_node", 2);
        trainer.put("save_freq", -1);
        trainer.put("test_freq", -1);
        trainer.put("val_before_train", false);
        trainer.put("critic_warmup", 0);
        trainer.put("balance_batch", false);
        trainer.put("default_local_dir", "/tmp/online_ppo_ckpt");
        trainer.put("logger", List.of("console"));
        trainer.put("project_name", "agent-online-rl");
        trainer.put("experiment_name", "online-ppo");
        trainer.put("device", "cuda");
        trainer.put("resume_mode", "disable");
        root.put("trainer", trainer);

        LinkedHashMap<String, Object> rewardModel = new LinkedHashMap<>();
        rewardModel.put("reward_manager", "naive");
        root.put("reward_model", rewardModel);

        LinkedHashMap<String, Object> jiuwenRl = new LinkedHashMap<>();
        jiuwenRl.put("whole_trajectory", false);
        jiuwenRl.put("final_keep_per_prompt", null);
        LinkedHashMap<String, Object> customFn = new LinkedHashMap<>();
        customFn.put("classifier", "default_classify_rollouts");
        customFn.put("validator", "default_validate_stop");
        customFn.put("sampler", "default_sampling");
        jiuwenRl.put("custom_fn", customFn);
        root.put("JiuwenRL", jiuwenRl);

        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> freezeMap(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> mapValue) {
                copy.put(entry.getKey(), freezeMap((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                copy.put(entry.getKey(), freezeList(listValue));
            } else {
                copy.put(entry.getKey(), value);
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> freezeList(List<?> source) {
        ArrayList<Object> copy = new ArrayList<>();
        for (Object value : source) {
            if (value instanceof Map<?, ?> mapValue) {
                copy.add(freezeMap((Map<String, Object>) mapValue));
            } else if (value instanceof List<?> listValue) {
                copy.add(freezeList(listValue));
            } else {
                copy.add(value);
            }
        }
        return Collections.unmodifiableList(copy);
    }
}
