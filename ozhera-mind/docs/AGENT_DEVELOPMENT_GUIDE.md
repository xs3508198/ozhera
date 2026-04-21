# OzHera Mind Agent 开发手册

本文档详细介绍 OzHera Mind Agent 系统的架构、核心组件和开发方法。

---

## 目录

1. [系统架构概览](#1-系统架构概览)
2. [核心组件详解](#2-核心组件详解)
3. [Agent 创建流程](#3-agent-创建流程)
4. [Reactor 响应式编程](#4-reactor-响应式编程)
5. [流式输出机制](#5-流式输出机制)
6. [记忆管理系统](#6-记忆管理系统)
7. [配置参考](#7-配置参考)
8. [最佳实践](#8-最佳实践)
9. [Multi-Agent 系统](#9-multi-agent-系统)
10. [高并发控制](#10-高并发控制)

---

## 1. 系统架构概览

### 1.1 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Gateway 层                              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ AgentRouter │  │ WorkerClient│  │ UserConfigService   │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼ HTTP/SSE
┌─────────────────────────────────────────────────────────────┐
│                      Worker 层                               │
│  ┌─────────────────────────────────────────────────────────┐│
│  │                    AgentService                         ││
│  │  ┌──────────┐  ┌──────────────┐  ┌─────────────────┐   ││
│  │  │ReActAgent│  │AutoContext   │  │ StreamingHook   │   ││
│  │  │          │  │Memory        │  │                 │   ││
│  │  └──────────┘  └──────────────┘  └─────────────────┘   ││
│  └─────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐       ┌──────────┐        ┌──────────┐
    │ Caffeine │       │  Redis   │        │  MySQL   │
    │ (JVM缓存) │       │ (状态存储)│        │ (历史记录)│
    └──────────┘       └──────────┘        └──────────┘
```

### 1.2 数据流

```
用户请求 → Gateway → Worker → ReActAgent → LLM
                                   ↓
用户响应 ← Gateway ← Worker ← Hook(流式) / Mono(同步)
```

---

## 2. 核心组件详解

### 2.1 ReActAgent

ReActAgent 是基于 ReAct (Reasoning + Acting) 模式的智能代理，能够进行推理并调用工具完成任务。

```java
ReActAgent agent = ReActAgent.builder()
    .name("MindAgent")                    // Agent 名称
    .sysPrompt("你是一个智能助手...")       // 系统提示词
    .model(model)                         // LLM 模型
    .toolkit(toolkit)                     // 工具集
    .memory(memory)                       // 记忆组件
    .hook(streamingHook)                  // 钩子（用于流式输出）
    .build();
```

**核心属性说明：**

| 属性 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | String | 是 | Agent 的唯一标识名称 |
| sysPrompt | String | 否 | 系统提示词，定义 Agent 的角色和行为 |
| model | Model | 是 | 底层 LLM 模型实例 |
| toolkit | Toolkit | 否 | 注册的工具集合 |
| memory | Memory | 否 | 对话记忆管理器 |
| hook | Hook | 否 | 事件钩子，用于流式输出等场景 |

### 2.2 Model（模型）

Model 是对 LLM API 的抽象封装，支持多种模型提供商。

```java
// 通过 ModelProviderService 创建模型
Model model = modelProviderService.createModel(userConfig);
```

**支持的模型平台：**
- OpenAI (GPT-4, GPT-3.5)
- Anthropic (Claude)
- 阿里云 (通义千问)
- 其他兼容 OpenAI API 的模型

### 2.3 Toolkit（工具集）

Toolkit 管理 Agent 可调用的工具。

```java
Toolkit toolkit = new Toolkit();
toolkit.registerTool(logToolService);  // 注册日志查询工具
toolkit.registerTool(myCustomTool);    // 注册自定义工具
```

**工具定义示例：**

```java
@Service
public class LogToolService {
    
    @Tool(name = "queryLogs", description = "查询日志")
    public String queryLogs(
        @ToolParam(name = "keyword", description = "搜索关键词") String keyword,
        @ToolParam(name = "timeRange", description = "时间范围") String timeRange
    ) {
        // 工具实现
        return "查询结果...";
    }
}
```

### 2.4 AutoContextMemory（自动上下文记忆）

AutoContextMemory 提供智能的上下文管理，自动压缩长对话以适应 token 限制。

```java
AutoContextConfig memoryConfig = AutoContextConfig.builder()
    .msgThreshold(20)           // 消息数量阈值，超过后触发压缩
    .maxToken(128000)           // 最大 token 数
    .tokenRatio(0.8)            // token 使用比例
    .lastKeep(5)                // 保留最近的消息数
    .largePayloadThreshold(5000)// 大负载阈值
    .build();

AutoContextMemory memory = new AutoContextMemory(memoryConfig, model);
```

**配置参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| msgThreshold | 20 | 消息数量达到此值时触发压缩 |
| maxToken | 128000 | 模型支持的最大 token 数 |
| tokenRatio | 0.8 | 实际使用的 token 比例 |
| lastKeep | 5 | 压缩时保留最近的消息数 |
| largePayloadThreshold | 5000 | 单条消息超过此长度视为大负载 |

---

## 3. Agent 创建流程

### 3.1 完整创建流程

```java
private ReActAgent getOrCreateAgent(String username) {
    // 1. 检查缓存
    ReActAgent agent = agentCache.getIfPresent(username);
    if (agent != null) {
        return agent;
    }

    // 2. 获取用户配置
    UserConfig userConfig = userConfigService.getByUsername(username);
    if (userConfig == null) {
        throw new RuntimeException("用户配置不存在");
    }

    // 3. 创建模型
    Model model = modelProviderService.createModel(userConfig);

    // 4. 创建记忆组件
    AutoContextConfig memoryConfig = AutoContextConfig.builder()
        .msgThreshold(msgThreshold)
        .maxToken(maxToken)
        .tokenRatio(tokenRatio)
        .lastKeep(lastKeep)
        .largePayloadThreshold(largePayloadThreshold)
        .build();
    AutoContextMemory memory = new AutoContextMemory(memoryConfig, model);

    // 5. 恢复记忆状态
    boolean restored = memoryStateService.loadState(username, memory);
    if (!restored) {
        // 从 MySQL 加载历史
        List<Msg> history = loadHistoryMessages(username);
        for (Msg msg : history) {
            memory.addMessage(msg);
        }
    }

    // 6. 创建工具集
    Toolkit toolkit = new Toolkit();
    toolkit.registerTool(logToolService);

    // 7. 构建 Agent
    agent = ReActAgent.builder()
        .name("MindAgent")
        .model(model)
        .toolkit(toolkit)
        .memory(memory)
        .build();

    // 8. 缓存
    agentCache.put(username, agent);
    memoryCache.put(username, memory);

    return agent;
}
```

### 3.2 创建流程图

```
┌────────────────────────────────────────────────────────────────┐
│                     Agent 创建流程                              │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│  ┌─────────┐    命中     ┌───────────────┐                    │
│  │检查缓存  │ ─────────→ │ 返回缓存Agent  │                    │
│  └────┬────┘            └───────────────┘                    │
│       │ 未命中                                                 │
│       ▼                                                       │
│  ┌─────────────┐                                              │
│  │获取用户配置  │                                              │
│  └──────┬──────┘                                              │
│         ▼                                                     │
│  ┌─────────────┐                                              │
│  │ 创建 Model  │                                              │
│  └──────┬──────┘                                              │
│         ▼                                                     │
│  ┌─────────────────┐                                          │
│  │创建 Memory 组件  │                                          │
│  └────────┬────────┘                                          │
│           ▼                                                   │
│  ┌─────────────────┐   有状态   ┌────────────────┐            │
│  │检查 Redis 状态   │ ────────→ │ 恢复 Redis 状态 │            │
│  └────────┬────────┘           └────────────────┘            │
│           │ 无状态                                             │
│           ▼                                                   │
│  ┌─────────────────┐                                          │
│  │ 加载 MySQL 历史  │                                          │
│  └────────┬────────┘                                          │
│           ▼                                                   │
│  ┌─────────────────┐                                          │
│  │ 创建 Toolkit    │                                          │
│  └────────┬────────┘                                          │
│           ▼                                                   │
│  ┌─────────────────┐                                          │
│  │ 构建 ReActAgent │                                          │
│  └────────┬────────┘                                          │
│           ▼                                                   │
│  ┌─────────────────┐                                          │
│  │  放入缓存       │                                          │
│  └─────────────────┘                                          │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 4. Reactor 响应式编程

OzHera Mind 使用 Project Reactor 实现响应式编程。以下是核心概念和操作符详解。

### 4.1 Mono 和 Flux

**Mono** - 表示 0 或 1 个元素的异步序列

```java
// 创建 Mono
Mono<String> mono = Mono.just("Hello");
Mono<String> empty = Mono.empty();
Mono<String> error = Mono.error(new RuntimeException("Error"));

// Agent 调用返回 Mono
Mono<Msg> response = agent.call(List.of(userMsg));
```

**Flux** - 表示 0 到 N 个元素的异步序列

```java
// 创建 Flux
Flux<String> flux = Flux.just("A", "B", "C");
Flux<Integer> range = Flux.range(1, 10);

// 流式响应返回 Flux
Flux<String> stream = sink.asFlux();
```

### 4.2 核心操作符详解

#### 4.2.1 doOnSubscribe

**用途：** 在订阅发生时执行操作

```java
agent.call(List.of(userMsg))
    .doOnSubscribe(subscription -> {
        // 订阅时执行，常用于初始化
        log.info("开始处理请求");
        UserContext.set(userInfo);  // 设置用户上下文
    })
    .subscribe();
```

**使用场景：**
- 初始化线程本地变量
- 记录请求开始日志
- 设置监控指标起始时间

#### 4.2.2 doOnSuccess

**用途：** 在成功完成时执行操作（仅适用于 Mono）

```java
agent.call(List.of(userMsg))
    .doOnSuccess(msg -> {
        // 成功时执行
        log.info("请求成功完成");
        streamingHook.complete();
        
        // 保存聊天记录
        chatMessageService.saveMessage(username, "USER", userMessage);
        chatMessageService.saveMessage(username, "ASSISTANT", msg.getTextContent());
        
        // 保存记忆状态
        saveMemoryState(username);
    })
    .subscribe();
```

**使用场景：**
- 完成后保存数据
- 发送完成通知
- 清理临时资源

#### 4.2.3 doOnError

**用途：** 在发生错误时执行操作

```java
agent.call(List.of(userMsg))
    .doOnError(e -> {
        // 发生错误时执行
        log.error("请求处理失败", e);
        streamingHook.error(e);
        
        // 记录错误指标
        metrics.incrementErrorCount();
    })
    .subscribe();
```

**使用场景：**
- 错误日志记录
- 发送错误通知
- 更新错误指标

#### 4.2.4 doFinally

**用途：** 无论成功、失败还是取消，都会执行

```java
agent.call(List.of(userMsg))
    .doFinally(signalType -> {
        // 始终执行，signalType 可以是 ON_COMPLETE, ON_ERROR, ON_CANCEL
        UserContext.clear();  // 清理线程本地变量
        
        log.info("请求处理结束，信号类型: {}", signalType);
    })
    .subscribe();
```

**SignalType 枚举值：**
- `ON_COMPLETE` - 正常完成
- `ON_ERROR` - 发生错误
- `ON_CANCEL` - 被取消

**使用场景：**
- 清理线程本地变量
- 关闭资源
- 记录最终状态

### 4.3 其他常用操作符

#### 转换操作符

```java
// map - 同步转换
Mono<String> result = mono.map(msg -> msg.toUpperCase());

// flatMap - 异步转换
Mono<String> result = mono.flatMap(msg -> 
    Mono.fromCallable(() -> processAsync(msg))
);

// filter - 过滤
Flux<String> filtered = flux.filter(s -> s.length() > 3);
```

#### 组合操作符

```java
// zip - 合并多个 Mono
Mono<Tuple2<A, B>> combined = Mono.zip(monoA, monoB);

// concat - 顺序连接
Flux<String> all = Flux.concat(flux1, flux2);

// merge - 并行合并
Flux<String> merged = Flux.merge(flux1, flux2);
```

#### 错误处理操作符

```java
// onErrorReturn - 返回默认值
Mono<String> safe = mono.onErrorReturn("默认值");

// onErrorResume - 切换到备用流
Mono<String> withFallback = mono.onErrorResume(e -> 
    Mono.just("备用结果")
);

// retry - 重试
Mono<String> withRetry = mono.retry(3);
```

#### 时间操作符

```java
// timeout - 超时
Mono<String> withTimeout = mono.timeout(Duration.ofSeconds(30));

// delay - 延迟发射
Mono<String> delayed = mono.delayElement(Duration.ofSeconds(1));

// interval - 定时发射
Flux<Long> ticker = Flux.interval(Duration.ofSeconds(1));
```

### 4.4 操作符对比表

| 操作符 | 触发时机 | 是否中断流 | 常见用途 |
|--------|----------|------------|----------|
| doOnSubscribe | 订阅时 | 否 | 初始化、日志 |
| doOnNext | 每个元素 | 否 | 处理、日志 |
| doOnSuccess | 成功完成(Mono) | 否 | 保存、通知 |
| doOnComplete | 完成(Flux) | 否 | 清理、通知 |
| doOnError | 错误时 | 否 | 日志、告警 |
| doFinally | 任何结束 | 否 | 清理资源 |
| map | 每个元素 | 否 | 同步转换 |
| flatMap | 每个元素 | 否 | 异步转换 |
| filter | 每个元素 | 可能 | 过滤 |
| onErrorReturn | 错误时 | 是 | 默认值 |
| onErrorResume | 错误时 | 是 | 降级 |
| timeout | 超时时 | 是 | 超时控制 |

---

## 5. 流式输出机制

### 5.1 Sinks 详解

**Sinks** 是 Reactor 中的信号发射器，用于手动推送数据到 Flux 或 Mono。

#### Sinks 类型

```java
// 单播 Sink - 只支持一个订阅者
Sinks.Many<String> unicast = Sinks.many().unicast().onBackpressureBuffer();

// 多播 Sink - 支持多个订阅者
Sinks.Many<String> multicast = Sinks.many().multicast().onBackpressureBuffer();

// 重放 Sink - 新订阅者可以收到历史数据
Sinks.Many<String> replay = Sinks.many().replay().all();
```

#### Sinks 核心方法

```java
Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

// 发射数据
sink.tryEmitNext("数据1");        // 尝试发射，失败返回结果
sink.emitNext("数据2", failureHandler);  // 发射并处理失败

// 完成信号
sink.tryEmitComplete();           // 标记流完成

// 错误信号
sink.tryEmitError(new Exception("错误"));  // 发射错误

// 转换为 Flux
Flux<String> flux = sink.asFlux();
```

#### EmitResult 处理

```java
Sinks.EmitResult result = sink.tryEmitNext("data");

switch (result) {
    case OK:
        // 发射成功
        break;
    case FAIL_OVERFLOW:
        // 缓冲区满
        break;
    case FAIL_CANCELLED:
        // 已取消
        break;
    case FAIL_TERMINATED:
        // 已终止
        break;
    case FAIL_NON_SERIALIZED:
        // 非序列化访问
        break;
}
```

### 5.2 StreamingHook 详解

StreamingHook 是连接 ReActAgent Hook 机制和 Reactor Sinks 的桥梁。

#### 完整实现

```java
public class StreamingHook implements Hook {

    private final Sinks.Many<String> sink;
    private final StringBuilder fullResponse;

    public StreamingHook(Sinks.Many<String> sink, StringBuilder fullResponse) {
        this.sink = sink;
        this.fullResponse = fullResponse;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        // 处理推理过程的流式输出
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
        }
        // 处理总结的流式输出
        else if (event instanceof SummaryChunkEvent) {
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

    public void complete() {
        sink.tryEmitComplete();
    }

    public void error(Throwable e) {
        sink.tryEmitError(e);
    }
}
```

#### Hook 事件类型

| 事件类型 | 说明 | 获取内容方法 |
|----------|------|--------------|
| ReasoningChunkEvent | 推理过程的增量输出 | getIncrementalChunk() |
| SummaryChunkEvent | 总结的增量输出 | getIncrementalChunk() |
| ToolCallEvent | 工具调用事件 | getToolCall() |
| ToolResultEvent | 工具结果事件 | getToolResult() |

### 5.3 流式输出完整流程

```java
public Flux<String> chatStream(ChatRequest request) {
    String username = request.getUsername();
    String userMessage = request.getMessage();

    // 1. 创建 Sink 和 Hook
    Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
    StringBuilder fullResponse = new StringBuilder();
    StreamingHook streamingHook = new StreamingHook(sink, fullResponse);

    // 2. 捕获用户上下文
    UserContext.UserInfo userInfo = new UserContext.UserInfo(username, 0);

    try {
        // 3. 创建带 Hook 的 Agent
        ReActAgent agent = createStreamingAgent(username, streamingHook);

        // 4. 构建用户消息
        Msg userMsg = Msg.builder()
            .name("user")
            .role(MsgRole.USER)
            .content(TextBlock.builder().text(userMessage).build())
            .build();

        // 5. 异步执行 Agent
        agent.call(List.of(userMsg))
            .doOnSubscribe(subscription -> {
                UserContext.set(userInfo);
            })
            .doOnSuccess(msg -> {
                streamingHook.complete();
                // 保存记录
                chatMessageService.saveMessage(username, "USER", userMessage);
                chatMessageService.saveMessage(username, "ASSISTANT", fullResponse.toString());
                saveMemoryState(username);
            })
            .doOnError(e -> {
                log.error("Stream chat failed", e);
                streamingHook.error(e);
            })
            .doFinally(signalType -> {
                UserContext.clear();
            })
            .subscribe();  // 触发执行

        // 6. 返回 Flux
        return sink.asFlux();
    } catch (Exception e) {
        return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
    }
}
```

### 5.4 流式输出数据流图

```
┌─────────────────────────────────────────────────────────────────┐
│                     流式输出数据流                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────┐     ┌───────────┐     ┌──────────────┐           │
│  │ReActAgent│────→│ LLM API   │────→│ Stream响应   │           │
│  └──────────┘     └───────────┘     └──────┬───────┘           │
│                                            │                    │
│                                            ▼                    │
│                                   ┌────────────────┐            │
│                                   │ HookEvent      │            │
│                                   │ (Chunk数据)    │            │
│                                   └───────┬────────┘            │
│                                           │                     │
│                                           ▼                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    StreamingHook                         │   │
│  │  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐  │   │
│  │  │ onEvent()   │───→│ 提取文本    │───→│ tryEmitNext │  │   │
│  │  └─────────────┘    └─────────────┘    └─────────────┘  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                           │                     │
│                                           ▼                     │
│                                   ┌────────────────┐            │
│                                   │  Sinks.Many    │            │
│                                   │  (信号发射器)   │            │
│                                   └───────┬────────┘            │
│                                           │ asFlux()            │
│                                           ▼                     │
│                                   ┌────────────────┐            │
│                                   │     Flux       │            │
│                                   │  (响应式流)    │            │
│                                   └───────┬────────┘            │
│                                           │                     │
│                                           ▼                     │
│                                   ┌────────────────┐            │
│                                   │  SSE 响应      │            │
│                                   │  (发送给客户端) │            │
│                                   └────────────────┘            │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 6. 记忆管理系统

### 6.1 三层存储架构

```
┌─────────────────────────────────────────────────────────────┐
│                    记忆存储架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│   ┌─────────────────────────────────────────────────────┐  │
│   │            Caffeine Cache (JVM 内存)                 │  │
│   │  ┌─────────────────┐  ┌─────────────────────────┐   │  │
│   │  │   agentCache    │  │     memoryCache         │   │  │
│   │  │  (ReActAgent)   │  │  (AutoContextMemory)    │   │  │
│   │  └─────────────────┘  └─────────────────────────┘   │  │
│   │  特点: 最快访问, 15分钟过期, 最大500个                │  │
│   └─────────────────────────────────────────────────────┘  │
│                           │ 驱逐时保存                      │
│                           ▼                                │
│   ┌─────────────────────────────────────────────────────┐  │
│   │              Redis (分布式缓存)                       │  │
│   │  ┌─────────────────────────────────────────────┐    │  │
│   │  │          JedisSession                        │    │  │
│   │  │  存储: 压缩后的记忆状态 (summaryCount等)      │    │  │
│   │  │  Key: hera:mind:memory:mind_agent:{username} │    │  │
│   │  └─────────────────────────────────────────────┘    │  │
│   │  特点: 跨实例共享, 快速恢复                          │  │
│   └─────────────────────────────────────────────────────┘  │
│                           │ 无状态时回退                    │
│                           ▼                                │
│   ┌─────────────────────────────────────────────────────┐  │
│   │               MySQL (持久化存储)                      │  │
│   │  ┌─────────────────────────────────────────────┐    │  │
│   │  │         chat_message 表                      │    │  │
│   │  │  存储: 原始对话记录 (username, role, content)│    │  │
│   │  └─────────────────────────────────────────────┘    │  │
│   │  特点: 永久保存, 完整历史, 用于展示                  │  │
│   └─────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 记忆恢复流程

```java
// 1. 尝试从 Redis 恢复
boolean restored = memoryStateService.loadState(username, memory);

if (!restored) {
    // 2. Redis 无状态，从 MySQL 加载历史
    List<Msg> historyMessages = loadHistoryMessages(username);
    for (Msg msg : historyMessages) {
        memory.addMessage(msg);
    }
}
```

### 6.3 记忆保存流程

```java
// 每次对话后保存
private void saveMemoryState(String username) {
    AutoContextMemory memory = memoryCache.getIfPresent(username);
    if (memory != null) {
        memoryStateService.saveState(username, memory);
    }
}

// 缓存驱逐时也保存
memoryCache = Caffeine.newBuilder()
    .removalListener((username, memory, cause) -> {
        if (cause.wasEvicted() && memory != null) {
            memoryStateService.saveState(username, memory);
        }
    })
    .build();
```

### 6.4 MemoryStateService 实现

```java
@Service
public class MemoryStateServiceImpl implements MemoryStateService {

    private static final String SESSION_KEY_PREFIX = "mind_agent";
    private static final String REDIS_KEY_PREFIX = "hera:mind:memory:";

    private Session session;  // agentscope 的 JedisSession

    @PostConstruct
    public void init() {
        this.session = JedisSession.builder()
            .jedisPool(redisService.getJedisPool())
            .keyPrefix(REDIS_KEY_PREFIX)
            .build();
    }

    @Override
    public void saveState(String username, AutoContextMemory memory) {
        SimpleSessionKey key = SimpleSessionKey.of(SESSION_KEY_PREFIX + ":" + username);
        memory.saveTo(session, key);  // 使用内置方法
    }

    @Override
    public boolean loadState(String username, AutoContextMemory memory) {
        SimpleSessionKey key = SimpleSessionKey.of(SESSION_KEY_PREFIX + ":" + username);
        memory.loadFrom(session, key);  // 使用内置方法
        return true;
    }
}
```

---

## 7. 配置参考

### 7.1 Nacos 配置项

```yaml
# Agent 缓存配置
agent.cache.expire-minutes: 15       # 缓存过期时间(分钟)
agent.cache.max-size: 500            # 最大缓存数量

# 记忆配置
agent.memory.msg-threshold: 20       # 触发压缩的消息数阈值
agent.memory.max-token: 128000       # 最大 token 数
agent.memory.token-ratio: 0.8        # token 使用比例
agent.memory.last-keep: 5            # 保留最近消息数
agent.memory.large-payload-threshold: 5000  # 大负载阈值

# 历史加载配置
agent.history.load-size: 50          # 从 MySQL 加载的历史条数

# Redis 配置
spring.redis.host: localhost
spring.redis.port: 6379
spring.redis.password: 
spring.redis.database: 0
spring.redis.cluster.nodes:          # 集群模式配置

# MySQL 配置
spring.datasource.url: jdbc:mysql://localhost:3306/hera_mind
spring.datasource.username: root
spring.datasource.password: 

# Worker 服务配置
spring.cloud.nacos.discovery.server-addr: localhost:8848
spring.cloud.nacos.discovery.namespace: 
spring.cloud.nacos.discovery.group: DEFAULT_GROUP
```

### 7.2 配置优先级

```
application.properties < application-{profile}.properties < Nacos 配置
```

---

## 8. 最佳实践

### 8.1 错误处理

```java
agent.call(List.of(userMsg))
    .timeout(Duration.ofMinutes(2))  // 设置超时
    .onErrorResume(e -> {
        if (e instanceof TimeoutException) {
            return Mono.just(createTimeoutResponse());
        }
        return Mono.error(e);  // 其他错误继续传播
    })
    .doOnError(e -> {
        log.error("Agent call failed", e);
        metrics.recordError(e);
    })
    .subscribe();
```

### 8.2 资源清理

```java
// 使用 doFinally 确保清理
.doFinally(signal -> {
    UserContext.clear();
    MDC.clear();
    // 其他清理操作
})
```

### 8.3 监控指标

```java
// 在关键节点添加指标
.doOnSubscribe(s -> metrics.incrementActiveRequests())
.doOnSuccess(msg -> metrics.recordLatency(startTime))
.doOnError(e -> metrics.incrementErrors())
.doFinally(s -> metrics.decrementActiveRequests())
```

### 8.4 内存管理

```java
// 1. 合理配置缓存大小
agentCache = Caffeine.newBuilder()
    .maximumSize(500)  // 根据实际内存调整
    .expireAfterAccess(15, TimeUnit.MINUTES)
    .build();

// 2. 及时清理无效缓存
public void invalidateCache(String username) {
    saveMemoryState(username);  // 先保存状态
    agentCache.invalidate(username);
    memoryCache.invalidate(username);
}
```

### 8.5 线程安全

```java
// 使用 ThreadLocal 管理用户上下文
public class UserContext {
    private static final ThreadLocal<UserInfo> CONTEXT = new ThreadLocal<>();
    
    public static void set(UserInfo info) {
        CONTEXT.set(info);
    }
    
    public static UserInfo get() {
        return CONTEXT.get();
    }
    
    public static void clear() {
        CONTEXT.remove();
    }
}

// 在异步边界正确传递
.doOnSubscribe(s -> UserContext.set(userInfo))
.doFinally(s -> UserContext.clear())
```

---

## 附录 A: 常见问题

### Q1: 流式输出显示空白？

检查点：
1. Hook 是否正确设置到 Agent
2. 事件类型是否正确处理 (ReasoningChunkEvent, SummaryChunkEvent)
3. getIncrementalChunk() 返回值是否为 null
4. Sink 是否正确连接到返回的 Flux

### Q2: Agent 状态丢失？

检查点：
1. Redis 是否正常连接
2. memoryStateService.saveState() 是否被调用
3. 缓存驱逐监听器是否正确配置

### Q3: 工具调用失败？

检查点：
1. UserContext 是否正确设置
2. 工具方法的注解是否正确
3. 参数类型是否匹配

---

## 附录 B: 代码模板

### 自定义 Tool 模板

```java
@Service
public class MyToolService {
    
    @Tool(name = "myTool", description = "工具描述")
    public String execute(
        @ToolParam(name = "param1", description = "参数1说明") String param1,
        @ToolParam(name = "param2", description = "参数2说明", required = false) Integer param2
    ) {
        // 获取用户上下文
        UserContext.UserInfo userInfo = UserContext.get();
        String username = userInfo.getUsername();
        
        // 工具逻辑
        return "执行结果";
    }
}
```

### 流式 Controller 模板

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {
    
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        return agentService.chatStream(request);
    }
}
```

---

## 9. Multi-Agent 系统

### 9.1 架构概述

Hera Mind 使用 **Swarm-like 去中心化架构**，支持多个专业 Agent 协作：

```
                    ┌─────────────────┐
                    │   CommonAgent   │  ← 默认入口
                    │   (通用对话)     │
                    └────────┬────────┘
                             │ Handoff
           ┌─────────────────┼─────────────────┐
           ▼                 ▼                 ▼
    ┌───────────┐     ┌───────────┐     ┌───────────┐
    │ LogAgent  │ ◄──►│MonitorAgent│◄──►│ TraceAgent │
    │ (日志管理) │     │ (监控告警) │     │ (链路追踪) │
    └───────────┘     └───────────┘     └───────────┘
```

### 9.2 核心组件

| 组件 | 说明 |
|-----|------|
| **HeraAgent** | Agent 接口，定义名称、描述、提示词、工具集 |
| **AgentRegistry** | Agent 注册表，自动发现所有实现类 |
| **SwarmSession** | 会话状态，记录当前 Agent |
| **SwarmExecutor** | 执行引擎，处理 Handoff |
| **HandoffTool** | 统一 Handoff 工具 |

### 9.3 创建新 Agent

```java
@Component
public class MyNewAgent implements HeraAgent {
    
    @Resource
    private MyToolService myToolService;
    
    @Resource
    private HandoffTool handoffTool;
    
    @Override
    public String getName() {
        return "MyNewAgent";
    }
    
    @Override
    public String getDescription() {
        return "My new agent description";
    }
    
    @Override
    public String getSystemPrompt() {
        return """
            You are a specialized agent...
            
            ## Capabilities
            - ...
            
            ## Handoff Rules
            - Log queries → handoff_to_log
            - Monitor queries → handoff_to_monitor
            """;
    }
    
    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(myToolService);
        toolkit.registerTool(handoffTool);
        return toolkit;
    }
    
    @Override
    public List<String> getHandoffTargets() {
        return List.of("CommonAgent", "LogAgent", "MonitorAgent");
    }
    
    @Override
    public ReActAgent createReActAgent(Model model, AutoContextMemory memory, Hook hook) {
        return ReActAgent.builder()
            .name(getName())
            .sysPrompt(getSystemPrompt())
            .model(model)
            .toolkit(getToolkit())
            .memory(memory)
            .hook(hook)
            .build();
    }
}
```

### 9.4 Memory 共享

所有 Agent 共享同一个 `AutoContextMemory`，Handoff 时新 Agent 能看到完整对话历史：

```java
// Memory 按用户隔离，所有 Agent 共享
private AutoContextMemory getOrCreateMemory(String username) {
    return memoryCache.get(username, k -> {
        // 创建 Memory
        AutoContextMemory memory = new AutoContextMemory(config, model);
        // 恢复状态
        memoryStateService.loadState(username, memory);
        return memory;
    });
}
```

---

## 10. 高并发控制

### 10.1 并发控制架构

```
请求 → ConcurrencyLimiter → UserLockManager → LLMRateLimiter → 执行
       (全局 1000 并发)      (用户级串行)      (API 限流)
```

### 10.2 使用虚拟线程

```java
// VirtualThreadConfig.java
@Bean
public TomcatProtocolHandlerCustomizer<?> virtualThreadExecutorCustomizer() {
    return protocolHandler -> {
        protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    };
}

@Bean
public ExecutorService agentVirtualExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}
```

### 10.3 并发限制使用

```java
// 全局并发检查
if (!concurrencyLimiter.tryAcquire(5000, TimeUnit.MILLISECONDS)) {
    return "系统繁忙，请稍后再试";
}

try {
    // 用户级锁
    if (!userLockManager.tryLock(username, 30000, TimeUnit.MILLISECONDS)) {
        return "您的请求正在处理中";
    }
    
    try {
        // LLM 限流
        llmRateLimiter.acquire(provider);
        
        // 执行业务逻辑
        return processChat(username, message);
        
    } finally {
        userLockManager.unlock(username);
    }
} finally {
    concurrencyLimiter.release();
}
```

### 10.4 配置参数

```yaml
agent:
  concurrency:
    max-concurrent-requests: 1000
    acquire-timeout-ms: 5000
    user-lock-timeout-ms: 30000
  rate-limit:
    openai-rpm: 60
    dashscope-rpm: 100
    default-rpm: 50
```

---

## 附录 C: Multi-Agent 快速参考

### Handoff 标记格式

```
__HANDOFF__:{"target":"LogAgent","reason":"用户需要日志操作","question":"创建日志空间"}
```

### Session 状态结构

```json
{
  "username": "alice",
  "currentAgentName": "LogAgent",
  "agentHistory": ["CommonAgent", "LogAgent"],
  "createTime": 1704067200000,
  "lastActiveTime": 1704070800000
}
```

### Agent 注册验证

```java
// 启动时自动打印已注册的 Agent
log.info("Registered {} agents: {}, default: {}", 
    agents.size(), agents.keySet(), defaultAgent.getName());
// 输出: Registered 3 agents: [CommonAgent, LogAgent, MonitorAgent], default: CommonAgent
```

---

*本文档版本: 1.1*
*最后更新: 2026-04-13*
