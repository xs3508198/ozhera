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
package org.apache.ozhera.log.manager.controller;

import com.google.gson.Gson;
import com.xiaomi.youpin.docean.anno.Controller;
import com.xiaomi.youpin.docean.anno.RequestMapping;
import com.xiaomi.youpin.docean.anno.RequestParam;
import com.xiaomi.youpin.docean.mvc.MvcContext;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import org.apache.ozhera.log.common.Result;
import org.apache.ozhera.log.manager.model.bo.BotQAParam;
import org.apache.ozhera.log.manager.model.dto.AiAnalysisHistoryDTO;
import org.apache.ozhera.log.manager.model.dto.LogAiAnalysisDTO;
import org.apache.ozhera.log.manager.model.vo.LogAiAnalysisResponse;
import org.apache.ozhera.log.manager.service.MilogAiAnalysisService;

import javax.annotation.Resource;
import java.util.List;

@Controller
public class MilogAiController {

    @Resource
    private MilogAiAnalysisService milogAiAnalysisService;

    private static final Gson gson = new Gson();

    @RequestMapping(path = "/milog/tail/aiAnalysis", method = "post")
    public Result<LogAiAnalysisResponse> aiAnalysis(LogAiAnalysisDTO tailLogAiAnalysisDTO) {
        return milogAiAnalysisService.tailLogAiAnalysis(tailLogAiAnalysisDTO);
    }

    /**
     * SSE streaming endpoint for AI analysis.
     * Uses Netty directly to bypass docean's response mechanism for chunked transfer.
     * Returns null to prevent docean from writing its own response.
     */
    @RequestMapping(path = "/milog/tail/aiAnalysis/stream", method = "post")
    public Object streamAiAnalysis(MvcContext context, LogAiAnalysisDTO dto) {
        ChannelHandlerContext ctx = context.getHandlerContext();

        // Send SSE response headers
        DefaultHttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/event-stream");
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "no-cache");
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        response.headers().set("X-Accel-Buffering", "no");
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        ctx.write(response);

        milogAiAnalysisService.streamAiAnalysis(dto,
                // onToken - send each token chunk as SSE data event
                token -> {
                    if (ctx.channel().isActive()) {
                        String sseData = "data: " + escapeSSE(token) + "\n\n";
                        ctx.writeAndFlush(new DefaultHttpContent(
                                Unpooled.copiedBuffer(sseData, CharsetUtil.UTF_8)));
                    }
                },
                // onComplete - send complete event with conversation info
                resp -> {
                    if (ctx.channel().isActive()) {
                        String completeData = "event: complete\ndata: " + gson.toJson(resp) + "\n\n";
                        ctx.writeAndFlush(new DefaultHttpContent(
                                Unpooled.copiedBuffer(completeData, CharsetUtil.UTF_8)));

                        String doneData = "event: done\ndata: [DONE]\n\n";
                        ctx.writeAndFlush(new DefaultHttpContent(
                                Unpooled.copiedBuffer(doneData, CharsetUtil.UTF_8)));

                        // End the chunked response
                        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    }
                },
                // onError - send error event
                error -> {
                    if (ctx.channel().isActive()) {
                        String errorData = "event: error\ndata: " + escapeSSE(error) + "\n\n";
                        ctx.writeAndFlush(new DefaultHttpContent(
                                Unpooled.copiedBuffer(errorData, CharsetUtil.UTF_8)));
                        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
                    }
                }
        );

        // Return null to prevent docean from writing response
        return null;
    }

    private String escapeSSE(String data) {
        if (data == null) return "";
        return data.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    @RequestMapping(path = "/milog/tail/aiHistoryList", method = "post")
    public Result<List<AiAnalysisHistoryDTO>> getAiHistoryList(@RequestParam(value = "storeId") Long storeId) {
        return milogAiAnalysisService.getAiHistoryList(storeId);
    }

    @RequestMapping(path = "/milog/tail/aiConversation", method = "post")
    public Result<List<BotQAParam.QAParam>> getAiConversation(@RequestParam(value = "id") Long id) {
        return milogAiAnalysisService.getAiConversation(id);
    }

    @RequestMapping(path = "/milog/tail/deleteAiConversation", method = "post")
    public Result<Boolean> deleteAiConversation(@RequestParam(value = "id") Long id) {
        return milogAiAnalysisService.deleteAiConversation(id);
    }

    @RequestMapping(path = "/milog/tail/updateAiName", method = "post")
    public Result<Boolean> updateAiName(@RequestParam(value = "id") Long id, @RequestParam(value = "name") String name) {
        return milogAiAnalysisService.updateAiName(id, name);
    }

    @RequestMapping(path = "/milog/tail/closeAiAnalysis", method = "post")
    public Result<Boolean> closeAiAnalysis(@RequestParam(value = "id") Long id) {
        return milogAiAnalysisService.closeAiAnalysis(id);
    }
}
