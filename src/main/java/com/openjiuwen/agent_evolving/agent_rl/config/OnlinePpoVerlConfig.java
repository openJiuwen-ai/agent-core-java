/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in online RL PPO configuration overlay constants.
 * <p>
 * Mirrors Python's {@code ONLINE_PPO_VERL_HYDRA_OVERLAY} in
 * {@code openjiuwen/agent_evolving/agent_rl/config/online_config.py}.
 */
public final class OnlinePpoVerlConfig {

    /**
     * Built-in online RL config (empty base).
     * <p>
     * Mirrors Python's {@code BUILTIN_ONLINE_RL_CONFIG}.
     */
    public static final Map<String, Object> BUILTIN_ONLINE_RL_CONFIG = new HashMap<>();

    /**
     * Online PPO Verl Hydra overlay configuration.
     * <p>
     * Mirrors Python's {@code ONLINE_PPO_VERL_HYDRA_OVERLAY}.
     */
    public static Map<String, Object> getOnlinePpoVerlHydraOverlay() {
        Map<String, Object> config = new HashMap<>();
        
        // data section
        Map<String, Object> data = new HashMap<>();
        data.put("train_files", "/dev/null");
        data.put("val_files", "/dev/null");
        data.put("train_batch_size", 8);
        data.put("max_prompt_length", 2048);
        data.put("max_response_length", 2048);
        data.put("truncation", "truncate");
        data.put("filter_overlong_prompts", false);
        config.put("data", data);
        
        // algorithm section
        Map<String, Object> algorithm = new HashMap<>();
        algorithm.put("adv_estimator", "reinforce_plus_plus");
        algorithm.put("gamma", 1.0);
        algorithm.put("lam", 1.0);
        algorithm.put("use_kl_in_reward", true);
        algorithm.put("kl_penalty", "kl");
        Map<String, Object> klCtrl = new HashMap<>();
        klCtrl.put("type", "fixed");
        klCtrl.put("kl_coef", 0.001);
        algorithm.put("kl_ctrl", klCtrl);
        algorithm.put("filter_groups", false);
        config.put("algorithm", algorithm);
        
        // actor_rollout_ref section
        Map<String, Object> actorRolloutRef = new HashMap<>();
        actorRolloutRef.put("hybrid_engine", true);
        
        // model sub-section
        Map<String, Object> model = new HashMap<>();
        model.put("use_remove_padding", true);
        model.put("enable_gradient_checkpointing", true);
        model.put("lora_rank", 16);
        model.put("lora_alpha", 32);
        model.put("target_modules", "all-linear");
        actorRolloutRef.put("model", model);
        
        // actor sub-section
        Map<String, Object> actor = new HashMap<>();
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
        Map<String, Object> actorFsdpConfig = new HashMap<>();
        actorFsdpConfig.put("param_offload", true);
        actorFsdpConfig.put("optimizer_offload", true);
        actor.put("fsdp_config", actorFsdpConfig);
        Map<String, Object> optim = new HashMap<>();
        optim.put("lr", 1e-5);
        optim.put("lr_scheduler_type", "constant");
        actor.put("optim", optim);
        actorRolloutRef.put("actor", actor);
        
        // ref sub-section
        Map<String, Object> ref = new HashMap<>();
        Map<String, Object> refFsdpConfig = new HashMap<>();
        refFsdpConfig.put("param_offload", true);
        ref.put("fsdp_config", refFsdpConfig);
        ref.put("log_prob_micro_batch_size_per_gpu", 2);
        actorRolloutRef.put("ref", ref);
        
        // rollout sub-section
        Map<String, Object> rollout = new HashMap<>();
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
        
        config.put("actor_rollout_ref", actorRolloutRef);
        
        // trainer section
        Map<String, Object> trainer = new HashMap<>();
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
        trainer.put("logger", java.util.List.of("console"));
        trainer.put("project_name", "agent-online-rl");
        trainer.put("experiment_name", "online-ppo");
        trainer.put("device", "cuda");
        trainer.put("resume_mode", "disable");
        config.put("trainer", trainer);
        
        // reward_model section
        Map<String, Object> rewardModel = new HashMap<>();
        rewardModel.put("reward_manager", "naive");
        config.put("reward_model", rewardModel);
        
        // JiuwenRL section
        Map<String, Object> jiuwenRL = new HashMap<>();
        jiuwenRL.put("whole_trajectory", false);
        jiuwenRL.put("final_keep_per_prompt", null);
        Map<String, Object> customFn = new HashMap<>();
        customFn.put("classifier", "default_classify_rollouts");
        customFn.put("validator", "default_validate_stop");
        customFn.put("sampler", "default_sampling");
        jiuwenRL.put("custom_fn", customFn);
        config.put("JiuwenRL", jiuwenRL);
        
        return config;
    }

    private OnlinePpoVerlConfig() {
        // Static utility class
    }
}
