  /*
   * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
   */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.constants.Constant;
import com.openjiuwen.core.session.BaseSession;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.StreamWriter;
import com.openjiuwen.core.session.stream.StreamWriterManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent-level interaction handler.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.session.interaction.interaction.AgentInteraction}.
 */
public class AgentInteraction extends BaseInteraction {

    public AgentInteraction(BaseSession session) {
        super(session);
    }

    @Override
    public Object waitUserInputs(Object value) {
        Object inputs = getNextInteractiveInput();
        if (inputs != null) {
            return inputs;
        }

        // Checkpoint interrupt - requires checkpointer implementation
        Object checkpointer = session.checkpointer();
        if (checkpointer instanceof Checkpointer typedCheckpointer) {
            typedCheckpointer.interruptAgentExecute(session);
        }

        InteractionOutput payload = new InteractionOutput(session.sessionId(), value);
        StreamWriterManager writerManager = session.streamWriterManager();
        if (writerManager != null) {
            StreamWriter<OutputSchema> outputWriter = writerManager.getOutputWriter();
            if (outputWriter != null) {
                Map<String, Object> data = new HashMap<>();
                data.put("type", Constant.INTERACTION);
                data.put("index", idx);
                data.put("payload", payload);
                outputWriter.write(data);
            }
        }

        throw new AgentInterrupt();
    }
}
