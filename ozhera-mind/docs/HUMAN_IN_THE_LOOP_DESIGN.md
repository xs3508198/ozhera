# Human in the Loop 确认机制设计文档

## 1. 问题背景

### 1.1 当前问题

在 OzHera Mind 多智能体系统中，Agent 可以执行各种操作工具，包括**危险操作**（如删除日志空间、删除告警策略等）。当前实现存在以下问题：

1. **删除操作没有用户确认机制**：Agent 可以直接执行删除，无法保证用户真正想要执行
2. **仅依赖 Prompt 约束不可靠**：System Prompt 中的 "Confirm before delete" 指令可能被 LLM 忽略
3. **无法满足生产环境安全要求**：危险操作需要显式的用户确认

### 1.2 当前代码现状

**System Prompt 层面（软约束）**：

```java
// LogAgent.java
private static final String SYSTEM_PROMPT = """
    ## Important
    - Confirm before destructive operations (delete)
    """;
```

**工具层面（无确认直接执行）**：

```java
// LogToolService.java
@Tool(name = "deleteLogSpace")
public String deleteSpace(Long spaceId) {
    // 直接调用API删除，没有确认！
    return logAgentApiService.deleteSpace(spaceInfo);
}
```

### 1.3 目标

实现 **Human in the Loop** 确认机制：
- 危险操作必须经过用户显式确认
- 确认机制在代码层面强制执行，不依赖 LLM 理解
- 支持复合任务（如"删除A，新建B"）的连续执行

---

## 2. 技术方案选型

### 2.1 方案对比

| 方案 | 实现方式 | 优点 | 缺点 |
|-----|---------|-----|------|
| **文字确认** | LLM 对话判断用户意图 | 自然 | 不可靠，依赖LLM |
| **按钮确认 + 请求结束** | 工具返回确认消息，请求结束，新请求继续 | 连接短 | 流程断裂，状态管理复杂 |
| **按钮确认 + 工具内阻塞** | 工具内部阻塞等待，用户确认后唤醒继续 | 流程连续，代码简洁 | 连接保持时间长 |

### 2.2 选定方案：按钮确认 + 工具内阻塞

**选择理由**：
1. **流程连续**：用户说"删除A，新建B"，确认删除后自动继续新建
2. **代码简洁**：对 Agent 透明，不需要修改框架
3. **虚拟线程支持**：Java 21 虚拟线程使阻塞等待几乎零成本
4. **前端已有 SSE**：可以保持连接等待确认

---

## 3. 架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              前端 (SSE Client)                               │
├─────────────────────────────────────────────────────────────────────────────┤
│  EventSource("/chat/stream")              POST /confirm                     │
│         ↓ 接收流式响应                          ↓ 点击确认按钮                │
│         ↓ 渲染确认按钮                          ↓                            │
└─────────────────────────────────────────────────────────────────────────────┘
              │ SSE 连接保持                           │ 新HTTP请求
              ↓                                        ↓
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Worker 服务                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────────────┐               ┌─────────────────────┐              │
│  │  WorkerController   │               │  ConfirmController  │              │
│  │  /chat/stream (SSE) │               │  /confirm           │              │
│  └──────────┬──────────┘               └──────────┬──────────┘              │
│             │                                      │                        │
│             ↓                                      ↓                        │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │                   ConfirmationManager                        │           │
│  │  ┌────────────────────────────────────────────────────────┐  │           │
│  │  │  ConcurrentHashMap<token, PendingConfirmation>         │  │           │
│  │  │                                                        │  │           │
│  │  │  "token-123" → { future, username, operation, params } │  │           │
│  │  │  "token-456" → { future, username, operation, params } │  │           │
│  │  └────────────────────────────────────────────────────────┘  │           │
│  │                                                              │           │
│  │  register() → 创建Future，返回token                          │           │
│  │  waitForConfirmation() → future.get() 阻塞                   │           │
│  │  confirm() → future.complete() 唤醒                          │           │
│  └──────────────────────────────────────────────────────────────┘           │
│             ↑ wait                             │ complete                   │
│             │                                  ↓                            │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │                     Tool (工具层)                            │           │
│  │                                                              │           │
│  │  deleteLogSpace() {                                          │           │
│  │      sink.next("##CONFIRM##...");  // 发送确认消息           │           │
│  │      confirmed = manager.waitForConfirmation();  // 阻塞     │           │
│  │      if (confirmed) executeDelete();  // 执行               │           │
│  │      return result;  // 返回结果给Agent                      │           │
│  │  }                                                           │           │
│  └──────────────────────────────────────────────────────────────┘           │
│             ↑                                                               │
│             │                                                               │
│  ┌──────────────────────────────────────────────────────────────┐           │
│  │                     ReActAgent                               │           │
│  │                                                              │           │
│  │  Agent 不感知阻塞，只是在等待工具返回                          │           │
│  │  工具返回后，结果自动加入 Memory                               │           │
│  └──────────────────────────────────────────────────────────────┘           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

| 组件 | 职责 |
|-----|------|
| **ConfirmationManager** | 管理待确认请求，协调阻塞/唤醒 |
| **ConfirmController** | 接收用户确认请求，唤醒等待线程 |
| **Tool（工具）** | 发送确认消息，阻塞等待，执行操作 |
| **FluxSink** | SSE 通道，发送确认消息到前端 |

### 3.3 跨请求线程唤醒机制

**核心问题**：请求1（SSE）中的工具阻塞等待，请求2（/confirm）如何找到并唤醒请求1的线程？

**解决方案**：Token + ConcurrentHashMap + CompletableFuture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                      ConfirmationManager                                    │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  ConcurrentHashMap<String, PendingConfirmation>                       │  │
│  │                                                                       │  │
│  │  "token-abc-123" → { future: CompletableFuture, username, ... }      │  │
│  │         ↑                       ↑                                     │  │
│  │         │                       │                                     │  │
│  │      唯一标识              连接两个请求的桥梁                           │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│                                                                             │
│  请求1 (SSE/工具)                      请求2 (/confirm)                     │
│  ┌──────────────────┐                  ┌──────────────────┐                │
│  │ 1. register()    │                  │                  │                │
│  │    → 创建 Future │                  │                  │                │
│  │    → 存入 Map    │                  │                  │                │
│  │    → 返回 token  │ ──── token ────→ │ (前端传来token)  │                │
│  │                  │                  │                  │                │
│  │ 2. 发送token到前端│                  │                  │                │
│  │                  │                  │                  │                │
│  │ 3. future.get()  │                  │ 4. confirm()     │                │
│  │    阻塞等待 ════════════════════════│    → get(token)  │                │
│  │         ↑        │                  │    → complete()  │                │
│  │         │        │                  │         │        │                │
│  │         └────────│──────────────────│─────────┘        │                │
│  │                  │                  │    唤醒!         │                │
│  │ 5. 继续执行      │                  │                  │                │
│  └──────────────────┘                  └──────────────────┘                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**工作原理**：

