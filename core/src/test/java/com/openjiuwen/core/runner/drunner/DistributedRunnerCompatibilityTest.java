
package com.openjiuwen.core.runner.drunner;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.RunnerConfig;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DistributedRunnerCompatibilityTest {
    @AfterEach
    void tearDown() {
        DistributedRunner.shutdown();
        RunnerConfig.setRunnerConfig(RunnerConfig.DEFAULT);
    }

    @Test
    void distributedRunnerShouldStartReplySubscriptionAndExposeTopics() {
        RunnerConfig.setRunnerConfig(RunnerConfig.builder().distributedMode(true).envPrefix("dev")
                .instanceId("instance-1")
                .distributedConfig(DistributedConfig.builder()
                        .messageQueueConfig(MessageQueueConfig.builder().type(MessageQueueType.FAKE.getValue()).build())
                        .requestTimeout(10.0).build())
                .build());

        DistributedRunner.ensureStarted();

        assertThat(DistributedRunner.messageQueue()).isNotNull();
        assertThat(DistributedRunner.replySubscription()).isNotNull();
        assertThat(DistributedRunner.replyTopic()).contains("instance-1");
        assertThat(DistributedRunner.agentTopic("agent-a", "v1")).contains("agent-a");
    }

    @Test
    void distributedRunnerShutdownShouldBeIdempotent() {
        DistributedRunner.ensureStarted();

        DistributedRunner.shutdown();
        DistributedRunner.shutdown();

        assertThat(DistributedRunner.replyTopic()).isNotBlank();
    }
}
