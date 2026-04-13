/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ozhera.mind.service.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.hook.SummaryChunkEvent;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * Hook for streaming reasoning chunks to a Sink.
 * Used to bridge ReActAgent's hook-based streaming to Flux-based streaming.
 */
public class StreamingHook implements Hook {

    private final Sinks.Many<String> sink;
    private final StringBuilder fullResponse;

    public StreamingHook(Sinks.Many<String> sink, StringBuilder fullResponse) {
        this.sink = sink;
        this.fullResponse = fullResponse;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ReasoningChunkEvent) {
            ReasoningChunkEvent chunkEvent = (ReasoningChunkEvent) event;
            Msg chunkMsg = chunkEvent.getIncrementalChunk();
            if (chunkMsg != null) {
                String text = chunkMsg.getTextContent();
                if (text != null && !text.isEmpty()) {
                    fullResponse.append(text);
                    sink.tryEmitNext(text);
                }
            }
        } else if (event instanceof SummaryChunkEvent) {
            SummaryChunkEvent chunkEvent = (SummaryChunkEvent) event;
            Msg chunkMsg = chunkEvent.getIncrementalChunk();
            if (chunkMsg != null) {
                String text = chunkMsg.getTextContent();
                if (text != null && !text.isEmpty()) {
                    fullResponse.append(text);
                    sink.tryEmitNext(text);
                }
            }
        }
        return Mono.just(event);
    }

    /**
     * Complete the sink when streaming is done.
     */
    public void complete() {
        sink.tryEmitComplete();
    }

    /**
     * Signal error to the sink.
     */
    public void error(Throwable e) {
        sink.tryEmitError(e);
    }

    /**
     * Get the full response accumulated so far.
     */
    public String getFullResponse() {
        return fullResponse.toString();
    }
}