| 步骤 | 请求 | 操作 | 说明 |
|-----|------|------|------|
| 1 | 请求1 | `register()` | 创建 Future，存入 Map，返回 token |
| 2 | 请求1 | 发送 token | 通过 SSE 发送给前端 |
| 3 | 请求1 | `future.get()` | 阻塞等待，虚拟线程挂起 |
| 4 | 请求2 | `confirm(token)` | 通过 token 从 Map 找到 Future，调用 `complete()` |
| 5 | 请求1 | 被唤醒 | `get()` 返回，继续执行 |

**关键代码**：

```java
// 请求1：工具中
String token = confirmationManager.register(username, operation, params);
sink.next("##CONFIRM##" + token);                    // token 发给前端
boolean confirmed = confirmationManager.waitForConfirmation(token);  // 阻塞

// 请求2：确认接口
confirmationManager.confirm(request.getToken(), true);  // 用 token 找到并唤醒
```

**类比理解**：
- **Token** = 取餐号码牌
- **Map** = 餐厅叫号系统
- **Future** = 等待区的顾客
- **complete()** = 叫号，通知顾客取餐

请求1 拿到号码牌（token）后在等待区等着（future.get），请求2 拿着同样的号码牌来叫号（complete），请求1 听到叫号后继续执行。

---

## 4. 详细流程

### 4.1 时序图

```
 前端                Tool(虚拟线程A)           ConfirmationManager        ConfirmController
  │                        │                          │                        │
  │ POST /chat/stream      │                          │                        │
  │ "删除空间A，新建空间B"   │                          │                        │
  │ ──────────────────────→│                          │                        │
  │                        │                          │                        │
  │                        │ LLM决定调用deleteLogSpace │                        │
  │                        │ (工具开始执行)            │                        │
  │                        │                          │                        │
  │                        │ register(token)          │                        │
  │                        │─────────────────────────→│                        │
  │                        │                          │ 创建 CompletableFuture │
  │                        │                          │                        │
  │ SSE: ##CONFIRM##       │                          │                        │
  │ {token,message,params} │                          │                        │
  │←───────────────────────│                          │                        │
  │                        │                          │                        │
  │ 渲染确认按钮            │ waitForConfirmation()    │                        │
  │                        │─────────────────────────→│                        │
  │                        │                          │                        │
  │                        │     ╔═══════════════╗    │                        │
  │                        │     ║ 虚拟线程A挂起  ║    │                        │
  │                        │     ║ future.get()  ║    │                        │
  │                        │     ║ 不占平台线程   ║    │                        │
  │                        │     ╚═══════════════╝    │                        │
  │                        │                          │                        │
  │ [用户点击确认]          │                          │                        │
  │                        │                          │                        │
  │ POST /confirm          │                          │                        │
  │ {token, confirmed}     │                          │                        │
  │────────────────────────────────────────────────────────────────────────────→│
  │                        │                          │                        │
  │                        │                          │ confirm(token, true)   │
  │                        │                          │←───────────────────────│
  │                        │                          │                        │
  │                        │                          │ future.complete(true)  │
  │                        │                          │                        │
  │                        │     ╔═══════════════╗    │                        │
  │                        │     ║ 虚拟线程A唤醒  ║    │                        │
  │                        │     ║ 返回 true     ║    │                        │
  │                        │     ╚═══════════════╝    │                        │
  │                        │                          │                        │
  │                        │ 执行删除 (调用后端API)    │                        │
  │                        │ logAgentApiService       │                        │
  │                        │         .deleteSpace()   │                        │
  │                        │                          │                        │
  │                        │ 工具返回 "删除成功"       │                        │
  │                        │                          │                        │
  │ SSE: "删除成功"         │                          │                        │
  │←───────────────────────│                          │                        │
  │                        │                          │                        │
  │                        │ Agent: memory.add(结果)  │                        │
  │                        │ Agent: 继续LLM调用       │                        │
  │                        │                          │                        │
  │                        │ LLM决定调用createLogSpace│                        │
  │                        │ (工具执行，无需确认)      │                        │
  │                        │                          │                        │
  │ SSE: "新建成功"         │                          │                        │
  │←───────────────────────│                          │                        │
  │                        │                          │                        │
  │ SSE: 连接关闭           │                          │                        │
  │←───────────────────────│                          │                        │
```

### 4.2 ReActAgent 执行状态

```
用户: "删除空间A，新建空间B"

┌─────────────────────────────────────────────────────────────────────┐
│ ReActAgent 循环第1轮                                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Memory:                                                            │
│    [user]: 删除空间A，新建空间B                                       │
│                                                                     │
│  LLM思考: 需要先删除A，调用 deleteLogSpace(A)                         │
│                                                                     │
│  执行工具: deleteLogSpace(A)                                         │
│       │                                                             │
│       ↓                                                             │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │  工具内部:                                                   │    │
│  │    1. 发送确认消息到SSE                                      │    │
│  │    2. future.get() ← ════════════════════════════════════   │    │
│  │                       │                                     │    │
│  │                       │  虚拟线程挂起                        │    │
│  │                       │  以下状态全部冻结:                    │    │
│  │                       │    - ReActAgent 循环位置             │    │
│  │                       │    - Memory 内容                     │    │
│  │                       │    - 局部变量                        │    │
│  │                       │    - SSE Sink 连接                   │    │
│  │                       │                                     │    │
│  │                       ↓                                     │    │
│  │                  [用户点击确认]                               │    │
│  │                       │                                     │    │
│  │                       ↓                                     │    │
│  │    3. 唤醒，confirmed = true                                │    │
│  │    4. 执行 logAgentApiService.deleteSpace()                 │    │
│  │    5. return "删除成功"                                      │    │
│  └─────────────────────────────────────────────────────────────┘    │
│       │                                                             │
│       ↓                                                             │
│  Agent 收到工具返回值: "删除成功"                                     │
│  Agent 自动: memory.add([tool_result]: 删除成功)                     │
│                                                                     │
│  Memory:                                                            │
│    [user]: 删除空间A，新建空间B                                       │
│    [assistant]: 调用 deleteLogSpace(A)                               │
│    [tool_result]: 删除成功                                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ ReActAgent 循环第2轮                                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  LLM思考: A已删除，现在需要新建B，调用 createLogSpace(B)              │
│                                                                     │
│  执行工具: createLogSpace(B) → 返回 "新建成功"                        │
│                                                                     │
│  Memory:                                                            │
│    [user]: 删除空间A，新建空间B                                       │
│    [assistant]: 调用 deleteLogSpace(A)                               │
│    [tool_result]: 删除成功                                           │
│    [assistant]: 调用 createLogSpace(B)                               │
│    [tool_result]: 新建成功                                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│ ReActAgent 循环第3轮                                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  LLM思考: 两个任务都完成了，给用户最终回答                             │
│                                                                     │
│  输出: "已完成：1. 删除空间A ✓  2. 新建空间B ✓"                        │
│                                                                     │
│  finished = true, 循环结束                                           │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 5. 坑点与解决方案

### 5.1 坑点一：Memory 对象不共享

**问题描述**：
请求1（SSE）和请求2（/confirm）是两个独立的HTTP请求，如果在请求2中操作Memory，请求1的Agent无法感知。

```
请求1: Memory对象A (在内存中)
请求2: Memory对象B (从Redis加载)
       ↓
写入Memory B，但Agent持有的是Memory A，看不到！
```

**解决方案**：
在工具内部阻塞，工具返回结果自动加入Memory，请求2只负责唤醒，不操作Memory。

```java
// 请求2 只唤醒，不写Memory
@PostMapping("/confirm")
public Result<?> confirm(@RequestBody ConfirmRequest request) {
    confirmationManager.confirm(request.getToken(), request.isConfirmed());
    return Result.success();
}
```

### 5.2 坑点二：SSE Sink 传递

**问题描述**：
工具需要通过 SSE 发送确认消息到前端，但标准的 `@Tool` 方法签名不包含 Sink。

**解决方案：构造时注入**

每个请求创建新的 Tool 实例，通过构造函数注入 FluxSink：

```java
public class DeleteSpaceTool {
    private final FluxSink<String> sink;  // 构造时注入
    
    public DeleteSpaceTool(FluxSink<String> sink, ...) {
        this.sink = sink;
    }
    
    @Tool(name = "deleteLogSpace")
    public String deleteSpace(Long spaceId) {
        // 直接使用 this.sink
        sink.next("##CONFIRM##...");
    }
}
```

### 5.3 坑点三：超时处理

**问题描述**：
用户可能长时间不点击确认按钮，导致：
1. 虚拟线程一直挂起
2. SSE 连接超时
3. 资源无法释放

**解决方案**：
设置超时时间，超时后自动取消操作。

```java
public boolean waitForConfirmation(String token, long timeoutSeconds) {
    try {
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        log.warn("Confirmation timeout: token={}", token);
        return false;  // 超时视为取消
    } finally {
        pendingConfirmations.remove(token);
    }
}
```

**前端配合**：
- 显示倒计时
- 超时后禁用确认按钮

### 5.4 坑点四：Token 安全性

**问题描述**：
确认 Token 如果可预测，可能被恶意利用。

**解决方案**：
1. 使用 UUID 生成不可预测的 Token
2. Token 绑定用户，验证时检查用户匹配
3. Token 一次性使用，使用后立即删除
4. 设置短有效期（如5分钟）

```java
public String register(String username, String operation, Map<String, Object> params) {
    String token = UUID.randomUUID().toString();
    PendingConfirmation pending = new PendingConfirmation(
        username,           // 绑定用户
        operation,
        params,
        new CompletableFuture<>(),
        System.currentTimeMillis()  // 记录创建时间
    );
    pendingConfirmations.put(token, pending);
    return token;
}

public boolean confirm(String token, String username, boolean confirmed) {
    PendingConfirmation pending = pendingConfirmations.get(token);
    if (pending == null) return false;
    
    // 验证用户
    if (!pending.getUsername().equals(username)) {
        log.warn("User mismatch: expected={}, actual={}", pending.getUsername(), username);
        return false;
    }
    
    // 验证是否过期（5分钟）
    if (System.currentTimeMillis() - pending.getCreateTime() > 5 * 60 * 1000) {
        pendingConfirmations.remove(token);
        return false;
    }
    
    pending.getFuture().complete(confirmed);
    pendingConfirmations.remove(token);  // 一次性使用
    return true;
}
```

### 5.5 坑点五：SSE 连接保活

**问题描述**：
长时间等待确认期间，SSE 连接可能被中间代理（如 Nginx）超时关闭。

**解决方案**：
定期发送心跳消息保持连接。

```java
// 在等待确认期间发送心跳
ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
    sink.next("##HEARTBEAT##");
}, 15, 15, TimeUnit.SECONDS);

try {
    return future.get(timeoutSeconds, TimeUnit.SECONDS);
} finally {
    heartbeat.cancel(true);
}
```

**Nginx 配置**：
```nginx
proxy_read_timeout 300s;
proxy_send_timeout 300s;
```

### 5.6 坑点六：并发确认请求

**问题描述**：
用户可能多次点击确认按钮，导致重复唤醒。

**解决方案**：
`CompletableFuture.complete()` 是幂等的，多次调用只有第一次生效。同时前端禁用按钮防止重复点击。

```java
// CompletableFuture.complete() 是幂等的
future.complete(true);   // 第一次生效
future.complete(false);  // 第二次无效，返回false
```

### 5.7 坑点七：agentscope 框架无工具超时机制

**问题描述**：
agentscope 框架的 ReActAgent 对工具调用**没有超时设置**。Agent 调用工具时是同步等待，工具执行多久，Agent 就等多久。

```java
// ReActAgent.builder() 没有 toolTimeout 之类的设置
ReActAgent agent = ReActAgent.builder()
    .name(getName())
    .sysPrompt(getSystemPrompt())
    .model(model)
    .toolkit(getToolkit())
    .memory(memory)
    .build();
```

**影响**：
- 好消息：大模型/框架不会认为工具超时
- 坏消息：如果不处理，用户不确认，虚拟线程会一直挂起

**解决方案**：
在工具内部自己控制超时，不依赖框架。

```java
@Tool
public String deleteSpace(Long spaceId) {
    sink.next("##CONFIRM##...");
    
    // 我们自己控制超时！
    boolean confirmed = confirmationManager.waitForConfirmation(token, 300);
    //                                                          ↑
    //                                         future.get(300, TimeUnit.SECONDS)
    
    if (confirmed) {
        return "删除成功";
    } else {
        return "操作已取消（超时或用户取消）";
    }
}
```

### 5.8 坑点八：Web 服务 vs CLI 架构差异

**问题描述**：
像 Claude Code 这样的 CLI 工具可以一直等待用户确认，因为它直接读取 stdin，没有网络超时问题。但 Web 服务有以下限制：

| 特性 | CLI 工具 (如 Claude Code) | Web 服务 (OzHera Mind) |
|-----|--------------------------|------------------------|
| 运行环境 | 本地终端进程 | 分布式 Web 服务 |
| 用户输入 | stdin 直接读取 | HTTP/SSE 请求 |
| 超时问题 | 无（进程一直活着） | 有（Nginx/LB 超时） |
| 连接保持 | 无需 | 需要心跳保活 |
| 用户离开 | Ctrl+C 终止 | 页面关闭/刷新 |

**解决方案**：
实现长等待支持，接近 CLI 的用户体验。

### 5.9 坑点九：竞态条件与双重检查

**问题描述**：
在确认和执行之间存在竞态条件：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  竞态场景1：确认与超时的竞争                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  T1: future.get() 抛出 TimeoutException                                     │
│  T2: confirm() 检查 pending != null ✓（还没被移除）                          │
│  T3: confirm() 调用 complete(true)，返回"确认成功"                           │
│  T4: finally { remove(token) }                                              │
│  T5: waitForConfirmation() 返回 false                                       │
│                                                                             │
│  结果不一致：用户看到"确认成功"，但工具执行"已取消"                           │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│  竞态场景2：重复执行风险                                                     │
├─────────────────────────────────────────────────────────────────────────────┤
│  虽然当前设计中不太可能发生，但为了安全性，                                   │
│  应该确保删除操作只能被执行一次。                                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

**解决方案**：
使用 `AtomicBoolean` + 双重检查，确保只有一个线程能执行操作。

**1. PendingConfirmation 增加执行标记**：

```java
@Data
@AllArgsConstructor
public static class PendingConfirmation {
    private String username;
    private String operation;
    private Map<String, Object> params;
    private CompletableFuture<Boolean> future;
    private long createTime;
    
    // 新增：确保只执行一次
    private final AtomicBoolean executed = new AtomicBoolean(false);
    
    // 新增：标记是否已处理（超时或确认）
    private final AtomicBoolean processed = new AtomicBoolean(false);
}
```

**2. ConfirmationManager 使用原子操作**：

```java
@Component
public class ConfirmationManager {
    
    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();
    
    /**
     * 等待用户确认
     */
    public boolean waitForConfirmation(String token, long timeoutSeconds) {
        PendingConfirmation pending = pendingConfirmations.get(token);
        if (pending == null) {
            return false;
        }
        
        try {
            return pending.getFuture().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 原子标记为已处理
            if (pending.getProcessed().compareAndSet(false, true)) {
                pendingConfirmations.remove(token);
                log.warn("Confirmation timeout: token={}", token);
            }
            return false;
        } catch (Exception e) {
            pending.getProcessed().compareAndSet(false, true);
            pendingConfirmations.remove(token);
            return false;
        }
        // 确认成功时不移除，等 tryAcquireExecution() 移除
    }
    
    /**
     * 用户确认（原子操作）
     */
    public boolean confirm(String token, String username, boolean confirmed) {
        PendingConfirmation pending = pendingConfirmations.get(token);
        if (pending == null) {
            return false;
        }
        
        // 原子标记为已处理
        if (!pending.getProcessed().compareAndSet(false, true)) {
            // 已被超时处理
            log.warn("Confirmation already processed (timeout): token={}", token);
            return false;
        }
        
        // 验证用户
        if (!pending.getUsername().equals(username)) {
            return false;
        }
        
        pending.getFuture().complete(confirmed);
        return true;
    }
    
    /**
     * 尝试获取执行权（双重检查）
     */
    public boolean tryAcquireExecution(String token) {
        PendingConfirmation pending = pendingConfirmations.get(token);
        if (pending == null) {
            log.warn("Cannot acquire execution, token not found: {}", token);
            return false;
        }
        
        // CAS：确保只有一个线程能获取执行权
        boolean acquired = pending.getExecuted().compareAndSet(false, true);
        
        if (acquired) {
            log.info("Execution acquired: token={}", token);
            pendingConfirmations.remove(token);  // 获取成功后移除
        } else {
            log.warn("Execution already acquired: token={}", token);
        }
        
        return acquired;
    }
}
```

**3. 工具中使用双重检查**（使用构造时注入 sink）：

```java
// Tool 类（每个请求创建新实例，注入 sink）
public class DeleteLogSpaceTool extends RequestScopedTool {
    
    private final LogAgentApiService logAgentApiService;
    
    public DeleteLogSpaceTool(FluxSink<String> sink, 
                               ConfirmationManager confirmationManager,
                               LogAgentApiService logAgentApiService) {
        super(sink, confirmationManager);  // sink 通过构造函数注入
        this.logAgentApiService = logAgentApiService;
    }
    
    @Tool(name = "deleteLogSpace")
    public String deleteSpace(Long spaceId) {
        String username = UserContext.get().getUsername();
        
        // 1. 注册确认
        String token = confirmationManager.register(username, "deleteLogSpace", 
            Map.of("spaceId", spaceId));
        
        // 2. 发送确认消息（使用 this.sink，不依赖 ThreadLocal）
        sendConfirmation(buildConfirmMessage(token, spaceId));
        
        // 3. 第一次检查：等待确认
        boolean confirmed = confirmationManager.waitForConfirmation(token, 300);
        
        if (!confirmed) {
            return "操作已取消";
        }
        
        // 4. 第二次检查：获取执行权（双重检查锁）
        if (!confirmationManager.tryAcquireExecution(token)) {
            log.error("Failed to acquire execution: token={}", token);
            return "操作状态异常，已取消";
        }
        
        // 5. 执行删除（只有获取执行权的线程才能到这里）
        log.info("Executing delete space: spaceId={}", spaceId);
        SpaceInfo spaceInfo = new SpaceInfo();
        spaceInfo.setSpaceId(spaceId);
        spaceInfo.setUserInfo(getCurrentUserInfo());
        String result = logAgentApiService.deleteSpace(spaceInfo);
        
        return "删除成功: " + result;
    }
}
```

**4. 流程图**：

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  双重检查流程                                                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  register()                                                                 │
│       │                                                                     │
│       ↓                                                                     │
│  Map: token → { future, processed: false, executed: false }                 │
│       │                                                                     │
│       ↓                                                                     │
│  waitForConfirmation()                                                      │
│       │                                                                     │
│       ├─→ 超时                                                              │
│       │     │                                                               │
│       │     ↓                                                               │
│       │   processed.compareAndSet(false, true)                              │
│       │     ├─→ 成功：remove, return false                                  │
│       │     └─→ 失败：已被 confirm 处理，return false                        │
│       │                                                                     │
│       └─→ 确认成功：return true                                             │
│                 │                                                           │
│                 ↓                                                           │
│         tryAcquireExecution()                                               │
│                 │                                                           │
│                 ↓                                                           │
│         executed.compareAndSet(false, true)                                 │
│                 │                                                           │
│           ┌─────┴─────┐                                                     │
│           ↓           ↓                                                     │
│         成功         失败                                                    │
│           │           │                                                     │
│           ↓           ↓                                                     │
│      remove()    return false                                               │
│      执行删除     (异常情况)                                                 │
│           │                                                                 │
│           ↓                                                                 │
│      return "删除成功"                                                       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**5. 关键点总结**：

| 原子变量 | 作用 | CAS 操作 |
|---------|------|----------|
| `processed` | 防止确认与超时竞争 | 只有一个能标记为已处理 |
| `executed` | 防止重复执行 | 只有一个能获取执行权 |

**6. 为什么需要两个原子变量**：

- `processed`：解决 confirm() 和 timeout 的竞争
- `executed`：双重检查，确保删除操作只执行一次

### 5.10 坑点十：ThreadLocal + Reactor 线程切换

**问题描述**：

在 Reactor/WebFlux 环境中，ThreadLocal 可能会失效，因为 Reactor 可能会在不同的线程上执行操作。

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ThreadLocal + Reactor = 有坑！                                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  Thread-A (请求线程)                    Thread-B (Reactor调度线程)           │
│  ┌──────────────────┐                  ┌──────────────────┐                │
│  │ ToolContext      │                  │ ToolContext      │                │
│  │  .setSink(sink)  │                  │  .getSink()      │                │
│  │                  │                  │     = null !!!   │                │
│  │ ThreadLocal:     │                  │ ThreadLocal:     │                │
│  │  sink = ✓        │                  │  sink = ✗ 空的   │                │
│  └──────────────────┘                  └──────────────────┘                │
│                                                                             │
│  问题：如果 agentscope 的 ReActAgent 内部使用线程池或 Reactor 调度器         │
│       执行 Tool，那么 Tool 执行时的线程可能不是设置 ThreadLocal 的线程       │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

**触发场景**：

| 场景 | ThreadLocal 是否有效 |
|-----|---------------------|
| Flux.create() 内同步执行 | ✓ 有效 |
| .publishOn(Schedulers.parallel()) | ✗ 无效（切换线程） |
| .subscribeOn(Schedulers.boundedElastic()) | ✗ 无效（切换线程） |
| agentscope 内部使用线程池执行 Tool | ✗ 可能无效 |

**解决方案：构造时注入**

不使用 ThreadLocal，在创建 Tool 时直接注入 FluxSink：

```java
// Tool 定义
public class DeleteSpaceTool {
    private final FluxSink<String> sink;  // 构造时注入
    private final ConfirmationManager confirmationManager;
    
    public DeleteSpaceTool(FluxSink<String> sink, ConfirmationManager confirmationManager) {
        this.sink = sink;
        this.confirmationManager = confirmationManager;
    }
    
    @Tool(name = "deleteLogSpace")
    public String deleteSpace(Long spaceId) {
        // 直接使用 this.sink，不依赖 ThreadLocal
        sink.next("##CONFIRM##...");
        // ...
    }
}

// 在 SwarmExecutor 中创建 Agent 时
public Flux<String> execute(...) {
    return Flux.create(sink -> {
        // 为每个请求创建新的 Tool 实例，注入 sink
        DeleteSpaceTool deleteTool = new DeleteSpaceTool(sink, confirmationManager);
        
        // 创建 Agent 时使用这个 Tool 实例
        ReActAgent agent = agentDef.createReActAgentWithTools(model, memory, hook, 
            List.of(deleteTool, ...));
        // ...
    });
}
```

**改造后的 HeraAgent 接口**：

```java
public interface HeraAgent {
    String getName();
    String getSystemPrompt();
    // ... 
    
    // 新增：创建带有请求级 Tool 的 Agent
    ReActAgent createReActAgentWithTools(
        Model model, 
        AutoContextMemory memory, 
        StreamingHook hook,
        List<Object> requestScopedTools  // 每个请求独立的 Tool 实例
    );
}
```

**关键点**：

1. 每个 HTTP 请求创建新的 Tool 实例
2. Tool 实例持有该请求的 FluxSink
3. 不依赖 ThreadLocal，完全避免线程切换问题

---

## 6. 实现代码

### 6.1 ConfirmationManager

```java
package org.apache.ozhera.mind.service.confirmation;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class ConfirmationManager {

    private static final long DEFAULT_TIMEOUT_SECONDS = 300;  // 5分钟
    private static final long EXPIRE_TIME_MS = 5 * 60 * 1000;  // 5分钟过期

    private final Map<String, PendingConfirmation> pendingConfirmations = new ConcurrentHashMap<>();

    @Data
    public static class PendingConfirmation {
        private String username;
        private String operation;
        private Map<String, Object> params;
        private CompletableFuture<Boolean> future;
        private long createTime;
        
        // 原子变量：防止确认与超时的竞争
        private final AtomicBoolean processed = new AtomicBoolean(false);
        
        // 原子变量：双重检查，确保只执行一次
        private final AtomicBoolean executed = new AtomicBoolean(false);
        
        public PendingConfirmation(String username, String operation, 
                                   Map<String, Object> params, 
                                   CompletableFuture<Boolean> future, 
                                   long createTime) {
            this.username = username;
            this.operation = operation;
            this.params = params;
            this.future = future;
            this.createTime = createTime;
        }
    }

    /**
     * 注册一个待确认的操作
     */
    public String register(String username, String operation, Map<String, Object> params) {
        String token = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        PendingConfirmation pending = new PendingConfirmation(
                username, operation, params, future, System.currentTimeMillis()
        );

        pendingConfirmations.put(token, pending);
        log.info("Registered confirmation: token={}, user={}, operation={}", token, username, operation);

        return token;
    }

    /**
     * 等待用户确认（阻塞当前虚拟线程）
     * 注意：确认成功时不移除 pending，等 tryAcquireExecution() 移除
     */
    public boolean waitForConfirmation(String token, long timeoutSeconds) {
        PendingConfirmation pending = pendingConfirmations.get(token);
        if (pending == null) {
            log.warn("Confirmation not found: token={}", token);
            return false;
        }

        try {
            log.info("Waiting for confirmation: token={}, timeout={}s", token, timeoutSeconds);
            return pending.getFuture().get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // 原子标记为已处理，防止与 confirm() 竞争
            if (pending.getProcessed().compareAndSet(false, true)) {
                pendingConfirmations.remove(token);
                log.warn("Confirmation timeout: token={}", token);
            }
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.getProcessed().compareAndSet(false, true);
            pendingConfirmations.remove(token);
            log.warn("Confirmation interrupted: token={}", token);
            return false;
        } catch (Exception e) {
            pending.getProcessed().compareAndSet(false, true);
            pendingConfirmations.remove(token);
            log.error("Confirmation error: token={}", token, e);
            return false;
        }
        // 确认成功时不在这里移除，等 tryAcquireExecution() 移除
    }

    /**
     * 等待用户确认（使用默认超时时间）
     */
    public boolean waitForConfirmation(String token) {
        return waitForConfirmation(token, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 用户确认操作（唤醒等待的虚拟线程）
     * 使用原子操作防止与超时处理竞争
     */
    public boolean confirm(String token, String username, boolean confirmed) {
        PendingConfirmation pending = pendingConfirmations.get(token);

        if (pending == null) {
            log.warn("Confirmation not found or expired: token={}", token);
            return false;
        }

        // 原子标记为已处理，防止与 timeout 竞争
        if (!pending.getProcessed().compareAndSet(false, true)) {
            log.warn("Confirmation already processed (timeout): token={}", token);
            return false;
        }

        // 验证用户
        if (!pending.getUsername().equals(username)) {
            log.warn("User mismatch: token={}, expected={}, actual={}",
                    token, pending.getUsername(), username);
            return false;
        }

        // 验证是否过期
        if (System.currentTimeMillis() - pending.getCreateTime() > EXPIRE_TIME_MS) {
            log.warn("Confirmation expired: token={}", token);
            pendingConfirmations.remove(token);
            return false;
        }

        // 唤醒等待的虚拟线程
        boolean completed = pending.getFuture().complete(confirmed);
        log.info("Confirmation completed: token={}, confirmed={}, success={}",
                token, confirmed, completed);

        return completed;
    }

    /**
     * 尝试获取执行权（双重检查）
     * 只有成功获取执行权的线程才能执行危险操作
     */
    public boolean tryAcquireExecution(String token) {
        PendingConfirmation pending = pendingConfirmations.get(token);
        
        if (pending == null) {
            log.warn("Cannot acquire execution, token not found: {}", token);
            return false;
        }
        
        // CAS：确保只有一个线程能获取执行权
        boolean acquired = pending.getExecuted().compareAndSet(false, true);
        
        if (acquired) {
            log.info("Execution acquired: token={}", token);
            pendingConfirmations.remove(token);  // 获取成功后移除
        } else {
            log.warn("Execution already acquired by another thread: token={}", token);
        }
        
        return acquired;
    }

    /**
     * 获取待确认操作的信息
     */
    public PendingConfirmation getPending(String token) {
        return pendingConfirmations.get(token);
    }

    /**
     * 清理过期的确认请求（可由定时任务调用）
     */
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        pendingConfirmations.entrySet().removeIf(entry -> {
            if (now - entry.getValue().getCreateTime() > EXPIRE_TIME_MS) {
                entry.getValue().getFuture().complete(false);  // 超时取消
                log.info("Cleaned up expired confirmation: token={}", entry.getKey());
                return true;
            }
            return false;
        });
    }
}
```

### 6.2 RequestScopedTool（请求级工具基类）

```java
package org.apache.ozhera.mind.service.llm.tool;

import reactor.core.publisher.FluxSink;

/**
 * 请求级别的 Tool 基类
 * 每个请求创建新实例，注入该请求的 FluxSink
 */
public abstract class RequestScopedTool {
    
    protected final FluxSink<String> sink;
    protected final ConfirmationManager confirmationManager;
    
    public RequestScopedTool(FluxSink<String> sink, ConfirmationManager confirmationManager) {
        this.sink = sink;
        this.confirmationManager = confirmationManager;
    }
    
    /**
     * 发送确认请求到前端
     */
    protected void sendConfirmation(String confirmJson) {
        if (sink != null) {
            sink.next("##CONFIRM##" + confirmJson);
        }
    }
}
```

### 6.3 确认消息格式

```java
package org.apache.ozhera.mind.service.confirmation;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ConfirmationMessage {

    /**
     * 消息类型
     */
    private String type = "confirmation";

    /**
     * 确认token
     */
    private String token;

    /**
     * 操作类型
     */
    private String operation;

    /**
     * 提示消息
     */
    private String message;

    /**
     * 操作详情（用于展示）
     */
    private Map<String, Object> detail;

    /**
     * 过期时间戳
     */
    private long expireTime;
}
```

### 6.4 工具改造示例（构造时注入方式）

```java
package org.apache.ozhera.mind.service.llm.tool;

import com.google.gson.Gson;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.confirmation.ConfirmationManager;
import org.apache.ozhera.mind.service.confirmation.ConfirmationMessage;
import org.apache.ozhera.mind.service.context.UserContext;
import reactor.core.publisher.FluxSink;

import java.util.Map;

/**
 * 日志空间删除工具 - 请求级实例
 * 每个 HTTP 请求创建新实例，注入该请求的 FluxSink
 * 
 * 注意：不使用 @Service 注解，因为每次请求需要创建新实例
 */
@Slf4j
public class DeleteLogSpaceTool {

    private static final long CONFIRM_TIMEOUT_SECONDS = 300;  // 5分钟

    // 构造时注入，不依赖 ThreadLocal
    private final FluxSink<String> sink;
    private final ConfirmationManager confirmationManager;
    private final LogAgentApiService logAgentApiService;
    private final Gson gson;

    public DeleteLogSpaceTool(FluxSink<String> sink,
                               ConfirmationManager confirmationManager,
                               LogAgentApiService logAgentApiService,
                               Gson gson) {
        this.sink = sink;
        this.confirmationManager = confirmationManager;
        this.logAgentApiService = logAgentApiService;
        this.gson = gson;
    }

    @Tool(name = "deleteLogSpace",
            description = "Delete a log space by its ID. This will also delete all stores and tails within the space.")
    public String deleteSpace(
            @ToolParam(name = "spaceId", description = "The ID of the space to delete", required = true)
            Long spaceId) {

        String username = UserContext.get().getUsername();
        log.info("Delete space requested: spaceId={}, user={}", spaceId, username);

        // 1. 查询空间详情用于确认展示
        String spaceDetail = logAgentApiService.getSpaceById(spaceId);

        // 2. 注册确认请求
        String token = confirmationManager.register(
                username,
                "deleteLogSpace",
                Map.of("spaceId", spaceId)
        );

        // 3. 构造确认消息
        ConfirmationMessage confirmMsg = ConfirmationMessage.builder()
                .token(token)
                .operation("deleteLogSpace")
                .message(String.format("确认删除 LogSpace (ID: %d)？\n此操作不可恢复，将同时删除其中所有的 stores 和 tails。", spaceId))
                .detail(Map.of(
                        "spaceId", spaceId,
                        "spaceDetail", spaceDetail
                ))
                .expireTime(System.currentTimeMillis() + CONFIRM_TIMEOUT_SECONDS * 1000)
                .build();

        // 4. 发送确认消息到前端（直接使用 this.sink，不依赖 ThreadLocal）
        sendConfirmation(gson.toJson(confirmMsg));

        // 5. 第一次检查：阻塞等待用户确认（虚拟线程挂起）
        boolean confirmed = confirmationManager.waitForConfirmation(token, CONFIRM_TIMEOUT_SECONDS);

        if (!confirmed) {
            log.info("User cancelled or timeout: spaceId={}", spaceId);
            return "操作已取消";
        }

        // 6. 第二次检查：获取执行权（双重检查锁）
        if (!confirmationManager.tryAcquireExecution(token)) {
            log.error("Failed to acquire execution: token={}, spaceId={}", token, spaceId);
            return "操作状态异常，已取消";
        }

        // 7. 执行删除（只有获取执行权的线程才能到达这里）
        log.info("Executing delete space: spaceId={}, user={}", spaceId, username);
        SpaceInfo spaceInfo = new SpaceInfo();
        spaceInfo.setSpaceId(spaceId);
        spaceInfo.setUserInfo(getCurrentUserInfo());
        String result = logAgentApiService.deleteSpace(spaceInfo);
        return "删除成功: " + result;
    }

    /**
     * 发送确认消息到前端
     */
    private void sendConfirmation(String confirmJson) {
        if (sink != null) {
            sink.next("##CONFIRM##" + confirmJson);
        }
    }

    // ... 其他辅助方法
}
```

### 6.5 SwarmExecutor 改造

```java
@Slf4j
@Service
public class SwarmExecutor {

    @Resource
    private ConfirmationManager confirmationManager;
    
    @Resource
    private LogAgentApiService logAgentApiService;
    
    @Resource
    private AlertAgentApiService alertAgentApiService;

    public Flux<String> execute(String username, String userMessage, 
                                 SwarmSession session, AutoContextMemory memory) {
        return Flux.create(sink -> {
            try {
                UserContext.set(new UserContext.UserInfo(username, 0));
                
                // 为每个请求创建独立的 Tool 实例，注入 sink
                List<Object> requestScopedTools = createRequestScopedTools(sink);
                
                executeInternal(username, userMessage, session, memory, 
                               sink, requestScopedTools, new HashSet<>(), 0);
            } catch (Exception e) {
                log.error("Execution failed for user: {}", username, e);
                sink.error(e);
            } finally {
                UserContext.clear();
            }
        });
    }
    
    /**
     * 创建请求级别的 Tool 实例
     * 每个请求独立创建，注入该请求的 FluxSink
     */
    private List<Object> createRequestScopedTools(FluxSink<String> sink) {
        return List.of(
            // 危险操作工具 - 需要确认
            new DeleteLogSpaceTool(sink, confirmationManager, logAgentApiService),
            new DeleteAlertRuleTool(sink, confirmationManager, alertAgentApiService),
            
            // 安全操作工具 - 不需要确认，可以是单例
            // queryLogSpaceTool,  // 可以注入单例
            // listAlertRulesTool  // 可以注入单例
        );
    }
    
    private void executeInternal(String username, String userMessage,
                                  SwarmSession session, AutoContextMemory memory,
                                  FluxSink<String> sink, 
                                  List<Object> requestScopedTools,  // 传入请求级工具
                                  Set<String> visitedAgents, int depth) {
        // ...
        
        HeraAgent agentDef = agentRegistry.getAgent(currentAgentName);
        
        // 使用请求级工具创建 Agent
        ReActAgent agent = agentDef.createReActAgentWithTools(
            model, memory, hook, requestScopedTools
        );
        
        // ... 执行 Agent
    }
}
```

**HeraAgent 接口扩展**：

```java
public interface HeraAgent {
    String getName();
    String getSystemPrompt();
    Toolkit getToolkit();  // 原有：返回单例 Toolkit
    
    /**
     * 创建带有请求级工具的 ReActAgent
     * 
     * @param requestScopedTools 请求级别的工具实例（每个请求独立创建）
     */
    default ReActAgent createReActAgentWithTools(
            Model model, 
            AutoContextMemory memory, 
            StreamingHook hook,
            List<Object> requestScopedTools) {
        
        // 合并单例工具和请求级工具
        Toolkit toolkit = Toolkit.builder()
            .tools(getToolkit().getTools())           // 单例工具（查询类）
            .tools(requestScopedTools)                // 请求级工具（危险操作类）
            .build();
        
        return ReActAgent.builder()
            .name(getName())
            .sysPrompt(getSystemPrompt())
            .model(model)
            .toolkit(toolkit)
            .memory(memory)
            .build();
    }
}
```

### 6.6 ConfirmController

```java
package org.apache.ozhera.mind.server.controller;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.Result;
import org.apache.ozhera.mind.service.confirmation.ConfirmationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping("/worker/confirm")
public class ConfirmController {

    @Resource
    private ConfirmationManager confirmationManager;

    @PostMapping
    public Result<Boolean> confirm(@RequestBody ConfirmRequest request) {
        log.info("Received confirmation: token={}, user={}, confirmed={}",
                request.getToken(), request.getUsername(), request.isConfirmed());

        boolean success = confirmationManager.confirm(
                request.getToken(),
                request.getUsername(),
                request.isConfirmed()
        );

        if (success) {
            return Result.success(true);
        } else {
            return Result.fail("确认已过期或不存在");
        }
    }

    @Data
    public static class ConfirmRequest {
        private String token;
        private String username;
        private boolean confirmed;
    }
}
```

### 6.7 前端处理示例

```typescript
// SSE 消息处理
eventSource.onmessage = (event) => {
    const data = event.data;

    // 检测确认消息
    if (data.startsWith('##CONFIRM##')) {
        const confirmJson = data.substring(11);
        const confirmation = JSON.parse(confirmJson);

        // 显示确认对话框
        showConfirmDialog({
            message: confirmation.message,
            detail: confirmation.detail,
            expireTime: confirmation.expireTime,
            onConfirm: async () => {
                await fetch('/worker/confirm', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        token: confirmation.token,
                        username: currentUser.username,
                        confirmed: true
                    })
                });
            },
            onCancel: async () => {
                await fetch('/worker/confirm', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        token: confirmation.token,
                        username: currentUser.username,
                        confirmed: false
                    })
                });
            }
        });
        return;
    }

    // 心跳消息
    if (data === '##HEARTBEAT##') {
        return;
    }

    // 正常消息处理
    appendMessage(data);
};
```

---

## 7. 配置建议

### 7.1 超时配置

```yaml
# application.yml
hera:
  mind:
    confirmation:
      # 默认超时（适用于快速确认场景）
      timeout-seconds: 300              # 5分钟
      # 长等待超时（适用于需要用户思考的场景）
      # timeout-seconds: 1800           # 30分钟
      cleanup-interval-seconds: 60      # 过期清理间隔
      heartbeat-interval-seconds: 15    # SSE心跳间隔
```

**超时时间选择建议**：
| 场景 | 推荐超时 | 说明 |
|-----|---------|------|
| 简单确认（删除单个资源） | 5分钟 | 用户快速决策 |
| 复杂确认（批量删除） | 15分钟 | 用户需要核对 |
| 需要审批的操作 | 30分钟 | 可能需要找人确认 |

### 7.2 Nginx 配置

```nginx
location /worker/agent/chat/stream {
    proxy_pass http://worker-service;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    
    # 长连接超时（应 >= 应用层超时配置）
    # 默认配置
    proxy_read_timeout 300s;
    proxy_send_timeout 300s;
    
    # 长等待配置（如需支持30分钟等待）
    # proxy_read_timeout 1800s;
    # proxy_send_timeout 1800s;
}
```

**注意**：Nginx 超时必须 >= 应用层超时，否则连接会被 Nginx 提前断开。

### 7.3 虚拟线程配置

```java
@Configuration
public class VirtualThreadConfig {

    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
```

---

## 8. 测试用例

### 8.1 正常确认流程

```
输入: "删除日志空间 test-space"
预期:
  1. 收到确认消息，显示确认按钮
  2. 点击确认
  3. 返回 "删除成功"
```

### 8.2 取消操作

```
输入: "删除日志空间 test-space"
预期:
  1. 收到确认消息，显示确认按钮
  2. 点击取消
  3. 返回 "操作已取消"
```

### 8.3 超时取消

```
输入: "删除日志空间 test-space"
预期:
  1. 收到确认消息，显示确认按钮
  2. 等待超时（5分钟）
  3. 返回 "操作已取消"
```

### 8.4 复合任务

```
输入: "删除日志空间A，然后新建日志空间B"
预期:
  1. 收到删除A的确认消息
  2. 点击确认
  3. 删除A成功
  4. 自动继续创建B（无需确认）
  5. 返回 "已完成：1. 删除A ✓ 2. 新建B ✓"
```

---

## 9. 总结

### 9.1 方案优势

1. **代码层面强制确认**：不依赖 LLM 理解
2. **流程连续**：复合任务自动继续执行
3. **对 Agent 透明**：不需要修改框架
4. **资源高效**：虚拟线程阻塞几乎零成本
5. **安全可靠**：Token 验证、超时处理、用户绑定

### 9.2 适用场景

- 删除操作（删除空间、删除配置等）
- 批量操作（批量删除、批量修改）
- 危险操作（重置配置、清理数据等）
- 需要用户确认的敏感操作

### 9.3 后续优化方向

1. **批量确认**：多个危险操作一次性确认
2. **确认级别**：区分警告级别（如红色高危、黄色警告）
3. **审计日志**：记录所有确认操作
4. **权限控制**：某些操作需要特定角色确认
