# Hera Mind Multi-Agent 技术实现方案

## 文档信息

| 项目 | 内容 |
|-----|------|
| 项目名称 | Hera Mind Multi-Agent 系统 |
| 版本 | v1.1 |
| 日期 | 2026-04 |
| 状态 | 实现完成 |

---

## 目录

1. [概述](#1-概述)
2. [整体架构](#2-整体架构)
3. [核心模块设计](#3-核心模块设计)
4. [Swarm Multi-Agent 实现](#4-swarm-multi-agent-实现)
5. [高并发方案](#5-高并发方案)
6. [多模态处理](#6-多模态处理)
7. [前端交互协议](#7-前端交互协议)
8. [数据存储设计](#8-数据存储设计)
9. [配置管理](#9-配置管理)
10. [潜在问题与解决方案](#10-潜在问题与解决方案)
11. [实施计划](#11-实施计划)

---

## 1. 概述

### 1.1 背景

当前 Hera Mind 系统仅支持单一的 LogAgent，随着可观测平台功能扩展，需要支持多个专业领域的 Agent（Log、Trace、Monitor 等）协同工作，以提供更全面的智能运维能力。

### 1.2 目标

- 构建去中心化的 Swarm-like Multi-Agent 架构
- 支持 Agent 间通过 Handoff 机制自主协作
- 实现高并发处理能力（虚拟线程 + WebFlux）
- 支持多模态交互（文本 + 图片）
- 提供结构化的前端交互协议

### 1.3 设计原则

| 原则 | 说明 |
|-----|------|
| **去中心化** | 无中心调度器，Agent 自主决策 Handoff |
| **可扩展** | 新增 Agent 仅需注册，无需修改核心逻辑 |
| **高性能** | 虚拟线程 + WebFlux，支持高并发 |
| **内存友好** | Memory 共享，图片外部存储 |
| **前后端解耦** | 结构化响应协议，前端灵活渲染 |

---

## 2. 整体架构

### 2.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Hera Mind System                                │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                           Gateway Layer                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │   认证鉴权   │  │  请求路由   │  │  负载均衡   │  │  限流熔断   │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                           Worker Layer                                 │  │
│  │                                                                        │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │  │
│  │  │                    Concurrency Layer                              │ │  │
│  │  │         (虚拟线程 + WebFlux + 并发控制)                           │ │  │
│  │  └──────────────────────────────────────────────────────────────────┘ │  │
│  │                                │                                       │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │  │
│  │  │                    Swarm Agent Layer                              │ │  │
│  │  │                                                                   │ │  │
│  │  │                    ┌─────────────────┐                           │ │  │
│  │  │                    │   CommonAgent   │ ← 默认入口                 │ │  │
│  │  │                    │   (闲聊/引导)    │                           │ │  │
│  │  │                    └────────┬────────┘                           │ │  │
│  │  │                             │ Handoff                             │ │  │
│  │  │           ┌─────────────────┼─────────────────┐                  │ │  │
│  │  │           ▼                 ▼                 ▼                  │ │  │
│  │  │    ┌───────────┐     ┌───────────┐     ┌───────────┐            │ │  │
│  │  │    │ LogAgent  │◄───►│TraceAgent │◄───►│MonitorAgent│            │ │  │
│  │  │    │           │     │           │     │            │            │ │  │
│  │  │    │ LogTools  │     │TraceTools │     │MonitorTools│            │ │  │
│  │  │    └───────────┘     └───────────┘     └────────────┘            │ │  │
│  │  │                                                                   │ │  │
│  │  └──────────────────────────────────────────────────────────────────┘ │  │
│  │                                │                                       │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐ │  │
│  │  │                    Multi-Modal Layer                              │ │  │
│  │  │              (图片处理 / 存储 / 传输策略)                          │ │  │
│  │  └──────────────────────────────────────────────────────────────────┘ │  │
│  │                                                                        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                      │                                      │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                           Storage Layer                                │  │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │  │
│  │  │  Caffeine   │  │    Redis    │  │    MySQL    │  │  OSS/MinIO  │  │  │
│  │  │  (热缓存)   │  │  (分布式)   │  │  (持久化)   │  │  (图片存储)  │  │  │
│  │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 模块职责

| 层级 | 模块 | 职责 |
|-----|------|------|
| Gateway | 认证鉴权 | 用户身份验证、权限校验 |
| Gateway | 请求路由 | 将请求路由到对应 Worker |
| Gateway | 负载均衡 | Worker 实例间负载均衡 |
| Gateway | 限流熔断 | 全局限流、服务熔断保护 |
| Worker | 并发控制 | 虚拟线程、并发限制、用户锁 |
| Worker | Swarm Agent | Multi-Agent 协作、Handoff |
| Worker | 多模态处理 | 图片上传、存储、传输 |
| Storage | Caffeine | JVM 内热缓存 |
| Storage | Redis | 分布式缓存、状态持久化 |
| Storage | MySQL | 对话历史、配置持久化 |
| Storage | OSS/MinIO | 图片文件存储 |

### 2.3 技术栈

| 类别 | 技术 | 版本 |
|-----|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.2+ |
| 响应式 | WebFlux | - |
| 并发 | Virtual Threads | Java 21 |
| Agent | AgentScope | 1.0.9 |
| 缓存 | Caffeine | 3.1.8 |
| 分布式缓存 | Redis | 7.x |
| 数据库 | MySQL | 8.x |
| ORM | MyBatis-Flex | 1.9.3 |
| 配置中心 | Nacos | 2.x |
| 对象存储 | OSS / MinIO | - |

---

## 3. 核心模块设计

### 3.1 目录结构

```
ozhera-mind-worker-service/src/main/java/org/apache/ozhera/mind/service/
│
├── swarm/                                    # Swarm Multi-Agent 核心
│   ├── SwarmAgentService.java               # 主服务入口
│   ├── SwarmExecutor.java                   # 执行引擎
│   ├── SwarmContext.java                    # Handoff 上下文
│   ├── SwarmSession.java                    # 会话状态
│   ├── HandoffResult.java                   # Handoff 结果
│   ├── HeraAgent.java                       # Agent 接口
│   ├── AgentRegistry.java                   # Agent 注册表
│   │
│   ├── agents/                              # Agent 实现
│   │   ├── AbstractHeraAgent.java           # Agent 基类
│   │   ├── CommonAgent.java                 # 通用 Agent（默认入口）
│   │   ├── LogAgent.java                    # 日志 Agent
│   │   ├── TraceAgent.java                  # 链路 Agent
│   │   └── MonitorAgent.java                # 监控 Agent
│   │
│   └── handoffs/                            # Handoff Tools
│       ├── CommonAgentHandoffs.java
│       ├── LogAgentHandoffs.java
│       ├── TraceAgentHandoffs.java
│       └── MonitorAgentHandoffs.java
│
├── concurrency/                             # 高并发控制
│   ├── VirtualThreadConfig.java             # 虚拟线程配置
│   ├── ConcurrencyLimiter.java              # 并发限制器
│   ├── UserLockManager.java                 # 用户级锁管理
│   └── LLMRateLimiter.java                  # LLM API 限流
│
├── multimodal/                              # 多模态处理
│   ├── MultiModalService.java               # 多模态服务
│   ├── ImageStorageService.java             # 图片存储接口
│   ├── ImageTransferStrategy.java           # 图片传输策略接口
│   ├── ImagePreprocessor.java               # 图片预处理接口
│   │
│   ├── impl/                                # 默认实现
│   │   ├── OssImageStorageService.java      # OSS 存储
│   │   ├── UrlImageTransferStrategy.java    # URL 传输（默认，优先）
│   │   ├── Base64ImageTransferStrategy.java # Base64 传输（降级）
│   │   └── DefaultImagePreprocessor.java    # 默认预处理
│   │
│   └── model/                               # 数据模型
│       ├── ImageRefContent.java             # 图片引用
│       └── ContentBlock.java                # 内容块接口
│
├── response/                                # 响应格式
│   ├── ResponseBlock.java                   # 响应块接口
│   ├── TextBlock.java                       # 文本块
│   ├── DataBlock.java                       # 数据块
│   ├── ActionBlock.java                     # 动作块
│   ├── ToolResult.java                      # Tool 返回结果
│   └── StreamingResponseFormatter.java      # 流式响应格式化
│
├── llm/tool/                                # 业务 Tools
│   ├── LogToolService.java                  # 日志工具
│   ├── TraceToolService.java                # 链路工具
│   └── MonitorToolService.java              # 监控工具
│
└── ...（其他现有代码）
```

### 3.2 核心接口定义

#### 3.2.1 HeraAgent 接口

```java
package org.apache.ozhera.mind.service.swarm;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;

import java.util.List;

/**
 * Hera Agent 接口
 * 所有专业 Agent 需实现此接口
 */
public interface HeraAgent {
    
    /**
     * Agent 名称（唯一标识）
     */
    String getName();
    
    /**
     * Agent 描述
     */
    String getDescription();
    
    /**
     * System Prompt
     */
    String getSystemPrompt();
    
    /**
     * 获取该 Agent 的工具集（业务工具 + Handoff 工具）
     */
    Toolkit getToolkit();
    
    /**
     * 可以 Handoff 到的目标 Agent 列表
     */
    List<String> getHandoffTargets();
    
    /**
     * 创建 AgentScope ReActAgent 实例
     */
    ReActAgent createReActAgent(Model model, AutoContextMemory memory, Hook hook);
    
    /**
     * 是否为默认入口 Agent
     */
    default boolean isDefaultEntry() {
        return false;
    }
}
```

#### 3.2.2 SwarmContext

```java
package org.apache.ozhera.mind.service.swarm;

import lombok.Data;
import java.util.*;

/**
 * Swarm 执行上下文
 * 在 Handoff 过程中传递，记录协作历史
 */
@Data
public class SwarmContext {
    
    /**
     * 会话 ID
     */
    private String sessionId;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 原始用户问题
     */
    private String originalQuery;
    
    /**
     * Handoff 链路记录
     */
    private List<HandoffRecord> handoffChain = new ArrayList<>();
    
    /**
     * 已访问的 Agent（防循环）
     */
    private Set<String> visitedAgents = new LinkedHashSet<>();
    
    /**
     * Agent 间共享的信息
     */
    private Map<String, Object> gatheredInfo = new HashMap<>();
    
    /**
     * 最大 Handoff 深度
     */
    private int maxHandoffDepth = 3;
    
    /**
     * 检查是否可以 Handoff 到目标 Agent
     */
    public boolean canHandoffTo(String targetAgent) {
        // 防止循环：同一个 Agent 不能访问两次
        if (visitedAgents.contains(targetAgent)) {
            return false;
        }
        // 防止过深
        return handoffChain.size() < maxHandoffDepth;
    }
    
    /**
     * 记录 Handoff
     */
    public void recordHandoff(String from, String to, String reason, String info) {
        visitedAgents.add(to);
        handoffChain.add(new HandoffRecord(from, to, reason, System.currentTimeMillis()));
        if (info != null && !info.isEmpty()) {
            gatheredInfo.put(from + "_info", info);
        }
    }
    
    /**
     * 生成给下一个 Agent 的上下文摘要
     */
    public String getHandoffSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("【原始问题】\n").append(originalQuery).append("\n\n");
        
        if (!handoffChain.isEmpty()) {
            sb.append("【处理链路】\n");
            for (HandoffRecord record : handoffChain) {
                sb.append("- ").append(record.getFrom())
                  .append(" → ").append(record.getTo())
                  .append(" (原因: ").append(record.getReason()).append(")\n");
            }
            sb.append("\n");
        }
        
        if (!gatheredInfo.isEmpty()) {
            sb.append("【已收集信息】\n");
            gatheredInfo.forEach((k, v) -> 
                sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        
        return sb.toString();
    }
    
    /**
     * Handoff 记录
     */
    @Data
    @AllArgsConstructor
    public static class HandoffRecord {
        private String from;
        private String to;
        private String reason;
        private long timestamp;
    }
}
```

#### 3.2.3 SwarmSession

```java
package org.apache.ozhera.mind.service.swarm;

import lombok.Data;
import java.util.*;

/**
 * Swarm 会话状态
 * 记录用户当前所在的 Agent
 */
@Data
public class SwarmSession {
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 当前活跃的 Agent 名称
     */
    private String currentAgentName;
    
    /**
     * Agent 切换历史
     */
    private List<String> agentHistory = new ArrayList<>();
    
    /**
     * 会话创建时间
     */
    private long createTime;
    
    /**
     * 最后活跃时间
     */
    private long lastActiveTime;
    
    /**
     * Handoff 临时上下文（跨请求保持）
     */
    private Map<String, Object> handoffContext = new HashMap<>();
    
    /**
     * 更新当前 Agent
     */
    public void switchAgent(String agentName) {
        this.currentAgentName = agentName;
        this.agentHistory.add(agentName);
        this.lastActiveTime = System.currentTimeMillis();
    }
    
    /**
     * 创建新会话
     */
    public static SwarmSession create(String username) {
        SwarmSession session = new SwarmSession();
        session.setUsername(username);
        session.setCurrentAgentName("CommonAgent");
        session.setCreateTime(System.currentTimeMillis());
        session.setLastActiveTime(System.currentTimeMillis());
        session.getAgentHistory().add("CommonAgent");
        return session;
    }
}
```

#### 3.2.4 HandoffResult

```java
package org.apache.ozhera.mind.service.swarm;

import lombok.Builder;
import lombok.Data;

/**
 * Handoff 结果
 * Tool 返回此对象表示需要移交给其他 Agent
 */
@Data
@Builder
public class HandoffResult {
    
    /**
     * 目标 Agent 名称
     */
    private String targetAgent;
    
    /**
     * 移交原因
     */
    private String reason;
    
    /**
     * 当前 Agent 收集的信息
     */
    private String gatheredInfo;
    
    /**
     * 希望目标 Agent 回答的具体问题
     */
    private String specificQuestion;
    
    /**
     * 构建器快捷方法
     */
    public static HandoffResultBuilder to(String targetAgent) {
        return HandoffResult.builder().targetAgent(targetAgent);
    }
}
```

---

## 4. Swarm Multi-Agent 实现

### 4.1 设计理念

采用 **去中心化 Swarm 架构**，核心特点：

1. **无中心调度器**：每个 Agent 地位平等，自主决策是否 Handoff
2. **CommonAgent 为默认入口**：处理闲聊、引导，按需移交给专业 Agent
3. **LLM 自主决定 Handoff**：通过 Handoff Tool，LLM 判断是否需要移交
4. **Memory 共享**：所有 Agent 共享同一用户的对话历史
5. **只缓存当前 Agent**：Handoff 时替换缓存的 Agent 实例

### 4.2 Agent 关系图

```
                      ┌─────────────────┐
                      │   CommonAgent   │
                      │   (默认入口)     │
                      │                 │
                      │  职责:          │
                      │  • 闲聊对话      │
                      │  • 平台介绍      │
                      │  • 引导使用      │
                      └────────┬────────┘
                               │
             ┌─────────────────┼─────────────────┐
             │                 │                 │
             ▼                 ▼                 ▼
      ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
      │  LogAgent   │   │ TraceAgent  │   │MonitorAgent │
      │             │   │             │   │             │
      │  职责:      │   │  职责:      │   │  职责:      │
      │  • 日志空间  │   │  • 链路追踪  │   │  • 指标监控  │
      │  • 日志存储  │   │  • Span 分析 │   │  • 告警规则  │
      │  • 日志采集  │   │  • 依赖拓扑  │   │  • 监控大盘  │
      └─────────────┘   └─────────────┘   └─────────────┘
             │                 │                 │
             └─────────────────┴─────────────────┘
                    (互相可以 Handoff)
```

### 4.3 Agent 注册表实现

```java
package org.apache.ozhera.mind.service.swarm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 注册表
 * 管理所有 Agent 定义
 */
@Slf4j
@Component
public class AgentRegistry {
    
    private final Map<String, HeraAgent> agents = new ConcurrentHashMap<>();
    
    private HeraAgent defaultAgent;
    
    @Resource
    private List<HeraAgent> heraAgents;
    
    @PostConstruct
    public void init() {
        for (HeraAgent agent : heraAgents) {
            register(agent);
            if (agent.isDefaultEntry()) {
                defaultAgent = agent;
            }
        }
        
        if (defaultAgent == null && !agents.isEmpty()) {
            defaultAgent = agents.get("CommonAgent");
        }
        
        log.info("Registered {} agents: {}, default: {}", 
            agents.size(), agents.keySet(), 
            defaultAgent != null ? defaultAgent.getName() : "none");
    }
    
    /**
     * 注册 Agent
     */
    public void register(HeraAgent agent) {
        agents.put(agent.getName(), agent);
        log.info("Registered agent: {} - {}", agent.getName(), agent.getDescription());
    }
    
    /**
     * 获取 Agent
     */
    public HeraAgent getAgent(String name) {
        HeraAgent agent = agents.get(name);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown agent: " + name);
        }
        return agent;
    }
    
    /**
     * 获取默认入口 Agent
     */
    public HeraAgent getDefaultAgent() {
        return defaultAgent;
    }
    
    /**
     * 获取所有 Agent
     */
    public Collection<HeraAgent> getAllAgents() {
        return Collections.unmodifiableCollection(agents.values());
    }
    
    /**
     * 检查 Agent 是否存在
     */
    public boolean hasAgent(String name) {
        return agents.containsKey(name);
    }
}
```

### 4.4 CommonAgent 实现

```java
package org.apache.ozhera.mind.service.swarm.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.apache.ozhera.mind.service.swarm.HeraAgent;
import org.apache.ozhera.mind.service.swarm.handoffs.CommonAgentHandoffs;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * CommonAgent - 通用 Agent
 * 作为默认入口，处理闲聊和引导
 */
@Component
public class CommonAgent implements HeraAgent {
    
    @Resource
    private CommonAgentHandoffs handoffs;
    
    @Override
    public String getName() {
        return "CommonAgent";
    }
    
    @Override
    public String getDescription() {
        return "通用助手，处理闲聊和引导用户使用平台功能";
    }
    
    @Override
    public String getSystemPrompt() {
        return """
            你是 Hera 可观测平台的智能助手。
            
            ## 你的职责
            1. 友好地与用户闲聊
            2. 介绍 Hera 平台的功能和能力
            3. 回答关于可观测性的通用问题
            4. 引导用户使用平台的各项功能
            
            ## Hera 平台能力
            - **日志管理**：日志空间创建、日志存储配置、日志采集设置、日志搜索分析
            - **链路追踪**：Trace 分析、Span 查看、服务依赖拓扑、调用链路排查
            - **监控告警**：指标查询、告警规则配置、监控大盘管理、性能分析
            
            ## 协作规则
            当用户有**明确的操作需求**时，使用 handoff 工具移交给专业 Agent：
            - 日志相关操作（创建、配置、搜索日志）→ handoff_to_log
            - 链路追踪操作（查看 Trace、分析调用链）→ handoff_to_trace
            - 监控告警操作（配置告警、查看指标）→ handoff_to_monitor
            
            ## 注意事项
            - 如果用户只是**咨询问题**（如"日志是什么"），直接回答，不需要移交
            - 只有用户明确要**执行操作**时才移交给专业 Agent
            - 移交前简要说明即将移交给哪个 Agent
            """;
    }
    
    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(handoffs);  // 只有 Handoff 工具
        return toolkit;
    }
    
    @Override
    public List<String> getHandoffTargets() {
        return List.of("LogAgent", "TraceAgent", "MonitorAgent");
    }
    
    @Override
    public ReActAgent createReActAgent(Model model, AutoContextMemory memory, Hook hook) {
        ReActAgent.Builder builder = ReActAgent.builder()
            .name(getName())
            .sysPrompt(getSystemPrompt())
            .model(model)
            .toolkit(getToolkit())
            .memory(memory);
        
        if (hook != null) {
            builder.hook(hook);
        }
        
        return builder.build();
    }
    
    @Override
    public boolean isDefaultEntry() {
        return true;
    }
}
```

### 4.5 CommonAgent Handoff Tools

```java
package org.apache.ozhera.mind.service.swarm.handoffs;

import io.agentscope.core.tool.annotation.Tool;
import io.agentscope.core.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * CommonAgent 的 Handoff 工具
 */
@Component
public class CommonAgentHandoffs {
    
    private static final String HANDOFF_PREFIX = "__HANDOFF__:";
    
    @Tool(name = "handoff_to_log",
          description = """
            当用户需要进行日志相关操作时移交给 LogAgent。
            
            适用场景：
            - 创建、更新、删除日志空间
            - 配置日志存储
            - 设置日志采集规则
            - 搜索和分析日志内容
            
            注意：如果用户只是询问"什么是日志空间"这类问题，直接回答即可，不需要移交。
            只有用户明确要执行操作时才移交。
            """)
    public String handoffToLog(
            @ToolParam(name = "reason", description = "移交原因", required = true)
            String reason,
            @ToolParam(name = "user_intent", description = "用户的具体意图", required = true)
            String userIntent) {
        
        return formatHandoff("LogAgent", reason, userIntent);
    }
    
    @Tool(name = "handoff_to_trace",
          description = """
            当用户需要进行链路追踪相关操作时移交给 TraceAgent。
            
            适用场景：
            - 查询和分析调用链路
            - 查看 Span 详情和耗时
            - 分析服务依赖拓扑
            - 排查跨服务调用问题
            """)
    public String handoffToTrace(
            @ToolParam(name = "reason", required = true) String reason,
            @ToolParam(name = "user_intent", required = true) String userIntent) {
        
        return formatHandoff("TraceAgent", reason, userIntent);
    }
    
    @Tool(name = "handoff_to_monitor",
          description = """
            当用户需要进行监控告警相关操作时移交给 MonitorAgent。
            
            适用场景：
            - 查询监控指标数据
            - 配置告警规则
            - 管理监控大盘
            - 分析性能趋势
            """)
    public String handoffToMonitor(
            @ToolParam(name = "reason", required = true) String reason,
            @ToolParam(name = "user_intent", required = true) String userIntent) {
        
        return formatHandoff("MonitorAgent", reason, userIntent);
    }
    
    /**
     * 格式化 Handoff 返回值
     */
    private String formatHandoff(String targetAgent, String reason, String userIntent) {
        return String.format("%s{\"target\":\"%s\",\"reason\":\"%s\",\"question\":\"%s\"}",
            HANDOFF_PREFIX, targetAgent, escapeJson(reason), escapeJson(userIntent));
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r");
    }
}
```

### 4.6 LogAgent 实现

```java
package org.apache.ozhera.mind.service.swarm.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.Toolkit;
import org.apache.ozhera.mind.service.llm.tool.LogToolService;
import org.apache.ozhera.mind.service.swarm.HeraAgent;
import org.apache.ozhera.mind.service.swarm.handoffs.LogAgentHandoffs;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * LogAgent - 日志专业 Agent
 */
@Component
public class LogAgent implements HeraAgent {
    
    @Resource
    private LogToolService logToolService;
    
    @Resource
    private LogAgentHandoffs handoffs;
    
    @Override
    public String getName() {
        return "LogAgent";
    }
    
    @Override
    public String getDescription() {
        return "日志管理专家，负责日志空间、存储、采集的管理和日志分析";
    }
    
    @Override
    public String getSystemPrompt() {
        return """
            你是 Hera 可观测平台的日志管理专家。
            
            ## 你的能力
            - **日志空间管理**：创建、更新、删除、查询日志空间
            - **日志存储管理**：创建、更新、删除、查询日志存储
            - **日志采集管理**：创建、更新、删除、查询日志采集配置（Tail）
            - **日志分析**：搜索日志、分析日志模式
            
            ## 协作规则
            当用户问题涉及以下领域时，使用 handoff 工具移交给专业 Agent：
            - 链路追踪、Span 分析、调用链 → handoff_to_trace
            - 监控指标、告警规则、监控大盘 → handoff_to_monitor
            - 闲聊、通用问题 → handoff_to_common
            
            移交前，请先总结你已收集的相关信息，帮助下一个 Agent 理解上下文。
            
            ## 工作流程
            1. 理解用户需求
            2. 调用相应的工具执行操作
            3. 返回执行结果，包含结构化数据（如适用）
            4. 如需其他领域协助，执行 Handoff
            """;
    }
    
    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(logToolService);  // 业务工具
        toolkit.registerTool(handoffs);         // Handoff 工具
        return toolkit;
    }
    
    @Override
    public List<String> getHandoffTargets() {
        return List.of("CommonAgent", "TraceAgent", "MonitorAgent");
    }
    
    @Override
    public ReActAgent createReActAgent(Model model, AutoContextMemory memory, Hook hook) {
        ReActAgent.Builder builder = ReActAgent.builder()
            .name(getName())
            .sysPrompt(getSystemPrompt())
            .model(model)
            .toolkit(getToolkit())
            .memory(memory);
        
        if (hook != null) {
            builder.hook(hook);
        }
        
        return builder.build();
    }
}
```

### 4.7 LogAgent Handoff Tools

```java
package org.apache.ozhera.mind.service.swarm.handoffs;

import io.agentscope.core.tool.annotation.Tool;
import io.agentscope.core.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * LogAgent 的 Handoff 工具
 */
@Component
public class LogAgentHandoffs {
    
    private static final String HANDOFF_PREFIX = "__HANDOFF__:";
    
    @Tool(name = "handoff_to_trace",
          description = """
            当用户问题涉及以下场景时移交给 TraceAgent：
            - 需要分析调用链路
            - 需要查看 Span 详情和耗时分布
            - 排查跨服务调用问题
            - 分析服务依赖拓扑
            - 需要关联日志和 Trace
            
            移交前请总结已收集的日志相关信息。
            """)
    public String handoffToTrace(
            @ToolParam(name = "reason", description = "移交原因", required = true)
            String reason,
            @ToolParam(name = "gathered_info", description = "已收集的日志信息", required = true)
            String gatheredInfo,
            @ToolParam(name = "question", description = "希望 TraceAgent 回答的问题", required = true)
            String question) {
        
        return formatHandoff("TraceAgent", reason, gatheredInfo, question);
    }
    
    @Tool(name = "handoff_to_monitor",
          description = """
            当用户问题涉及以下场景时移交给 MonitorAgent：
            - 需要查看监控指标数据
            - 需要配置或查询告警规则
            - 需要分析性能指标趋势
            - 需要管理监控大盘
            """)
    public String handoffToMonitor(
            @ToolParam(name = "reason", required = true) String reason,
            @ToolParam(name = "gathered_info", required = true) String gatheredInfo,
            @ToolParam(name = "question", required = true) String question) {
        
        return formatHandoff("MonitorAgent", reason, gatheredInfo, question);
    }
    
    @Tool(name = "handoff_to_common",
          description = """
            当用户想要闲聊或问题与日志无关时，移交回 CommonAgent。
            
            适用场景：
            - 用户说"算了"、"不弄了"
            - 用户想聊其他话题
            - 问题与日志管理无关
            """)
    public String handoffToCommon(
            @ToolParam(name = "reason", required = true) String reason) {
        
        return formatHandoff("CommonAgent", reason, "", "");
    }
    
    private String formatHandoff(String target, String reason, String info, String question) {
        return String.format(
            "%s{\"target\":\"%s\",\"reason\":\"%s\",\"info\":\"%s\",\"question\":\"%s\"}",
            HANDOFF_PREFIX, target, escapeJson(reason), escapeJson(info), escapeJson(question));
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n");
    }
}
```

### 4.8 Swarm 执行器实现

```java
package org.apache.ozhera.mind.service.swarm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.context.UserContext;
import org.apache.ozhera.mind.service.hook.StreamingHook;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import javax.annotation.Resource;
import java.util.List;
import java.util.UUID;

/**
 * Swarm 执行器
 * 负责执行 Agent 并处理 Handoff
 */
@Slf4j
@Service
public class SwarmExecutor {
    
    private static final String HANDOFF_PREFIX = "__HANDOFF__:";
    
    @Resource
    private AgentRegistry agentRegistry;
    
    @Resource
    private ModelProviderService modelProviderService;
    
    @Resource
    private UserConfigService userConfigService;
    
    private final Gson gson = new Gson();
    
    /**
     * 执行 Agent（流式）
     */
    public Flux<String> execute(
            HeraAgent agentDef,
            String userMessage,
            SwarmContext context,
            SwarmSession session,
            AutoContextMemory memory,
            String username) {
        
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullResponse = new StringBuilder();
        StreamingHook hook = new StreamingHook(sink, fullResponse);
        
        // 获取用户 Model 配置
        var userConfig = userConfigService.getByUsername(username);
        Model model = modelProviderService.createModel(userConfig);
        
        // 创建 Agent 实例
        ReActAgent agent = agentDef.createReActAgent(model, memory, hook);
        
        // 构建输入消息
        String actualInput = buildInput(userMessage, context);
        Msg userMsg = Msg.builder()
            .name("user")
            .role(MsgRole.USER)
            .content(TextBlock.builder().text(actualInput).build())
            .build();
        
        // 执行 Agent
        UserContext.UserInfo userInfo = new UserContext.UserInfo(username, 0);
        
        agent.call(List.of(userMsg))
            .doOnSubscribe(s -> UserContext.set(userInfo))
            .doOnSuccess(response -> {
                String content = fullResponse.toString();
                
                // 检查是否是 Handoff
                HandoffResult handoff = parseHandoff(content);
                if (handoff != null && context.canHandoffTo(handoff.getTargetAgent())) {
                    handleHandoff(handoff, context, session, memory, username, sink);
                } else {
                    hook.complete();
                }
            })
            .doOnError(e -> {
                log.error("Agent {} execution failed", agentDef.getName(), e);
                hook.error(e);
            })
            .doFinally(s -> UserContext.clear())
            .subscribe();
        
        return sink.asFlux();
    }
    
    /**
     * 构建输入（添加 Handoff 上下文）
     */
    private String buildInput(String userMessage, SwarmContext context) {
        if (context.getHandoffChain().isEmpty()) {
            return userMessage;
        }
        return context.getHandoffSummary() + "\n\n【当前问题】\n" + userMessage;
    }
    
    /**
     * 处理 Handoff
     */
    private void handleHandoff(
            HandoffResult handoff,
            SwarmContext context,
            SwarmSession session,
            AutoContextMemory memory,
            String username,
            Sinks.Many<String> sink) {
        
        String from = session.getCurrentAgentName();
        String to = handoff.getTargetAgent();
        
        // 记录 Handoff
        context.recordHandoff(from, to, handoff.getReason(), handoff.getGatheredInfo());
        
        // 更新 Session
        session.switchAgent(to);
        
        // 发送 Handoff 通知
        sink.tryEmitNext(formatHandoffNotice(from, to, handoff.getReason()));
        
        // 获取目标 Agent 并继续执行
        HeraAgent nextAgentDef = agentRegistry.getAgent(to);
        String nextQuestion = handoff.getSpecificQuestion();
        if (nextQuestion == null || nextQuestion.isEmpty()) {
            nextQuestion = context.getOriginalQuery();
        }
        
        // 递归执行下一个 Agent
        execute(nextAgentDef, nextQuestion, context, session, memory, username)
            .subscribe(
                sink::tryEmitNext,
                sink::tryEmitError,
                sink::tryEmitComplete
            );
    }
    
    /**
     * 解析 Handoff 结果
     */
    private HandoffResult parseHandoff(String content) {
        if (content == null || !content.contains(HANDOFF_PREFIX)) {
            return null;
        }
        
        try {
            int start = content.indexOf(HANDOFF_PREFIX) + HANDOFF_PREFIX.length();
            int end = content.indexOf("}", start) + 1;
            String json = content.substring(start, end);
            
            JsonObject obj = gson.fromJson(json, JsonObject.class);
            return HandoffResult.builder()
                .targetAgent(getJsonString(obj, "target"))
                .reason(getJsonString(obj, "reason"))
                .gatheredInfo(getJsonString(obj, "info"))
                .specificQuestion(getJsonString(obj, "question"))
                .build();
        } catch (Exception e) {
            log.warn("Failed to parse handoff: {}", content, e);
            return null;
        }
    }
    
    private String getJsonString(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }
    
    /**
     * 格式化 Handoff 通知
     */
    private String formatHandoffNotice(String from, String to, String reason) {
        return String.format(
            "\n\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
            "🔄 正在移交: %s → %s\n" +
            "原因: %s\n" +
            "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n",
            from, to, reason);
    }
}
```

### 4.9 Swarm Agent Service（主服务）

```java
package org.apache.ozhera.mind.service.swarm;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.model.Model;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.api.dto.ChatRequest;
import org.apache.ozhera.mind.service.concurrency.ConcurrencyLimiter;
import org.apache.ozhera.mind.service.concurrency.LLMRateLimiter;
import org.apache.ozhera.mind.service.concurrency.UserLockManager;
import org.apache.ozhera.mind.service.llm.provider.ModelProviderService;
import org.apache.ozhera.mind.service.service.ChatMessageService;
import org.apache.ozhera.mind.service.service.MemoryStateService;
import org.apache.ozhera.mind.service.service.UserConfigService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Swarm Agent 主服务
 * 替代原有的 AgentServiceImpl
 */
@Slf4j
@Service
public class SwarmAgentService {
    
    // ==================== 配置 ====================
    
    @NacosValue(value = "${agent.cache.expire-minutes:15}", autoRefreshed = true)
    private int cacheExpireMinutes;
    
    @NacosValue(value = "${agent.cache.max-size:500}", autoRefreshed = true)
    private int cacheMaxSize;
    
    @NacosValue(value = "${agent.memory.msg-threshold:20}", autoRefreshed = true)
    private int msgThreshold;
    
    @NacosValue(value = "${agent.memory.max-token:128000}", autoRefreshed = true)
    private int maxToken;
    
    @NacosValue(value = "${agent.memory.token-ratio:0.8}", autoRefreshed = true)
    private double tokenRatio;
    
    @NacosValue(value = "${agent.memory.last-keep:5}", autoRefreshed = true)
    private int lastKeep;
    
    @NacosValue(value = "${agent.swarm.max-handoff-depth:3}", autoRefreshed = true)
    private int maxHandoffDepth;
    
    // ==================== 依赖 ====================
    
    @Resource
    private AgentRegistry agentRegistry;
    
    @Resource
    private SwarmExecutor swarmExecutor;
    
    @Resource
    private ModelProviderService modelProviderService;
    
    @Resource
    private UserConfigService userConfigService;
    
    @Resource
    private MemoryStateService memoryStateService;
    
    @Resource
    private ChatMessageService chatMessageService;
    
    @Resource
    private ConcurrencyLimiter concurrencyLimiter;
    
    @Resource
    private UserLockManager userLockManager;
    
    @Resource
    private LLMRateLimiter llmRateLimiter;
    
    // ==================== 缓存 ====================
    
    /**
     * Memory 缓存：每用户一个，所有 Agent 共享
     */
    private Cache<String, AutoContextMemory> memoryCache;
    
    /**
     * Session 缓存：记录当前 Agent 状态
     */
    private Cache<String, SwarmSession> sessionCache;
    
    /**
     * Agent 实例缓存：当前活跃的 Agent
     */
    private Cache<String, ReActAgent> agentCache;
    
    /**
     * 虚拟线程执行器
     */
    private ExecutorService virtualExecutor;
    
    @PostConstruct
    public void init() {
        log.info("Initializing SwarmAgentService with maxSize={}, expireMinutes={}", 
            cacheMaxSize, cacheExpireMinutes);
        
        // 虚拟线程执行器
        virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        
        // Memory 缓存
        memoryCache = Caffeine.newBuilder()
            .maximumSize(cacheMaxSize)
            .expireAfterAccess(cacheExpireMinutes, TimeUnit.MINUTES)
            .scheduler(Scheduler.systemScheduler())
            .removalListener((String username, AutoContextMemory memory, RemovalCause cause) -> {
                if (cause.wasEvicted() && memory != null) {
                    log.info("Memory evicted, saving state for user: {}", username);
                    memoryStateService.saveState(username, memory);
                }
            })
            .build();
        
        // Session 缓存
        sessionCache = Caffeine.newBuilder()
            .maximumSize(cacheMaxSize)
            .expireAfterAccess(cacheExpireMinutes, TimeUnit.MINUTES)
            .build();
        
        // Agent 实例缓存
        agentCache = Caffeine.newBuilder()
            .maximumSize(cacheMaxSize)
            .expireAfterAccess(cacheExpireMinutes, TimeUnit.MINUTES)
            .build();
    }
    
    /**
     * 流式聊天
     */
    public Flux<String> chatStream(ChatRequest request) {
        String username = request.getUsername();
        String userMessage = request.getMessage();
        
        return Flux.create(sink -> {
            virtualExecutor.submit(() -> {
                try {
                    // 1. 全局并发限制
                    if (!concurrencyLimiter.tryAcquire(10, TimeUnit.SECONDS)) {
                        sink.next("服务繁忙，请稍后再试");
                        sink.complete();
                        return;
                    }
                    
                    // 2. 用户级锁
                    if (!userLockManager.tryLock(username, 30, TimeUnit.SECONDS)) {
                        sink.next("您有请求正在处理中，请稍候");
                        sink.complete();
                        concurrencyLimiter.release();
                        return;
                    }
                    
                    try {
                        // 3. LLM API 限流
                        var userConfig = userConfigService.getByUsername(username);
                        llmRateLimiter.acquire(userConfig.getProvider());
                        
                        // 4. 执行聊天
                        processChat(username, userMessage, sink);
                        
                    } finally {
                        userLockManager.unlock(username);
                        concurrencyLimiter.release();
                    }
                    
                } catch (Exception e) {
                    log.error("Chat failed for user: {}", username, e);
                    sink.next("处理出错: " + e.getMessage());
                    sink.complete();
                }
            });
        });
    }
    
    /**
     * 处理聊天（同步代码，在虚拟线程中执行）
     */
    private void processChat(String username, String userMessage, 
                             reactor.core.publisher.FluxSink<String> sink) {
        
        // 1. 获取或创建 Memory（共享）
        AutoContextMemory memory = getOrCreateMemory(username);
        
        // 2. 获取或创建 Session
        SwarmSession session = getOrCreateSession(username);
        
        // 3. 创建执行上下文
        SwarmContext context = new SwarmContext();
        context.setSessionId(UUID.randomUUID().toString());
        context.setUsername(username);
        context.setOriginalQuery(userMessage);
        context.setMaxHandoffDepth(maxHandoffDepth);
        context.getVisitedAgents().add(session.getCurrentAgentName());
        
        // 4. 获取当前 Agent
        HeraAgent agentDef = agentRegistry.getAgent(session.getCurrentAgentName());
        
        // 5. 执行 Agent（会处理 Handoff）
        swarmExecutor.execute(agentDef, userMessage, context, session, memory, username)
            .doOnNext(content -> {
                sink.next(content);
            })
            .doOnComplete(() -> {
                // 保存消息历史
                chatMessageService.saveMessage(username, "USER", userMessage);
                // Memory 状态会在缓存驱逐时自动保存
                sink.complete();
            })
            .doOnError(e -> {
                log.error("Chat execution failed", e);
                sink.error(e);
            })
            .subscribe();
    }
    
    /**
     * 获取或创建 Memory
     */
    private AutoContextMemory getOrCreateMemory(String username) {
        return memoryCache.get(username, k -> {
            var userConfig = userConfigService.getByUsername(username);
            Model model = modelProviderService.createModel(userConfig);
            
            AutoContextConfig config = AutoContextConfig.builder()
                .msgThreshold(msgThreshold)
                .maxToken(maxToken)
                .tokenRatio(tokenRatio)
                .lastKeep(lastKeep)
                .build();
            
            AutoContextMemory memory = new AutoContextMemory(config, model);
            
            // 尝试从 Redis 恢复
            memoryStateService.loadState(username, memory);
            
            return memory;
        });
    }
    
    /**
     * 获取或创建 Session
     */
    private SwarmSession getOrCreateSession(String username) {
        return sessionCache.get(username, k -> SwarmSession.create(username));
    }
    
    /**
     * 使缓存失效（用户更新配置时调用）
     */
    public void invalidateCache(String username) {
        log.info("Invalidating cache for user: {}", username);
        
        AutoContextMemory memory = memoryCache.getIfPresent(username);
        if (memory != null) {
            memoryStateService.saveState(username, memory);
        }
        
        memoryCache.invalidate(username);
        sessionCache.invalidate(username);
        agentCache.invalidate(username);
    }
}
```

### 4.10 请求处理流程

```
用户发送消息: "帮我创建一个日志空间叫 test-space"
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    SwarmAgentService.chatStream()               │
│                                                                 │
│  1. 提交到虚拟线程执行器                                         │
│     virtualExecutor.submit(() -> { ... })                       │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                       并发控制                                   │
│                                                                 │
│  2. 全局并发限制检查                                             │
│     concurrencyLimiter.tryAcquire()                             │
│                                                                 │
│  3. 用户级锁获取                                                 │
│     userLockManager.tryLock(username)                           │
│                                                                 │
│  4. LLM API 限流                                                │
│     llmRateLimiter.acquire(provider)                            │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    资源获取                                      │
│                                                                 │
│  5. 获取/创建 Memory（共享）                                     │
│     memoryCache.get(username, ...)                              │
│                                                                 │
│  6. 获取/创建 Session                                           │
│     sessionCache.get(username, ...)                             │
│     session.currentAgent = "CommonAgent"（新会话）               │
│                                                                 │
│  7. 创建执行上下文 SwarmContext                                  │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                 SwarmExecutor.execute()                         │
│                                                                 │
│  8. 获取当前 Agent 定义                                          │
│     agentRegistry.getAgent("CommonAgent")                       │
│                                                                 │
│  9. 创建 Agent 实例                                              │
│     agentDef.createReActAgent(model, memory, hook)              │
│                                                                 │
│  10. 执行 Agent                                                 │
│      agent.call(userMsg)                                        │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Agent 执行                                    │
│                                                                 │
│  CommonAgent 接收消息: "帮我创建一个日志空间叫 test-space"        │
│                                                                 │
│  LLM 思考: 用户要创建日志空间，这是日志操作，应该移交给 LogAgent  │
│                                                                 │
│  调用 Tool: handoff_to_log(                                     │
│      reason="用户需要创建日志空间",                              │
│      user_intent="创建名为 test-space 的日志空间"                │
│  )                                                              │
│                                                                 │
│  返回: __HANDOFF__:{"target":"LogAgent",...}                    │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Handoff 处理                                  │
│                                                                 │
│  11. 解析 Handoff 结果                                          │
│      parseHandoff(content) → HandoffResult                      │
│                                                                 │
│  12. 检查是否可以 Handoff                                        │
│      context.canHandoffTo("LogAgent") → true                    │
│                                                                 │
│  13. 记录 Handoff                                               │
│      context.recordHandoff("CommonAgent", "LogAgent", ...)      │
│                                                                 │
│  14. 更新 Session                                               │
│      session.switchAgent("LogAgent")                            │
│                                                                 │
│  15. 发送 Handoff 通知给前端                                     │
│      sink.next("🔄 正在移交: CommonAgent → LogAgent")            │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                 递归执行 LogAgent                                │
│                                                                 │
│  16. 获取 LogAgent 定义                                         │
│      agentRegistry.getAgent("LogAgent")                         │
│                                                                 │
│  17. 创建 LogAgent 实例（共享同一个 Memory）                     │
│      logAgentDef.createReActAgent(model, memory, hook)          │
│                                                                 │
│  18. 执行 LogAgent                                              │
│      输入包含 Handoff 上下文摘要                                 │
│                                                                 │
│  LogAgent 执行:                                                 │
│  - 调用 createLogSpace(name="test-space", ...)                  │
│  - 返回创建结果（包含结构化数据和跳转动作）                       │
└─────────────────────────────────────────────────────────────────┘
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│                    完成处理                                      │
│                                                                 │
│  19. 保存消息历史                                                │
│      chatMessageService.saveMessage(...)                        │
│                                                                 │
│  20. 完成流式响应                                                │
│      sink.complete()                                            │
│                                                                 │
│  21. 释放资源                                                    │
│      userLockManager.unlock(username)                           │
│      concurrencyLimiter.release()                               │
└─────────────────────────────────────────────────────────────────┘
```

### 4.11 Multi-Agent Memory 设计

基于现有的 MemoryStateService（Redis + MySQL 持久化）扩展 Multi-Agent 记忆方案。

#### 4.11.1 现有实现基础

当前已有的记忆基础设施：

| 组件 | 说明 |
|-----|------|
| **MemoryStateService** | 使用 AgentScope 的 JedisSession 持久化 AutoContextMemory |
| **ChatMessageService** | 将对话消息存储到 MySQL，用于前端展示和恢复 |
| **AutoContextMemory** | AgentScope 提供的自动压缩上下文记忆 |
| **Caffeine Cache** | JVM 内热缓存，15分钟过期 |

```java
// 现有的 MemoryStateService 实现
@Service
public class MemoryStateServiceImpl implements MemoryStateService {
    
    private static final String REDIS_KEY_PREFIX = "hera:mind:memory:";
    
    // 使用 AgentScope 的 JedisSession
    private Session session = JedisSession.builder()
            .jedisPool(redisService.getJedisPool())
            .keyPrefix(REDIS_KEY_PREFIX)
            .build();
    
    @Override
    public void saveState(String username, AutoContextMemory memory) {
        SimpleSessionKey sessionKey = SimpleSessionKey.of("mind_agent:" + username);
        memory.saveTo(session, sessionKey);  // AgentScope 内置方法
    }
    
    @Override
    public boolean loadState(String username, AutoContextMemory memory) {
        SimpleSessionKey sessionKey = SimpleSessionKey.of("mind_agent:" + username);
        memory.loadFrom(session, sessionKey);  // AgentScope 内置方法
        return true;
    }
}
```

#### 4.11.2 Multi-Agent 扩展设计

| 设计原则 | 说明 |
|---------|------|
| **Memory 继续共享** | 沿用现有设计，每用户一个 Memory，所有 Agent 共享 |
| **新增 Session 持久化** | SwarmSession 也需要持久化，记录当前 Agent 状态 |
| **消息增加 Agent 标记** | ChatMessage 表增加 `agent_name` 字段 |
| **现有接口不变** | MemoryStateService 接口保持不变，只扩展 |

#### 4.11.3 存储架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                   Multi-Agent 存储架构（每用户独立）                      │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    Caffeine (JVM 热缓存)                           │  │
│  │                                                                   │  │
│  │  memoryCache[username]   → AutoContextMemory（所有 Agent 共享）    │  │
│  │  sessionCache[username]  → SwarmSession（当前 Agent 状态）         │  │
│  │                                                                   │  │
│  │  过期时自动持久化到 Redis                                          │  │
│  └───────────────────────────┬───────────────────────────────────────┘  │
│                               │                                         │
│                               ▼                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    Redis (分布式温缓存)                             │  │
│  │                                                                   │  │
│  │  hera:mind:memory:mind_agent:{username}  → Memory 状态            │  │
│  │    (使用 AgentScope JedisSession，与现有实现一致)                  │  │
│  │                                                                   │  │
│  │  hera:mind:session:{username}  → SwarmSession JSON（新增）         │  │
│  │    {                                                              │  │
│  │      "currentAgentName": "LogAgent",                              │  │
│  │      "agentHistory": ["CommonAgent", "LogAgent"],                 │  │
│  │      "lastActiveTime": 1704067200000                              │  │
│  │    }                                                              │  │
│  │                                                                   │  │
│  └───────────────────────────┬───────────────────────────────────────┘  │
│                               │                                         │
│                               ▼                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    MySQL (永久存储)                                 │  │
│  │                                                                   │  │
│  │  hera_mind_chat_message 表（扩展）：                                │  │
│  │    +------------+--------------+----------------------------------+ │  │
│  │    | username   | role         | content        | agent_name     | │  │
│  │    +------------+--------------+----------------------------------+ │  │
│  │    | alice      | USER         | 帮我创建日志   | NULL           | │  │
│  │    | alice      | ASSISTANT    | 好的，移交...  | CommonAgent    | │  │
│  │    | alice      | ASSISTANT    | 已创建空间     | LogAgent       | │  │
│  │    +------------+--------------+----------------------------------+ │  │
│  │                                                                   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 4.11.4 需要扩展的代码

**1. ChatMessage 实体增加 agent_name 字段**

```java
@Table("hera_mind_chat_message")
public class ChatMessage {
    
    @Id(keyType = KeyType.Auto)
    private Long id;
    private String username;
    private String role;
    private String content;
    
    /**
     * 产生此消息的 Agent 名称（新增）
     * USER 消息为 null，ASSISTANT 消息为具体 Agent 名称
     */
    @Column("agent_name")
    private String agentName;
    
    @Column("created_at")
    private Long createdAt;
}
```

**2. 新增 SwarmSessionService（Session 持久化）**

```java
/**
 * SwarmSession 持久化服务
 * 基于现有 RedisService 实现
 */
@Service
public class SwarmSessionService {
    
    private static final String SESSION_KEY_PREFIX = "hera:mind:session:";
    private static final long SESSION_TTL_HOURS = 24;
    
    @Resource
    private RedisService redisService;
    
    @Resource
    private Gson gson;
    
    /**
     * 保存 Session 到 Redis
     */
    public void saveSession(String username, SwarmSession session) {
        String key = SESSION_KEY_PREFIX + username;
        String json = gson.toJson(session);
        redisService.setWithExpire(key, json, SESSION_TTL_HOURS, TimeUnit.HOURS);
    }
    
    /**
     * 从 Redis 加载 Session
     */
    public SwarmSession loadSession(String username) {
        String key = SESSION_KEY_PREFIX + username;
        String json = redisService.get(key);
        if (json == null) {
            return null;
        }
        return gson.fromJson(json, SwarmSession.class);
    }
    
    /**
     * 获取或创建 Session
     */
    public SwarmSession getOrCreate(String username) {
        SwarmSession session = loadSession(username);
        if (session == null) {
            session = SwarmSession.create(username);
        }
        return session;
    }
}
```

**3. ChatMessageService 扩展（保存 Agent 名称）**

```java
@Service
public class ChatMessageServiceImpl implements ChatMessageService {
    
    /**
     * 保存消息（扩展：支持 agentName）
     */
    public void saveMessage(String username, String role, String content, String agentName) {
        ChatMessage message = ChatMessage.builder()
                .username(username)
                .role(role)
                .content(content)
                .agentName(agentName)  // 新增
                .createdAt(System.currentTimeMillis())
                .build();
        chatMessageMapper.insert(message);
    }
}
```

#### 4.11.5 Memory 共享机制

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     Memory 共享示意图                                    │
│                                                                         │
│  用户 Alice 的对话流程：                                                 │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                    AutoContextMemory                               │  │
│  │                    （CommonAgent、LogAgent、MonitorAgent 共享）     │  │
│  │                                                                   │  │
│  │  [1] USER: "你好"                                                 │  │
│  │  [2] ASSISTANT: "你好！我是 Hera Mind..." (CommonAgent)           │  │
│  │  [3] USER: "帮我创建一个日志空间叫 test"                           │  │
│  │  [4] ASSISTANT: "好的，移交给日志专家..." (CommonAgent)            │  │
│  │  ────────────────── Handoff → LogAgent ──────────────────         │  │
│  │  [5] ASSISTANT: "正在创建日志空间..." (LogAgent)                  │  │
│  │  [6] USER: "创建成功后帮我配置告警"                                │  │
│  │  [7] ASSISTANT: "日志空间已创建，移交给监控专家..." (LogAgent)     │  │
│  │  ────────────────── Handoff → MonitorAgent ──────────────────     │  │
│  │  [8] ASSISTANT: "请问要配置什么告警？" (MonitorAgent)              │  │
│  │      ↑ MonitorAgent 能看到完整历史，知道用户创建了 test 空间        │  │
│  │                                                                   │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 4.11.6 Memory 恢复流程

```java
/**
 * 基于现有 MemoryStateService 的恢复流程
 */
private AutoContextMemory getOrCreateMemory(String username) {
    return memoryCache.get(username, k -> {
        var userConfig = userConfigService.getByUsername(username);
        Model model = modelProviderService.createModel(userConfig);
        
        AutoContextConfig config = AutoContextConfig.builder()
                .msgThreshold(msgThreshold)
                .maxToken(maxToken)
                .tokenRatio(tokenRatio)
                .lastKeep(lastKeep)
                .build();
        
        AutoContextMemory memory = new AutoContextMemory(config, model);
        
        // 1. 尝试从 Redis 恢复（使用现有 MemoryStateService）
        boolean restored = memoryStateService.loadState(username, memory);
        
        if (restored) {
            log.info("Memory restored from Redis for user: {}", username);
            return memory;
        }
        
        // 2. 从 MySQL 加载历史消息重建
        log.info("Rebuilding Memory from MySQL for user: {}", username);
        List<ChatMessage> history = chatMessageService.getRecentMessages(username, 50);
        
        for (ChatMessage msg : history) {
            Msg m = Msg.builder()
                    .name(msg.getRole().toLowerCase())
                    .role("USER".equals(msg.getRole()) ? MsgRole.USER : MsgRole.ASSISTANT)
                    .content(TextBlock.builder().text(msg.getContent()).build())
                    .build();
            memory.addMessage(m);
        }
        
        return memory;
    });
}
```

#### 4.11.7 数据库变更

```sql
-- 扩展现有的 hera_mind_chat_message 表
ALTER TABLE hera_mind_chat_message 
ADD COLUMN agent_name VARCHAR(50) DEFAULT NULL COMMENT '产生此消息的 Agent 名称';

-- 添加索引
CREATE INDEX idx_username_agent ON hera_mind_chat_message(username, agent_name);
```

#### 4.11.8 Memory 与 Handoff 的协作

| 场景 | Memory 行为 |
|-----|-------------|
| **正常对话** | 消息添加到 Memory，标记 agentName |
| **Handoff 发生** | Memory 不变，Session 切换当前 Agent |
| **新 Agent 接手** | 新 Agent 使用同一 Memory，能看到完整历史 |
| **用户重新访问** | 从 Redis 恢复 Memory，从 Session 恢复当前 Agent |
| **缓存过期** | Memory 和 Session 分别持久化到 Redis |

#### 4.11.9 SwarmContext（单次请求内的上下文）

SwarmContext 用于**单次请求内**的 Handoff 信息传递，不持久化：

```java
/**
 * Handoff 时生成上下文摘要（给新 Agent 的提示）
 */
public String getHandoffSummary() {
    StringBuilder sb = new StringBuilder();
    sb.append("【Original Query】\n").append(originalQuery).append("\n\n");
    
    if (!handoffChain.isEmpty()) {
        sb.append("【Processing Chain】\n");
        for (HandoffRecord record : handoffChain) {
            sb.append("- ").append(record.getFrom())
              .append(" → ").append(record.getTo())
              .append(" (Reason: ").append(record.getReason()).append(")\n");
        }
    }
    
    if (!gatheredInfo.isEmpty()) {
        sb.append("\n【Gathered Information】\n");
        gatheredInfo.forEach((k, v) -> 
            sb.append("- ").append(k).append(": ").append(v).append("\n"));
    }
    
    return sb.toString();
}

### 4.12 Multi-Agent 使用示例

#### 4.12.1 完整对话流程示例

```
用户: "你好"
│
▼ CommonAgent 处理
AI: "你好！我是 Hera Mind，Hera 可观测平台的智能助手。
     我可以帮你管理日志、分析链路追踪、配置监控告警。有什么可以帮你的吗？"

用户: "帮我创建一个日志空间叫 order-service"
│
▼ CommonAgent 识别日志操作需求
AI: "好的，这是日志管理相关的操作，我将为您转接到日志专家。"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔄 正在移交: CommonAgent → LogAgent
原因: 用户需要创建日志空间
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
│
▼ LogAgent 接手（共享同一 Memory，能看到之前的对话）
AI: "正在为您创建日志空间 order-service...

     ✅ 日志空间创建成功！
     
     📋 详情:
     - 名称: order-service
     - ID: 12345
     - 存储配额: 100GB
     - 保留天数: 30天
     
     [点击查看] [配置采集]"

用户: "创建成功后帮我配置告警，当日志中出现 ERROR 超过 100 条时告警"
│
▼ LogAgent 识别监控需求
AI: "日志空间已准备就绪。配置告警规则需要监控专家处理，我将为您转接。"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🔄 正在移交: LogAgent → MonitorAgent
原因: 用户需要配置告警规则
已收集信息: 日志空间 order-service (ID:12345) 已创建成功
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
│
▼ MonitorAgent 接手（知道 order-service 的上下文）
AI: "我了解到您刚创建了 order-service 日志空间。
     
     正在为您配置告警规则...
     
     ✅ 告警规则创建成功！
     
     📋 规则详情:
     - 数据源: order-service 日志空间
     - 触发条件: ERROR 日志 > 100 条/5分钟
     - 通知方式: 邮件 + 飞书
     
     [查看规则] [测试告警]"
```

#### 4.12.2 Handoff 循环防护示例

```
场景: 用户问题涉及多个领域

用户: "帮我查一下 order-service 最近的错误日志，然后看看对应的 trace 链路"
│
▼ 从 CommonAgent 开始
CommonAgent: "好的，我先帮您转接到日志专家查询错误日志。"
│
▼ Handoff to LogAgent
LogAgent: "查询到 order-service 最近 1 小时有 23 条 ERROR 日志：
           - NullPointerException: 15 条
           - TimeoutException: 8 条
           
           您需要查看对应的 Trace 链路，我将转接到链路追踪专家。"
│
▼ Handoff to TraceAgent (visitedAgents = [CommonAgent, LogAgent, TraceAgent])
TraceAgent: "根据日志中的 TraceID，我找到了相关的调用链路：
             
             OrderService → PaymentService → BankGateway
                          ↑
                          这里超时导致了 8 条 TimeoutException
             
             [查看链路详情]"
│
▼ 用户继续: "算了，我不看了，帮我删除这个日志空间"
│
▼ 需要 Handoff 回 LogAgent，但 LogAgent 已在 visitedAgents 中
│
▼ context.canHandoffTo("LogAgent") 返回 false（防止循环）
│
▼ TraceAgent 直接回答
TraceAgent: "抱歉，我无法直接删除日志空间。
             请输入新的消息，我会引导您找到合适的专家来处理。"
```

#### 4.12.3 Memory 共享验证示例

```java
/**
 * 验证不同 Agent 共享 Memory
 */
@Test
public void testMemorySharing() {
    String username = "test-user";
    
    // 1. 创建 Memory
    AutoContextMemory memory = getOrCreateMemory(username);
    
    // 2. 模拟 CommonAgent 添加消息
    memory.addMessage(Msg.builder()
        .role(MsgRole.USER)
        .content(TextBlock.builder().text("你好").build())
        .build());
    memory.addMessage(Msg.builder()
        .role(MsgRole.ASSISTANT)
        .content(TextBlock.builder().text("你好！我是 Hera Mind。").build())
        .build());
    
    // 3. 模拟 Handoff 到 LogAgent
    session.switchAgent("LogAgent");
    
    // 4. LogAgent 使用同一个 Memory
    List<Msg> messages = memory.getMessages();
    assertEquals(2, messages.size());  // ✅ LogAgent 能看到之前的对话
    
    // 5. LogAgent 添加新消息
    memory.addMessage(Msg.builder()
        .role(MsgRole.USER)
        .content(TextBlock.builder().text("创建日志空间 test").build())
        .build());
    
    // 6. 验证 Memory 状态
    assertEquals(3, memory.getMessages().size());  // ✅ 消息累积
}
```

#### 4.12.4 Session 持久化示例

```java
/**
 * 验证 Session 持久化与恢复
 */
@Test
public void testSessionPersistence() {
    String username = "test-user";
    
    // 1. 创建并使用 Session
    SwarmSession session = SwarmSession.create(username);
    assertEquals("CommonAgent", session.getCurrentAgentName());
    
    // 2. 模拟 Handoff
    session.switchAgent("LogAgent");
    session.switchAgent("MonitorAgent");
    
    // 3. 保存到 Redis
    sessionService.saveSession(username, session);
    
    // 4. 模拟重新登录（从 Redis 恢复）
    SwarmSession restored = sessionService.loadSession(username);
    
    // 5. 验证恢复状态
    assertEquals("MonitorAgent", restored.getCurrentAgentName());  // ✅ 当前 Agent
    assertEquals(3, restored.getAgentHistory().size());  // ✅ 历史记录
    // agentHistory = ["CommonAgent", "LogAgent", "MonitorAgent"]
}
```

#### 4.12.5 新增 Agent 示例

```java
/**
 * 新增 TraceAgent 的完整示例
 */
@Component
public class TraceAgent implements HeraAgent {
    
    @Resource
    private TraceToolService traceToolService;
    
    @Resource
    private HandoffTool handoffTool;
    
    @Override
    public String getName() {
        return "TraceAgent";
    }
    
    @Override
    public String getDescription() {
        return "Distributed tracing expert, analyzes call chains and latency issues";
    }
    
    @Override
    public String getSystemPrompt() {
        return """
            You are Hera's distributed tracing expert.
            
            ## Capabilities
            - Query and analyze distributed traces
            - View span details and timing breakdown
            - Analyze service dependency topology
            - Troubleshoot cross-service call issues
            
            ## Tools Available
            - queryTrace: Query traces by traceId or service name
            - getSpanDetails: Get detailed span information
            - analyzeLatency: Analyze latency distribution
            - getTopology: Get service dependency graph
            
            ## Handoff Rules
            - Log queries → handoff_to_log
            - Alert configuration → handoff_to_monitor
            - General chat → handoff_to_common
            
            Always provide structured data when available.
            """;
    }
    
    @Override
    public Toolkit getToolkit() {
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(traceToolService);
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

### 4.13 实际实现的文件清单

| 文件 | 描述 |
|-----|------|
| `swarm/HeraAgent.java` | Agent 接口定义 |
| `swarm/AgentRegistry.java` | Agent 注册表（自动发现所有 HeraAgent 实现） |
| `swarm/SwarmSession.java` | 会话状态（当前 Agent、历史） |
| `swarm/SwarmSessionService.java` | Session Redis 持久化 |
| `swarm/SwarmExecutor.java` | 执行引擎（Handoff 处理） |
| `swarm/SwarmAgentService.java` | 主服务（替代 AgentServiceImpl） |
| `swarm/HandoffTool.java` | 统一 Handoff 工具 |
| `swarm/agents/CommonAgent.java` | 通用 Agent（默认入口） |
| `swarm/agents/LogAgent.java` | 日志 Agent |
| `swarm/agents/MonitorAgent.java` | 监控 Agent |
| `concurrency/VirtualThreadConfig.java` | 虚拟线程配置 |
| `concurrency/ConcurrencyLimiter.java` | 全局并发限制 |
| `concurrency/UserLockManager.java` | 用户级锁 |
| `concurrency/LLMRateLimiter.java` | LLM API 限流 |

---

## 5. 高并发方案

### 5.1 技术选型

采用 **Java 21 虚拟线程 + WebFlux** 组合方案：

| 技术 | 作用 | 优势 |
|-----|------|------|
| Virtual Threads | 执行 Agent 任务 | 轻量级，可创建百万级线程 |
| WebFlux | 响应式流式输出 | 非阻塞，高吞吐 |
| Semaphore | 并发控制 | 限制同时处理的请求数 |
| RateLimiter | API 限流 | 防止触发外部 API 限制 |

### 5.2 为什么选择虚拟线程

```
传统线程模型：
┌─────────────────────────────────────────────────────────────┐
│  Platform Threads (有限，如 200 个)                          │
│                                                             │
│  请求1 ──► Thread-1 [████████████████] 阻塞等待 LLM 响应    │
│  请求2 ──► Thread-2 [████████████████] 阻塞等待 LLM 响应    │
│  请求3 ──► 等待...（线程耗尽）                               │
│                                                             │
│  问题：每个线程 ~1MB 栈内存，200 线程 = 200MB               │
│       线程被 I/O 阻塞时无法处理其他请求                      │
└─────────────────────────────────────────────────────────────┘

虚拟线程模型：
┌─────────────────────────────────────────────────────────────┐
│  Virtual Threads (可创建百万个)                              │
│                                                             │
│  请求1 ──► VThread-1 [██]等待[██]继续    每个 ~KB 级内存    │
│  请求2 ──► VThread-2 [██]等待[██]继续                       │
│  请求3 ──► VThread-3 [██]等待[██]继续                       │
│  ...                                                        │
│  请求10000 ──► VThread-10000                                │
│                                                             │
│  优势：I/O 等待时自动让出平台线程                            │
│       可以写同步代码，但获得异步性能                         │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 虚拟线程配置

```java
package org.apache.ozhera.mind.service.concurrency;

import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * 虚拟线程配置
 */
@Configuration
public class VirtualThreadConfig {
    
    /**
     * Tomcat 使用虚拟线程处理请求
     */
    @Bean
    public TomcatProtocolHandlerCustomizer<?> virtualThreadExecutorCustomizer() {
        return protocolHandler -> {
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
    
    /**
     * Agent 任务虚拟线程执行器
     */
    @Bean
    public java.util.concurrent.ExecutorService agentVirtualExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

```yaml
# application.yml (Spring Boot 3.2+)
spring:
  threads:
    virtual:
      enabled: true
```

### 5.4 并发控制组件

#### 5.4.1 全局并发限制器

```java
package org.apache.ozhera.mind.service.concurrency;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 全局并发限制器
 * 限制系统同时处理的请求数量
 */
@Slf4j
@Component
public class ConcurrencyLimiter {
    
    @NacosValue(value = "${agent.concurrency.max-concurrent-requests:1000}", autoRefreshed = true)
    private int maxConcurrentRequests;
    
    private Semaphore semaphore;
    
    @PostConstruct
    public void init() {
        this.semaphore = new Semaphore(maxConcurrentRequests);
        log.info("ConcurrencyLimiter initialized with max={}", maxConcurrentRequests);
    }
    
    /**
     * 尝试获取许可
     */
    public boolean tryAcquire(long timeout, TimeUnit unit) throws InterruptedException {
        return semaphore.tryAcquire(timeout, unit);
    }
    
    /**
     * 释放许可
     */
    public void release() {
        semaphore.release();
    }
    
    /**
     * 获取当前可用许可数
     */
    public int availablePermits() {
        return semaphore.availablePermits();
    }
    
    /**
     * 获取当前等待数
     */
    public int getQueueLength() {
        return semaphore.getQueueLength();
    }
}
```

#### 5.4.2 用户级锁管理器

```java
package org.apache.ozhera.mind.service.concurrency;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 用户级锁管理器
 * 保证同一用户的请求串行执行
 */
@Slf4j
@Component
public class UserLockManager {
    
    /**
     * 用户锁映射
     * 每个用户一个 Semaphore(1)，确保串行
     */
    private final ConcurrentHashMap<String, Semaphore> userLocks = new ConcurrentHashMap<>();
    
    /**
     * 尝试获取用户锁
     */
    public boolean tryLock(String username, long timeout, TimeUnit unit) throws InterruptedException {
        Semaphore lock = userLocks.computeIfAbsent(username, k -> new Semaphore(1));
        boolean acquired = lock.tryAcquire(timeout, unit);
        if (!acquired) {
            log.warn("Failed to acquire lock for user: {}, timeout: {}ms", 
                username, unit.toMillis(timeout));
        }
        return acquired;
    }
    
    /**
     * 释放用户锁
     */
    public void unlock(String username) {
        Semaphore lock = userLocks.get(username);
        if (lock != null) {
            lock.release();
        }
    }
    
    /**
     * 检查用户是否有锁
     */
    public boolean isLocked(String username) {
        Semaphore lock = userLocks.get(username);
        return lock != null && lock.availablePermits() == 0;
    }
    
    /**
     * 清理不活跃的锁（定期调用）
     */
    public void cleanup() {
        userLocks.entrySet().removeIf(entry -> 
            entry.getValue().availablePermits() == 1);
    }
}
```

#### 5.4.3 LLM API 限流器

使用 **Token Bucket 算法** 基于 Semaphore 实现，无需额外依赖：

```java
package org.apache.ozhera.mind.service.concurrency;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * LLM API 限流器
 * 使用 Token Bucket 算法，按 Provider 分别限流
 */
@Slf4j
@Component
public class LLMRateLimiter {

    @NacosValue(value = "${agent.rate-limit.openai-rpm:60}", autoRefreshed = true)
    private int openaiRpm;

    @NacosValue(value = "${agent.rate-limit.dashscope-rpm:100}", autoRefreshed = true)
    private int dashscopeRpm;

    @NacosValue(value = "${agent.rate-limit.default-rpm:50}", autoRefreshed = true)
    private int defaultRpm;

    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        buckets.put("openai", new RateLimitBucket(openaiRpm));
        buckets.put("dashscope", new RateLimitBucket(dashscopeRpm));
        log.info("LLMRateLimiter initialized: openai={}rpm, dashscope={}rpm",
                openaiRpm, dashscopeRpm);
    }

    /**
     * 获取 API 调用许可（阻塞等待）
     */
    public void acquire(String provider) {
        RateLimitBucket bucket = buckets.computeIfAbsent(
                provider.toLowerCase(),
                p -> new RateLimitBucket(defaultRpm)
        );
        bucket.acquire();
    }

    /**
     * 尝试获取许可（非阻塞）
     */
    public boolean tryAcquire(String provider) {
        RateLimitBucket bucket = buckets.computeIfAbsent(
                provider.toLowerCase(),
                p -> new RateLimitBucket(defaultRpm)
        );
        return bucket.tryAcquire();
    }

    /**
     * Token Bucket 实现
     */
    private static class RateLimitBucket {
        private final int maxTokens;
        private final Semaphore tokens;
        private volatile long lastRefillTime;
        private final long refillIntervalMs = 60_000; // 1 minute

        RateLimitBucket(int rpm) {
            this.maxTokens = rpm;
            this.tokens = new Semaphore(rpm);
            this.lastRefillTime = System.currentTimeMillis();
        }

        void acquire() {
            refillIfNeeded();
            try {
                tokens.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Rate limit acquire interrupted", e);
            }
        }

        boolean tryAcquire() {
            refillIfNeeded();
            return tokens.tryAcquire();
        }

        private synchronized void refillIfNeeded() {
            long now = System.currentTimeMillis();
            if (now - lastRefillTime >= refillIntervalMs) {
                int toRefill = maxTokens - tokens.availablePermits();
                if (toRefill > 0) {
                    tokens.release(toRefill);
                }
                lastRefillTime = now;
            }
        }
    }
}
```

**Token Bucket 算法说明：**

```
┌─────────────────────────────────────────────────────────────┐
│                    Token Bucket 工作原理                     │
│                                                             │
│   初始状态：桶中有 60 个令牌 (maxTokens = 60)                 │
│                                                             │
│   ┌─────────────────────┐                                   │
│   │  ●●●●●●●●●●●●●●●●●●  │  ← 令牌桶 (Semaphore)             │
│   │  ●●●●●●●●●●●●●●●●●●  │                                   │
│   │  ●●●●●●●●●●●●●●●●●●  │    60 个令牌 = 60 RPM             │
│   │  ●●●●●●              │                                   │
│   └─────────┬───────────┘                                   │
│             │                                               │
│             ▼                                               │
│   请求到来 → acquire() → 取走一个令牌                        │
│                                                             │
│   每 60 秒 → refillIfNeeded() → 补充令牌到 maxTokens         │
│                                                             │
│   令牌耗尽 → acquire() 阻塞等待 → 直到下一次 refill          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### 5.5 并发控制流程

```
请求进入
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  1. 全局并发限制检查                                         │
│     ConcurrencyLimiter.tryAcquire(10s)                       │
│                                                             │
│     ├─ 成功 → 继续                                          │
│     └─ 超时 → 返回 "服务繁忙，请稍后再试"                     │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  2. 用户级锁获取                                             │
│     UserLockManager.tryLock(username, 30s)                   │
│                                                             │
│     ├─ 成功 → 继续                                          │
│     └─ 超时 → 返回 "您有请求正在处理中，请稍候"              │
│              释放全局许可                                    │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  3. LLM API 限流                                            │
│     LLMRateLimiter.acquire(provider)                        │
│                                                             │
│     ├─ 成功 → 继续                                          │
│     └─ 等待 → 排队等待配额                                  │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  4. 在虚拟线程中执行 Agent                                   │
│     virtualExecutor.submit(() -> processChat(...))           │
│                                                             │
│     虚拟线程优势：                                           │
│     - 阻塞等待 LLM 响应时自动让出                            │
│     - 可以写同步代码风格                                     │
│     - 支持百万级并发                                         │
└─────────────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────────────┐
│  5. 完成处理，释放资源                                       │
│     finally {                                               │
│         userLockManager.unlock(username);                   │
│         concurrencyLimiter.release();                       │
│     }                                                       │
└─────────────────────────────────────────────────────────────┘
```

### 5.6 配置参数

```yaml
agent:
  concurrency:
    # 全局并发控制
    max-concurrent-requests: 1000    # 最大同时处理请求数
    acquire-timeout-ms: 5000         # 获取全局许可超时（毫秒）
    
    # 用户级控制
    user-lock-timeout-ms: 30000      # 用户锁超时（毫秒）
    
  rate-limit:
    # LLM API 限流（每分钟请求数）
    openai-rpm: 60
    dashscope-rpm: 100
    default-rpm: 50
```

### 5.7 并发控制集成示例

以下是 `SwarmAgentService` 中集成并发控制的完整代码：

```java
/**
 * 流式聊天 - 带并发控制
 */
public Flux<String> chatStream(ChatRequest request) {
    String username = request.getUsername();
    String userMessage = request.getMessage();

    log.info("Chat request from user: {}, message length: {}", username, userMessage.length());

    return Mono.fromCallable(() -> {
                // Step 1: 全局并发限制
                if (!concurrencyLimiter.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                    throw new RuntimeException("System busy, please try again later. " +
                            "Queue length: " + concurrencyLimiter.getQueueLength());
                }
                return true;
            })
            .flatMapMany(acquired -> {
                // Step 2: 用户级锁
                return Mono.fromCallable(() -> {
                            if (!userLockManager.tryLock(username, userLockTimeoutMs, TimeUnit.MILLISECONDS)) {
                                concurrencyLimiter.release();
                                throw new RuntimeException("Previous request still processing, please wait.");
                            }
                            return true;
                        })
                        .flatMapMany(locked -> {
                            // Step 3: 执行聊天
                            AutoContextMemory memory = getOrCreateMemory(username);
                            SwarmSession session = getOrCreateSession(username);
                            
                            chatMessageService.saveMessage(username, "USER", userMessage);

                            StringBuilder fullResponse = new StringBuilder();

                            return swarmExecutor.execute(username, userMessage, session, memory)
                                    .doOnNext(chunk -> fullResponse.append(chunk))
                                    .doOnComplete(() -> {
                                        // 保存响应
                                        String response = fullResponse.toString();
                                        if (!response.isEmpty()) {
                                            String cleanResponse = response
                                                .replaceAll("__HANDOFF__:\\{[^}]+\\}", "").trim();
                                            if (!cleanResponse.isEmpty()) {
                                                chatMessageService.saveMessage(username, "ASSISTANT", cleanResponse);
                                            }
                                        }
                                        memoryStateService.saveState(username, memory);
                                        sessionService.saveSession(username, session);
                                    })
                                    .doOnError(e -> log.error("Chat failed for user: {}", username, e))
                                    .doFinally(signal -> {
                                        // 释放锁
                                        userLockManager.unlock(username);
                                        concurrencyLimiter.release();
                                        log.debug("Released locks for user: {}", username);
                                    });
                        });
            })
            .subscribeOn(Schedulers.fromExecutor(agentVirtualExecutor))
            .onErrorResume(e -> {
                log.error("Chat error for user: {}", username, e);
                return Flux.just("{\"error\": \"" + e.getMessage() + "\"}");
            });
}
```

### 5.8 SwarmExecutor 中的 LLM 限流

```java
/**
 * SwarmExecutor.executeInternal() 中在调用 LLM 前进行限流
 */
private void executeInternal(...) {
    // ... 前置代码 ...

    // 创建 Model
    UserConfig userConfig = userConfigService.getByUsername(username);
    Model model = modelProviderService.createModel(userConfig);

    // ★ LLM API 限流 - 在调用前获取令牌
    String provider = userConfig.getModelPlatform();  // "openai" / "dashscope"
    llmRateLimiter.acquire(provider);
    log.debug("LLM rate limit acquired for provider: {}", provider);

    // 创建并执行 Agent
    ReActAgent agent = agentDef.createReActAgent(model, memory, hook);
    agent.call(List.of(userMsg)).subscribe();
}
```

### 5.9 使用场景示例

#### 场景 1：系统高负载保护

```
时间线：
├─ T0: 1000 个用户同时发起请求
├─ T0: ConcurrencyLimiter 放行 1000 个请求（达到上限）
├─ T0: 第 1001 个用户请求进入等待队列
├─ T0 + 5s: 等待超时，返回 "System busy, please try again later"
├─ T0 + 3s: 某些请求完成，释放许可
└─ T0 + 3s: 队列中的请求开始处理

效果：
- 系统不会因请求过多而崩溃
- 超时请求快速失败，不浪费资源
- 用户收到明确的提示信息
```

#### 场景 2：防止用户重复提交

```
时间线：
├─ T0: 用户 Alice 发送请求 A
├─ T0: UserLockManager 为 Alice 加锁
├─ T1: 用户 Alice 再次点击发送（请求 B）
├─ T1: 请求 B 等待获取 Alice 的锁
├─ T0 + 10s: 请求 A 完成，释放锁
├─ T0 + 10s: 请求 B 获取锁，开始处理
│
│  如果请求 A 耗时超过 30s：
├─ T0 + 30s: 请求 B 等待超时
└─ T0 + 30s: 返回 "Previous request still processing, please wait."

效果：
- 同一用户的请求串行处理
- 防止 Memory 状态竞争
- 避免重复扣费
```

#### 场景 3：LLM API 配额保护

```
时间线（OpenAI 限制 60 RPM）：
├─ T0~T30s: 处理了 60 个请求，令牌耗尽
├─ T31s: 第 61 个请求到来
├─ T31s: llmRateLimiter.acquire("openai") 阻塞
├─ T60s: refillIfNeeded() 补充 60 个令牌
├─ T60s: 第 61 个请求获取令牌，继续处理
│
│  多 Provider 隔离：
├─ OpenAI 令牌耗尽，等待中...
├─ DashScope 用户的请求正常处理（独立令牌桶）
└─ 不同 Provider 互不影响

效果：
- 防止触发 LLM API 的 429 错误
- 不同 Provider 独立限流
- 平滑请求流量
```

### 5.10 监控与运维

```java
// 获取系统负载状态
public ConcurrencyStatus getStatus() {
    return ConcurrencyStatus.builder()
        .availablePermits(concurrencyLimiter.availablePermits())
        .queueLength(concurrencyLimiter.getQueueLength())
        .totalPermits(1000)
        .utilizationRate(1.0 - (concurrencyLimiter.availablePermits() / 1000.0))
        .build();
}

// 检查特定用户状态
public boolean isUserProcessing(String username) {
    return userLockManager.isLocked(username);
}

// 定期清理不活跃的用户锁
@Scheduled(fixedRate = 300000)  // 每 5 分钟
public void cleanupInactiveLocks() {
    userLockManager.cleanup();
}
```

---

## 6. 多模态处理

### 6.1 设计目标

1. 支持用户在聊天框中上传图片
2. 图片不存储在 Memory 中，只存引用
3. **优先使用 URL 传输**（节省 Token、节省存储、提高性能）
4. Base64 作为降级方案（当 LLM 无法访问 URL 时）

### 6.2 设计理念：URL 优先 + 引用存储

#### 核心原则：存储与传输分离

| 层级 | 存储内容 | 大小 | 说明 |
|-----|---------|------|------|
| **Memory/Redis** | ImageRefContent（引用） | ~200 字节 | 只存 imageId、url、mimeType |
| **MySQL** | ImageRefContent（引用） | ~200 字节 | 聊天记录只存引用，不存图片数据 |
| **OSS/MinIO** | 实际图片文件 | 原始大小 | 图片文件的唯一存储位置 |
| **LLM 传输** | URL 或 Base64（临时） | 视策略 | **临时转换，不持久化** |

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     存储 vs 传输 分离设计                                │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    持久化存储（永久保存）                         │   │
│  │                                                                  │   │
│  │   Memory/Redis/MySQL：                                           │   │
│  │   {                                                              │   │
│  │     "type": "image_ref",                                         │   │
│  │     "imageId": "abc123",                   ← 只存引用，~200 字节 │   │
│  │     "url": "https://oss.xxx.com/abc.jpg",                        │   │
│  │     "mimeType": "image/jpeg"                                     │   │
│  │   }                                                              │   │
│  │                                                                  │   │
│  │   OSS/MinIO：                                                    │   │
│  │   mind/images/alice/abc123.jpg → 实际图片文件                    │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                               │                                         │
│                               ▼                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                  临时转换（仅发送给 LLM 时）                       │   │
│  │                                                                  │   │
│  │   URL 策略（默认）：                                              │   │
│  │   { "type": "image_url", "url": "https://oss.xxx.com/..." }      │   │
│  │                                                                  │   │
│  │   Base64 策略（降级）：                                           │   │
│  │   { "type": "base64", "data": "临时从 OSS 下载并编码..." }       │   │
│  │                        ↑                                         │   │
│  │                  这个 Base64 数据是临时的                         │   │
│  │                  用完即丢，不会存入 MySQL！                        │   │
│  │                                                                  │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

#### 传输策略对比（不影响存储）

| 方案 | Token 消耗 | 存储消耗 | 性能 | 适用场景 |
|-----|-----------|---------|------|---------|
| **URL 传输（优先）** | 低（仅 URL 字符串） | **相同（都是引用）** | 高 | LLM 可访问图片 URL |
| Base64 传输（降级） | 高（图片编码后约 1.37 倍） | **相同（都是引用）** | 较低（需下载+编码） | LLM 无法访问 URL |

> **重要**：无论使用哪种传输策略，MySQL 存储的都是 ImageRefContent 引用（~200 字节），不是 Base64 数据！
> Base64 只是在调用 `convertForLLM()` 时临时生成，用于发送给 LLM，不会被持久化。

**为什么优先使用 URL：**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     URL vs Base64 对比                                   │
│                                                                         │
│  1MB 图片的处理方式对比：                                                 │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ URL 方式（默认）                                                  │   │
│  │                                                                  │   │
│  │ Memory/MySQL: { "imageId": "...", "url": "..." }     ~200 字节  │   │
│  │ OSS 存储:     mind/images/xxx.jpg                    1 MB       │   │
│  │ 发送给 LLM:   { "type": "image_url", "url": "..." }  ~150 字节  │   │
│  │ Token 消耗:   约 50 tokens                                       │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │ Base64 方式（降级）                                               │   │
│  │                                                                  │   │
│  │ Memory/MySQL: { "imageId": "...", "url": "..." }     ~200 字节  │   │
│  │ OSS 存储:     mind/images/xxx.jpg                    1 MB       │   │
│  │ 发送给 LLM:   { "data": "临时 Base64..." }          ~1.37 MB   │   │
│  │ Token 消耗:   约 1,500+ tokens                                   │   │
│  │                     ↑                                            │   │
│  │          临时生成，用完即丢，不存 MySQL！                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  结论：                                                                 │
│  - 存储消耗相同（都是引用 ~200 字节）                                    │
│  - URL 方式节省约 97% 的 Token 消耗                                     │
│  - URL 方式性能更高（无需下载+编码）                                     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.3 架构设计

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          多模态处理流程                                  │
│                                                                         │
│  用户上传图片                                                            │
│       │                                                                 │
│       ▼                                                                 │
│  ┌──────────────────┐                                                   │
│  │  前端预处理       │  压缩、格式检查                                   │
│  └────────┬─────────┘                                                   │
│           │                                                             │
│           ▼                                                             │
│  ┌──────────────────┐      ┌──────────────────┐                        │
│  │  ImagePreprocessor│ ───► │ ImageStorageService│                       │
│  │  (压缩/格式转换)  │      │ (OSS/MinIO)       │                        │
│  └──────────────────┘      └────────┬─────────┘                        │
│                                     │                                   │
│                              返回 ImageRefContent                       │
│                              { imageId, url, mimeType, size }           │
│                                     │                                   │
│                                     ▼                                   │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                         Memory 存储                               │  │
│  │                                                                   │  │
│  │  消息: {                                                          │  │
│  │    role: "user",                                                  │  │
│  │    content: [                                                     │  │
│  │      { type: "text", text: "这张图片是什么？" },                   │  │
│  │      { type: "image_ref",                                         │  │
│  │        imageId: "abc123",                      ← 只存引用         │  │
│  │        url: "https://oss.xxx.com/xxx.jpg",     ← ~200 字节        │  │
│  │        mimeType: "image/jpeg",                                    │  │
│  │        size: 1048576 }                                            │  │
│  │    ]                                                              │  │
│  │  }                                                                │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                     │                                   │
│                                     ▼                                   │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │                    ImageTransferStrategy                          │  │
│  │                                                                   │  │
│  │   ★ URL 优先（默认）              Base64 降级（备选）             │  │
│  │   ┌──────────────────┐          ┌──────────────────┐             │  │
│  │   │ 直接传递公网 URL  │          │ 1. 下载图片       │             │  │
│  │   │ 节省 Token       │          │ 2. Base64 编码   │             │  │
│  │   │ 高性能           │          │ 3. 构建 data URL │             │  │
│  │   └──────────────────┘          └──────────────────┘             │  │
│  │                                                                   │  │
│  │   使用条件：                      使用条件：                       │  │
│  │   - OSS 有公网访问能力            - 内网部署无公网                 │  │
│  │   - LLM 能访问该 URL              - LLM 无法访问图片 URL           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│                                     │                                   │
│                                     ▼                                   │
│                              发送给 LLM                                 │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.3 核心接口定义

#### 6.3.1 图片存储服务接口

```java
package org.apache.ozhera.mind.service.multimodal;

import org.apache.ozhera.mind.service.multimodal.model.ImageRefContent;

/**
 * 图片存储服务接口
 * 外网默认实现 OSS，内网可重载为 MinIO
 */
public interface ImageStorageService {
    
    /**
     * 上传图片
     *
     * @param data     图片数据
     * @param filename 文件名
     * @param mimeType MIME 类型
     * @param username 用户名
     * @return 图片引用信息
     */
    ImageRefContent upload(byte[] data, String filename, String mimeType, String username);
    
    /**
     * 下载图片
     *
     * @param imageId 图片 ID
     * @return 图片数据
     */
    byte[] download(String imageId);
    
    /**
     * 删除图片
     *
     * @param imageId 图片 ID
     */
    void delete(String imageId);
    
    /**
     * 生成访问 URL
     *
     * @param imageId 图片 ID
     * @return 访问 URL
     */
    String generateUrl(String imageId);
}
```

#### 6.3.2 图片传输策略接口

```java
package org.apache.ozhera.mind.service.multimodal;

import org.apache.ozhera.mind.service.multimodal.model.ImageRefContent;

/**
 * 图片传输策略接口
 * 定义如何将图片传递给 LLM
 * 
 * 策略优先级：
 * 1. URL 直传（默认，节省 Token 和存储）
 * 2. Base64 传输（降级方案，当 URL 不可访问时）
 */
public interface ImageTransferStrategy {
    
    /**
     * 将图片引用转换为 LLM 可接受的格式
     *
     * @param imageRef 图片引用
     * @param provider LLM 提供商
     * @return LLM 格式的图片内容
     */
    Object convertForLLM(ImageRefContent imageRef, String provider);
    
    /**
     * 是否支持该 Provider
     *
     * @param provider LLM 提供商
     * @return 是否支持
     */
    boolean supports(String provider);
    
    /**
     * 获取策略优先级（数字越小优先级越高）
     * URL 策略返回 1，Base64 策略返回 10
     */
    default int getPriority() {
        return 10;
    }
    
    /**
     * 策略优先级（数字越小优先级越高）
     */
    default int getOrder() {
        return 100;
    }
}
```

#### 6.3.3 图片预处理接口

```java
package org.apache.ozhera.mind.service.multimodal;

/**
 * 图片预处理接口
 * 负责图片压缩、格式转换等
 */
public interface ImagePreprocessor {
    
    /**
     * 预处理图片
     *
     * @param original 原始图片数据
     * @param mimeType MIME 类型
     * @return 处理后的图片数据
     */
    byte[] preprocess(byte[] original, String mimeType);
    
    /**
     * 生成缩略图
     *
     * @param original 原始图片数据
     * @param maxSize  最大边长
     * @return 缩略图数据
     */
    byte[] generateThumbnail(byte[] original, int maxSize);
    
    /**
     * 检查图片是否需要压缩
     */
    boolean needsCompression(byte[] data, String mimeType);
}
```

### 6.4 数据模型

```java
package org.apache.ozhera.mind.service.multimodal.model;

import lombok.Builder;
import lombok.Data;

/**
 * 图片引用内容
 * 存储在 Memory 中，只包含引用信息，不包含图片数据
 */
@Data
@Builder
public class ImageRefContent implements ContentBlock {
    
    private static final String TYPE = "image_ref";
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    /**
     * 图片唯一 ID
     */
    private String imageId;
    
    /**
     * MIME 类型 (image/jpeg, image/png, etc.)
     */
    private String mimeType;
    
    /**
     * 原始文件大小（字节）
     */
    private long size;
    
    /**
     * 默认访问 URL
     */
    private String url;
    
    /**
     * 内网 URL（内网部署时使用）
     */
    private String internalUrl;
    
    /**
     * 缩略图 URL（前端展示用）
     */
    private String thumbnailUrl;
    
    /**
     * 存储对象 Key
     */
    private String objectKey;
    
    /**
     * 图片宽度
     */
    private Integer width;
    
    /**
     * 图片高度
     */
    private Integer height;
    
    /**
     * 图片描述（LLM 分析后可填充）
     */
    private String description;
}
```

### 6.5 外网默认实现

#### 6.5.1 OSS 存储实现

```java
package org.apache.ozhera.mind.service.multimodal.impl;

import com.alibaba.nacos.api.config.annotation.NacosValue;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.multimodal.ImageStorageService;
import org.apache.ozhera.mind.service.multimodal.model.ImageRefContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.UUID;

/**
 * OSS 图片存储实现（外网默认）
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.multimodal.storage.type", havingValue = "oss", matchIfMissing = true)
public class OssImageStorageService implements ImageStorageService {
    
    @Resource
    private OSS ossClient;
    
    @NacosValue("${agent.multimodal.storage.bucket}")
    private String bucket;
    
    @NacosValue("${agent.multimodal.storage.prefix:mind/images}")
    private String prefix;
    
    @NacosValue("${agent.multimodal.storage.url-expire-hours:24}")
    private int urlExpireHours;
    
    @Override
    public ImageRefContent upload(byte[] data, String filename, String mimeType, String username) {
        // 生成唯一 ID 和对象 Key
        String imageId = UUID.randomUUID().toString();
        String extension = getExtension(mimeType);
        String objectKey = String.format("%s/%s/%s.%s", prefix, username, imageId, extension);
        
        // 上传到 OSS
        ossClient.putObject(bucket, objectKey, new ByteArrayInputStream(data));
        log.info("Image uploaded: bucket={}, key={}, size={}", bucket, objectKey, data.length);
        
        // 生成访问 URL
        String url = generateUrl(imageId);
        
        return ImageRefContent.builder()
            .imageId(imageId)
            .objectKey(objectKey)
            .url(url)
            .mimeType(mimeType)
            .size(data.length)
            .build();
    }
    
    @Override
    public byte[] download(String imageId) {
        String objectKey = resolveObjectKey(imageId);
        OSSObject object = ossClient.getObject(bucket, objectKey);
        try {
            return object.getObjectContent().readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Failed to download image: " + imageId, e);
        }
    }
    
    @Override
    public void delete(String imageId) {
        String objectKey = resolveObjectKey(imageId);
        ossClient.deleteObject(bucket, objectKey);
        log.info("Image deleted: {}", objectKey);
    }
    
    @Override
    public String generateUrl(String imageId) {
        String objectKey = resolveObjectKey(imageId);
        Date expiration = new Date(System.currentTimeMillis() + urlExpireHours * 3600 * 1000L);
        return ossClient.generatePresignedUrl(bucket, objectKey, expiration).toString();
    }
    
    private String getExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> "jpg";
        };
    }
    
    private String resolveObjectKey(String imageId) {
        // 实际实现需要从数据库或缓存查找 objectKey
        // 这里简化处理
        return prefix + "/*/" + imageId + ".*";
    }
}
```

#### 6.5.2 URL 传输策略实现（默认，优先使用）

```java
package org.apache.ozhera.mind.service.multimodal.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.multimodal.ImageStorageService;
import org.apache.ozhera.mind.service.multimodal.ImageTransferStrategy;
import org.apache.ozhera.mind.service.multimodal.model.ImageRefContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Map;

/**
 * URL 图片传输策略（默认，优先使用）
 * 
 * 优势：
 * - 节省 Token：仅传递 URL 字符串（~100 字节 vs 1MB+ Base64）
 * - 节省存储：Memory 中只存引用
 * - 高性能：无需下载和编码
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.multimodal.transfer-strategy", havingValue = "url", matchIfMissing = true)
public class UrlImageTransferStrategy implements ImageTransferStrategy {
    
    @Resource
    private ImageStorageService storageService;
    
    @Override
    public Object convertForLLM(ImageRefContent imageRef, String provider) {
        // 生成公网可访问的 URL
        String accessUrl = storageService.generateUrl(imageRef.getImageId());
        
        // 根据不同 Provider 格式化
        return formatForProvider(accessUrl, imageRef.getMimeType(), provider);
    }
    
    private Object formatForProvider(String url, String mimeType, String provider) {
        return switch (provider.toLowerCase()) {
            case "openai" -> Map.of(
                "type", "image_url",
                "image_url", Map.of("url", url)
            );
            case "dashscope" -> Map.of(
                "type", "image",
                "image", url
            );
            case "claude" -> Map.of(
                "type", "image",
                "source", Map.of(
                    "type", "url",
                    "url", url
                )
            );
            default -> Map.of(
                "type", "image_url",
                "url", url
            );
        };
    }
    
    @Override
    public boolean supports(String provider) {
        // 支持所有可访问公网 URL 的 Provider
        return true;
    }
    
    @Override
    public int getPriority() {
        return 1; // 最高优先级
    }
}
```

#### 6.5.3 Base64 传输策略实现（降级方案）

```java
package org.apache.ozhera.mind.service.multimodal.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.multimodal.ImageStorageService;
import org.apache.ozhera.mind.service.multimodal.ImageTransferStrategy;
import org.apache.ozhera.mind.service.multimodal.model.ImageRefContent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Base64;
import java.util.Map;

/**
 * Base64 图片传输策略（降级方案）
 * 
 * 使用场景：
 * - LLM 无法访问公网 URL（防火墙限制）
 * - 内网部署且 LLM 也在内网
 * 
 * 配置启用：agent.multimodal.transfer-strategy=base64
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "agent.multimodal.transfer-strategy", havingValue = "base64")
public class Base64ImageTransferStrategy implements ImageTransferStrategy {
    
    @Resource
    private ImageStorageService storageService;
    
    @Override
    public Object convertForLLM(ImageRefContent imageRef, String provider) {
        log.debug("Using Base64 strategy for image: {}", imageRef.getImageId());
        
        // 下载图片
        byte[] imageData = storageService.download(imageRef.getImageId());
        
        // 转换为 Base64
        String base64 = Base64.getEncoder().encodeToString(imageData);
        
        // 根据不同 Provider 格式化
        return formatForProvider(base64, imageRef.getMimeType(), provider);
    }
    
    private Object formatForProvider(String base64, String mimeType, String provider) {
        String dataUrl = "data:" + mimeType + ";base64," + base64;
        
        return switch (provider.toLowerCase()) {
            case "openai" -> Map.of(
                "type", "image_url",
                "image_url", Map.of("url", dataUrl)
            );
            case "dashscope" -> Map.of(
                "type", "image",
                "image", dataUrl
            );
            case "claude" -> Map.of(
                "type", "image",
                "source", Map.of(
                    "type", "base64",
                    "media_type", mimeType,
                    "data", base64
                )
            );
            default -> Map.of(
                "type", "image",
                "data", dataUrl
            );
        };
    }
    
    @Override
    public boolean supports(String provider) {
        return true; // Base64 通用支持所有 Provider
    }
    
    @Override
    public int getPriority() {
        return 10; // 较低优先级，作为降级方案
    }
}
```

### 6.6 策略选择逻辑

```java
/**
 * 选择最优传输策略
 */
private ImageTransferStrategy selectStrategy(String provider) {
    return transferStrategies.stream()
        .filter(s -> s.supports(provider))
        .min(Comparator.comparingInt(ImageTransferStrategy::getPriority))
        .orElseThrow(() -> new RuntimeException("No strategy found for: " + provider));
}

// 使用示例：
// 1. 默认配置（URL 策略）：getPriority() = 1 → 优先选择
// 2. 显式配置 Base64：只有 Base64ImageTransferStrategy 被加载
```

### 6.7 内网部署配置

内网部署时的推荐配置：

```yaml
agent:
  multimodal:
    # 存储类型：minio（内网）
    storage:
      type: minio
      endpoint: http://minio.internal:9000
      bucket: hera-mind
    
    # 传输策略：仍然优先 URL
    # 内网 LLM（如 Qwen-VL）通常可以访问内网 MinIO URL
    transfer-strategy: url
    
    # 如果内网 LLM 也无法访问 MinIO，则用 Base64
    # transfer-strategy: base64
```

### 6.7 多模态服务

```java
package org.apache.ozhera.mind.service.multimodal;

import lombok.extern.slf4j.Slf4j;
import org.apache.ozhera.mind.service.multimodal.model.ContentBlock;
import org.apache.ozhera.mind.service.multimodal.model.ImageRefContent;
import org.apache.ozhera.mind.service.multimodal.model.TextContent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.*;

/**
 * 多模态处理服务
 */
@Slf4j
@Service
public class MultiModalService {
    
    private static final long MAX_IMAGE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    
    @Resource
    private ImageStorageService storageService;
    
    @Resource
    private ImagePreprocessor preprocessor;
    
    @Resource
    private List<ImageTransferStrategy> transferStrategies;
    
    /**
     * 处理用户上传的图片
     */
    public ImageRefContent handleImageUpload(MultipartFile file, String username) {
        // 验证
        validateImage(file);
        
        try {
            // 预处理（压缩等）
            byte[] processed = preprocessor.preprocess(
                file.getBytes(), 
                file.getContentType()
            );
            
            // 上传存储
            return storageService.upload(
                processed,
                file.getOriginalFilename(),
                file.getContentType(),
                username
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to process image", e);
        }
    }
    
    /**
     * 构建多模态消息内容
     */
    public List<ContentBlock> buildContent(String text, List<ImageRefContent> images) {
        List<ContentBlock> content = new ArrayList<>();
        
        if (text != null && !text.isEmpty()) {
            content.add(new TextContent(text));
        }
        
        if (images != null) {
            content.addAll(images);
        }
        
        return content;
    }
    
    /**
     * 转换消息为 LLM 格式
     */
    public List<Object> convertForLLM(List<ContentBlock> content, String provider) {
        List<Object> result = new ArrayList<>();
        
        for (ContentBlock block : content) {
            if (block instanceof TextContent text) {
                result.add(Map.of("type", "text", "text", text.getText()));
            } else if (block instanceof ImageRefContent imageRef) {
                ImageTransferStrategy strategy = findStrategy(provider);
                result.add(strategy.convertForLLM(imageRef, provider));
            }
        }
        
        return result;
    }
    
    private void validateImage(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Image file is empty");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException("Image size exceeds limit: " + MAX_IMAGE_SIZE);
        }
        if (!SUPPORTED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Unsupported image type: " + file.getContentType());
        }
    }
    
    private ImageTransferStrategy findStrategy(String provider) {
        return transferStrategies.stream()
            .filter(s -> s.supports(provider))
            .min(Comparator.comparingInt(ImageTransferStrategy::getOrder))
            .orElseThrow(() -> new UnsupportedOperationException(
                "No transfer strategy for provider: " + provider));
    }
}
```

### 6.8 配置

```yaml
agent:
  multimodal:
    enabled: true
    
    # 存储配置
    storage:
      type: oss                    # oss / minio / local
      bucket: hera-mind
      prefix: mind/images
      url-expire-hours: 24
    
    # 传输策略
    transfer-strategy: base64      # base64 / url / auto
    
    # 图片限制
    image:
      max-size: 10MB
      max-count-per-message: 5
      supported-types:
        - image/jpeg
        - image/png
        - image/gif
        - image/webp
      
      # 压缩配置
      compress:
        enabled: true
        threshold: 4MB
        target-size: 2MB
        quality: 0.85
        max-dimension: 2048
```

---

## 7. 前端交互协议

### 7.1 设计目标

1. Agent 返回结构化数据，前端可以渲染表格、图表等
2. Agent 可以触发前端动作（跳转、刷新等）
3. 支持流式输出中的结构化内容
4. 前端能正确解析和渲染

### 7.2 响应格式定义

#### 7.2.1 响应块类型

```java
/**
 * 响应块接口
 */
public interface ResponseBlock {
    String getType();
}

/**
 * 文本块
 */
@Data
public class TextBlock implements ResponseBlock {
    private final String type = "text";
    private String content;
    private String format = "markdown";  // plain / markdown
}

/**
 * 数据块（前端渲染）
 */
@Data
@Builder
public class DataBlock implements ResponseBlock {
    private final String type = "data";
    
    /**
     * 数据类型：table / card / list / chart
     */
    private String dataType;
    
    /**
     * 标题
     */
    private String title;
    
    /**
     * 数据内容
     */
    private Object data;
    
    /**
     * 渲染选项
     */
    private Map<String, Object> renderOptions;
}

/**
 * 动作块（前端执行）
 */
@Data
@Builder
public class ActionBlock implements ResponseBlock {
    private final String type = "action";
    
    /**
     * 动作类型：navigate / refresh / notify / copy / download
     */
    private String actionType;
    
    /**
     * 动作参数
     */
    private Map<String, Object> params;
    
    /**
     * 是否自动执行
     */
    private boolean autoExecute;
    
    /**
     * 确认提示文字（autoExecute=false 时显示）
     */
    private String confirmText;
}
```

#### 7.2.2 Tool 返回结果

```java
/**
 * Tool 返回结果
 */
@Data
@Builder
public class ToolResult {
    
    /**
     * 执行状态
     */
    private boolean success;
    
    /**
     * 文字消息
     */
    private String message;
    
    /**
     * 结构化数据
     */
    private DataBlock data;
    
    /**
     * 前端动作
     */
    private ActionBlock action;
    
    // 工厂方法
    public static ToolResult success(String message) {
        return ToolResult.builder()
            .success(true)
            .message(message)
            .build();
    }
    
    public static ToolResult successWithData(String message, DataBlock data) {
        return ToolResult.builder()
            .success(true)
            .message(message)
            .data(data)
            .build();
    }
    
    public static ToolResult successWithAction(String message, ActionBlock action) {
        return ToolResult.builder()
            .success(true)
            .message(message)
            .action(action)
            .build();
    }
    
    public static ToolResult successWithDataAndAction(String message, DataBlock data, ActionBlock action) {
        return ToolResult.builder()
            .success(true)
            .message(message)
            .data(data)
            .action(action)
            .build();
    }
    
    public static ToolResult error(String message) {
        return ToolResult.builder()
            .success(false)
            .message(message)
            .build();
    }
}
```

### 7.3 数据类型示例

#### 7.3.1 表格数据

```json
{
  "type": "data",
  "dataType": "table",
  "title": "日志存储列表",
  "data": {
    "columns": [
      { "key": "name", "title": "存储名称", "width": 150 },
      { "key": "type", "title": "类型", "width": 80 },
      { "key": "size", "title": "大小", "width": 100 },
      { "key": "status", "title": "状态", "width": 80 }
    ],
    "rows": [
      { "id": 1, "name": "app-log", "type": "ES", "size": "120GB", "status": "active" },
      { "id": 2, "name": "error-log", "type": "ES", "size": "45GB", "status": "active" }
    ]
  },
  "renderOptions": {
    "showIndex": true,
    "rowClickable": true,
    "rowClickAction": {
      "type": "navigate",
      "target": "/log/store/{id}"
    }
  }
}
```

#### 7.3.2 卡片数据

```json
{
  "type": "data",
  "dataType": "card",
  "title": "日志空间详情",
  "data": {
    "id": 123,
    "name": "test-space",
    "description": "测试空间",
    "createdAt": "2024-01-15 10:30:00",
    "creator": "admin",
    "storeCount": 3
  },
  "renderOptions": {
    "layout": "vertical",
    "fields": [
      { "key": "name", "label": "名称" },
      { "key": "description", "label": "描述" },
      { "key": "createdAt", "label": "创建时间" },
      { "key": "creator", "label": "创建人" }
    ]
  }
}
```

#### 7.3.3 图表数据

```json
{
  "type": "data",
  "dataType": "chart",
  "title": "错误日志趋势",
  "data": {
    "chartType": "line",
    "xAxis": ["01-09", "01-10", "01-11", "01-12", "01-13"],
    "series": [
      {
        "name": "ERROR",
        "data": [120, 150, 80, 200, 180],
        "color": "#ff4d4f"
      },
      {
        "name": "WARN",
        "data": [300, 280, 350, 400, 320],
        "color": "#faad14"
      }
    ]
  },
  "renderOptions": {
    "height": 300,
    "showLegend": true,
    "smooth": true
  }
}
```

### 7.4 动作类型定义

| 动作类型 | 说明 | 参数 |
|---------|------|------|
| `navigate` | 页面跳转 | `target`: 目标路径, `title`: 页面标题 |
| `open_tab` | 新标签页打开 | `url`: 目标 URL |
| `refresh` | 刷新组件 | `component`: 组件名称 |
| `notify` | 显示通知 | `message`: 消息, `type`: success/warning/error |
| `copy` | 复制到剪贴板 | `content`: 复制内容, `message`: 提示文字 |
| `download` | 下载文件 | `url`: 下载地址, `filename`: 文件名 |
| `callback` | 执行前端方法 | `method`: 方法名, `args`: 参数 |

### 7.5 流式输出中的结构化内容

#### 7.5.1 标记设计

```
普通文本直接输出

结构化数据使用特殊标记包裹：
<<<DATA_BLOCK>>>{"type":"data",...}<<</DATA_BLOCK>>>

动作指令使用特殊标记包裹：
<<<ACTION_BLOCK>>>{"type":"action",...}<<</ACTION_BLOCK>>>
```

#### 7.5.2 格式化工具

```java
/**
 * 流式响应格式化工具
 */
@Component
public class StreamingResponseFormatter {
    
    private static final String DATA_BLOCK_START = "<<<DATA_BLOCK>>>";
    private static final String DATA_BLOCK_END = "<<</DATA_BLOCK>>>";
    private static final String ACTION_BLOCK_START = "<<<ACTION_BLOCK>>>";
    private static final String ACTION_BLOCK_END = "<<</ACTION_BLOCK>>>";
    
    private final Gson gson = new Gson();
    
    /**
     * 格式化数据块
     */
    public String formatDataBlock(DataBlock block) {
        return DATA_BLOCK_START + gson.toJson(block) + DATA_BLOCK_END;
    }
    
    /**
     * 格式化动作块
     */
    public String formatActionBlock(ActionBlock block) {
        return ACTION_BLOCK_START + gson.toJson(block) + ACTION_BLOCK_END;
    }
    
    /**
     * 格式化 Tool 结果
     */
    public String formatToolResult(ToolResult result) {
        StringBuilder sb = new StringBuilder();
        
        // 文字消息
        if (result.getMessage() != null) {
            sb.append(result.getMessage()).append("\n\n");
        }
        
        // 数据块
        if (result.getData() != null) {
            sb.append(formatDataBlock(result.getData())).append("\n\n");
        }
        
        // 动作块
        if (result.getAction() != null) {
            sb.append(formatActionBlock(result.getAction()));
        }
        
        return sb.toString();
    }
}
```

### 7.6 Tool 使用示例

```java
@Service
public class LogToolService {
    
    @Resource
    private StreamingResponseFormatter formatter;
    
    @Tool(name = "createLogSpace", description = "创建日志空间")
    public String createSpace(
            @ToolParam(name = "spaceName") String spaceName,
            @ToolParam(name = "description") String description) {
        
        // 执行创建
        LogSpace space = logSpaceService.create(spaceName, description);
        
        // 构建结构化结果
        ToolResult result = ToolResult.successWithDataAndAction(
            "✅ 已成功创建日志空间 **" + spaceName + "**",
            
            // 数据卡片
            DataBlock.builder()
                .dataType("card")
                .title("日志空间详情")
                .data(Map.of(
                    "id", space.getId(),
                    "name", space.getName(),
                    "description", space.getDescription(),
                    "createdAt", space.getCreatedAt()
                ))
                .build(),
            
            // 跳转动作
            ActionBlock.builder()
                .actionType("navigate")
                .params(Map.of(
                    "target", "/log/space/" + space.getId(),
                    "title", "日志空间管理"
                ))
                .autoExecute(false)
                .confirmText("是否跳转到日志空间管理页面？")
                .build()
        );
        
        return formatter.formatToolResult(result);
    }
    
    @Tool(name = "getStoresInSpace", description = "查询日志存储列表")
    public String getStores(@ToolParam(name = "spaceId") Long spaceId) {
        
        List<LogStore> stores = logStoreService.listBySpace(spaceId);
        
        ToolResult result = ToolResult.successWithData(
            "找到 " + stores.size() + " 个日志存储",
            
            DataBlock.builder()
                .dataType("table")
                .title("日志存储列表")
                .data(Map.of(
                    "columns", List.of(
                        Map.of("key", "name", "title", "存储名称"),
                        Map.of("key", "type", "title", "类型"),
                        Map.of("key", "size", "title", "大小"),
                        Map.of("key", "status", "title", "状态")
                    ),
                    "rows", stores.stream().map(this::toRow).toList()
                ))
                .renderOptions(Map.of(
                    "rowClickable", true,
                    "rowClickAction", Map.of(
                        "type", "navigate",
                        "target", "/log/store/{id}"
                    )
                ))
                .build()
        );
        
        return formatter.formatToolResult(result);
    }
}
```

### 7.7 前端解析方案

```
前端处理流程（状态机）：

状态：
  - TEXT：正常文本流，逐字渲染
  - DATA_BUFFERING：遇到 <<<DATA_BLOCK>>>，缓冲中
  - ACTION_BUFFERING：遇到 <<<ACTION_BLOCK>>>，缓冲中

流程：
1. 默认 TEXT 状态，逐字渲染到聊天框
2. 遇到 <<<DATA_BLOCK>>> → 切换到 DATA_BUFFERING
   - 显示 Loading 占位符
   - 缓冲数据直到 <<</DATA_BLOCK>>>
   - 解析 JSON，渲染对应组件（表格/卡片/图表）
   - 回到 TEXT 状态
3. 遇到 <<<ACTION_BLOCK>>> → 切换到 ACTION_BUFFERING
   - 缓冲数据直到 <<</ACTION_BLOCK>>>
   - 解析 JSON
   - autoExecute=true → 自动执行
   - autoExecute=false → 显示确认按钮
   - 回到 TEXT 状态
4. 解析失败 → 显示原始内容，降级处理
```

---

## 8. 数据存储设计

### 8.1 存储架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          存储层架构                                  │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     Caffeine (JVM 热缓存)                    │   │
│  │                                                             │   │
│  │  memoryCache[username]  → AutoContextMemory                 │   │
│  │  sessionCache[username] → SwarmSession                      │   │
│  │  agentCache[username]   → ReActAgent                        │   │
│  │                                                             │   │
│  │  特点：最快访问，15分钟过期，最多500用户                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              │ 驱逐时持久化                          │
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     Redis (分布式缓存)                        │   │
│  │                                                             │   │
│  │  hera:mind:memory:{username}  → Memory 状态（压缩序列化）    │   │
│  │  hera:mind:session:{username} → Session 状态                │   │
│  │                                                             │   │
│  │  特点：跨实例共享，重启可恢复，24小时过期                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              │ 定期同步                             │
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     MySQL (永久存储)                          │   │
│  │                                                             │   │
│  │  chat_message   → 对话历史（用于前端展示和恢复）              │   │
│  │  user_config    → 用户配置（API Key、模型选择等）            │   │
│  │  image_record   → 图片记录（元数据，实际文件在 OSS）         │   │
│  │                                                             │   │
│  │  特点：永久存储，完整历史                                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                     OSS/MinIO (文件存储)                      │   │
│  │                                                             │   │
│  │  mind/images/{username}/{imageId}.jpg → 用户上传的图片       │   │
│  │                                                             │   │
│  │  特点：大文件存储，7天自动清理                               │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 缓存键设计

| 缓存 | Key 格式 | Value | TTL |
|-----|---------|-------|-----|
| Caffeine memoryCache | `{username}` | AutoContextMemory | 15 分钟访问过期 |
| Caffeine sessionCache | `{username}` | SwarmSession | 15 分钟访问过期 |
| Caffeine agentCache | `{username}` | ReActAgent | 15 分钟访问过期 |
| Redis memory | `hera:mind:memory:{username}` | 压缩的 Memory 状态 | 24 小时 |
| Redis session | `hera:mind:session:{username}` | Session JSON | 24 小时 |

### 8.3 Memory 恢复流程

```
用户请求到达
      │
      ▼
┌─────────────────────────────────────────┐
│  检查 Caffeine memoryCache              │
│  memoryCache.getIfPresent(username)     │
└─────────────────────────┬───────────────┘
                          │
           ┌──────────────┴──────────────┐
           │                             │
        有缓存                         无缓存
           │                             │
           ▼                             ▼
      直接返回            ┌──────────────────────────────┐
                          │  尝试从 Redis 恢复            │
                          │  memoryStateService.loadState │
                          └──────────────────┬───────────┘
                                             │
                              ┌──────────────┴──────────────┐
                              │                             │
                           有状态                         无状态
                              │                             │
                              ▼                             ▼
                       恢复 Memory            ┌───────────────────────────┐
                                              │  从 MySQL 加载历史消息     │
                                              │  chatMessageService       │
                                              │    .getRecentMessages(50) │
                                              └──────────────┬────────────┘
                                                             │
                                                             ▼
                                                      重建 Memory
                                                             │
                                                             ▼
                                                    放入 Caffeine 缓存
```

### 8.4 数据库表设计

```sql
-- 对话消息表
CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'USER/ASSISTANT',
    content TEXT NOT NULL,
    agent_name VARCHAR(50) COMMENT '处理该消息的 Agent',
    has_image TINYINT DEFAULT 0 COMMENT '是否包含图片',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_username_created (username, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 图片记录表
CREATE TABLE image_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    image_id VARCHAR(50) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    size BIGINT NOT NULL,
    width INT,
    height INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME COMMENT '过期时间',
    INDEX idx_username (username),
    INDEX idx_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户配置表（现有）
CREATE TABLE user_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL UNIQUE,
    provider VARCHAR(50) NOT NULL COMMENT 'openai/dashscope/...',
    api_key VARCHAR(255) NOT NULL,
    model_name VARCHAR(100),
    base_url VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 9. 配置管理

### 9.1 完整配置示例

```yaml
# ==================== 服务配置 ====================
server:
  port: 8080

spring:
  application:
    name: hera-mind-worker
  
  # 虚拟线程
  threads:
    virtual:
      enabled: true
  
  # 数据源
  datasource:
    url: jdbc:mysql://localhost:3306/hera_mind
    username: root
    password: root
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
  
  # Redis
  redis:
    host: localhost
    port: 6379
    lettuce:
      pool:
        max-active: 100
        max-idle: 50
        min-idle: 10

# ==================== Agent 配置 ====================
agent:
  # 缓存配置
  cache:
    max-size: 500              # 最大缓存用户数
    expire-minutes: 15         # 缓存过期时间
  
  # Memory 配置
  memory:
    msg-threshold: 20          # 触发压缩的消息数阈值
    max-token: 128000          # 模型最大 token
    token-ratio: 0.8           # 实际使用比例
    last-keep: 5               # 压缩时保留最近消息数
    large-payload-threshold: 5000
  
  # 历史加载
  history:
    load-size: 50              # 从 MySQL 加载的历史条数
  
  # Swarm 配置
  swarm:
    default-agent: CommonAgent # 默认入口 Agent
    max-handoff-depth: 3       # 最大 Handoff 深度
  
  # 并发控制
  concurrency:
    max-concurrent-requests: 1000   # 最大并发请求数
    global-acquire-timeout: 10s     # 全局许可获取超时
    user-lock-timeout: 30s          # 用户锁超时
    request-timeout: 2m             # 请求超时
  
  # LLM API 限流
  rate-limit:
    openai-rpm: 60
    dashscope-rpm: 100
    claude-rpm: 50
    default-rpm: 50
  
  # 多模态配置
  multimodal:
    enabled: true
    
    storage:
      type: oss                     # oss / minio / local
      bucket: hera-mind
      prefix: mind/images
      url-expire-hours: 24
    
    transfer-strategy: base64       # base64 / url / auto
    
    image:
      max-size: 10MB
      max-count-per-message: 5
      supported-types:
        - image/jpeg
        - image/png
        - image/gif
        - image/webp
      compress:
        enabled: true
        threshold: 4MB
        target-size: 2MB
        quality: 0.85
        max-dimension: 2048

# ==================== OSS 配置 ====================
aliyun:
  oss:
    endpoint: oss-cn-hangzhou.aliyuncs.com
    access-key-id: ${OSS_ACCESS_KEY_ID}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET}

# ==================== Nacos 配置 ====================
nacos:
  config:
    server-addr: localhost:8848
    namespace: hera-mind
    group: DEFAULT_GROUP
```

### 9.2 配置分层

| 层级 | 位置 | 说明 |
|-----|------|------|
| 默认配置 | application.yml | 框架默认值 |
| 环境配置 | application-{env}.yml | 环境特定配置 |
| Nacos 配置 | nacos 配置中心 | 动态配置，支持热更新 |
| 环境变量 | 系统环境变量 | 敏感信息（API Key 等） |

---

## 10. 潜在问题与解决方案

### 10.1 Handoff 相关问题

| 问题 | 场景 | 解决方案 |
|-----|------|---------|
| **无限循环** | Agent A → B → A → B... | `SwarmContext.visitedAgents` 检测循环，同一 Agent 不能访问两次 |
| **Handoff 过深** | 超过 3 次 Handoff | `maxHandoffDepth` 限制，超限后强制当前 Agent 总结回答 |
| **目标 Agent 不存在** | Handoff 到未注册的 Agent | `AgentRegistry.hasAgent()` 检查，不存在则忽略 Handoff |
| **Handoff 解析失败** | Tool 返回格式错误 | try-catch 包裹解析，失败则视为普通文本 |
| **上下文丢失** | Handoff 后目标 Agent 不理解 | `SwarmContext.getHandoffSummary()` 生成上下文摘要 |

### 10.2 并发相关问题

| 问题 | 场景 | 解决方案 |
|-----|------|---------|
| **同一用户并发** | 用户快速连续发送消息 | `UserLockManager` 用户级 Semaphore，串行执行 |
| **系统过载** | 大量用户同时请求 | `ConcurrencyLimiter` 全局信号量限制 |
| **LLM API 限流** | 触发外部 API 429 | `LLMRateLimiter` 按 Provider 限流 |
| **请求超时** | Agent 执行时间过长 | 2 分钟超时限制，虚拟线程自动中断 |
| **缓存竞争** | 并发访问同一用户缓存 | Caffeine 线程安全，`computeIfAbsent` 原子操作 |

### 10.3 内存相关问题

| 问题 | 场景 | 解决方案 |
|-----|------|---------|
| **Memory 过大** | 对话历史积累 | AutoContextMemory 自动压缩，token 限制 |
| **图片占用内存** | 图片存 Memory | 只存 ImageRefContent 引用（~200 字节），实际图片在 OSS |
| **缓存膨胀** | 用户数增长 | Caffeine `maximumSize` 限制，LRU 驱逐 |
| **Agent 实例过多** | 每用户多个 Agent | 只缓存当前活跃 Agent，Handoff 时替换 |

### 10.4 流式输出问题

| 问题 | 场景 | 解决方案 |
|-----|------|---------|
| **结构化块截断** | 流式传输中块不完整 | 前端状态机缓冲，直到遇到结束标记 |
| **标记混乱** | 普通文本包含标记字符 | 使用特殊前缀 `<<<` 和 `>>>` 降低冲突 |
| **解析失败** | JSON 格式错误 | 降级显示原始内容 |
| **Sink 关闭** | 异步执行后 Sink 已关闭 | `tryEmitNext` 检查 Sink 状态 |

### 10.5 多模态问题

| 问题 | 场景 | 解决方案 |
|-----|------|---------|
| **图片过大** | 上传超大图片 | 前端 + 后端双重压缩，限制 10MB |
| **格式不支持** | 上传 BMP/TIFF 等 | 白名单限制支持的 MIME 类型 |
| **外网 LLM 无法访问内网 URL** | 使用外部 API | Base64 传输策略，临时下载转码 |
| **图片堆积** | 图片不清理 | 定时任务清理过期图片（7 天） |

### 10.6 Agent 执行问题

| 问题 | 场景 | 解决方案 |
|-----|------|---------|
| **Tool 执行失败** | 下游服务异常 | Tool 内 try-catch，返回友好错误信息 |
| **LLM 幻觉** | 调用不存在的 Tool | AgentScope 框架内置 Tool 验证 |
| **System Prompt 过长** | Agent Prompt 太复杂 | 精简 Prompt，核心要点在前 |
| **用户上下文丢失** | 异步执行线程切换 | `UserContext` ThreadLocal + `doOnSubscribe` 设置 |

---

## 11. 实施计划

### 11.1 阶段划分

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            实施阶段                                          │
│                                                                             │
│  Phase 1: 基础架构 (1-2 周)                                                  │
│  ├─ SwarmContext, SwarmSession, HandoffResult                               │
│  ├─ HeraAgent 接口定义                                                      │
│  ├─ AgentRegistry                                                           │
│  └─ 重构现有 LogAgent                                                       │
│                                                                             │
│  Phase 2: CommonAgent + Handoff (1 周)                                       │
│  ├─ CommonAgent 实现                                                        │
│  ├─ CommonAgentHandoffs                                                     │
│  ├─ LogAgentHandoffs                                                        │
│  ├─ SwarmExecutor                                                           │
│  └─ SwarmAgentService（基础版）                                              │
│                                                                             │
│  Phase 3: 高并发 (1 周)                                                      │
│  ├─ VirtualThreadConfig                                                     │
│  ├─ ConcurrencyLimiter                                                      │
│  ├─ UserLockManager                                                         │
│  ├─ LLMRateLimiter                                                          │
│  └─ 集成测试                                                                │
│                                                                             │
│  Phase 4: 多模态 (1-2 周)                                                    │
│  ├─ ImageStorageService + OSS 实现                                          │
│  ├─ ImageTransferStrategy + Base64 实现                                     │
│  ├─ ImagePreprocessor                                                       │
│  ├─ MultiModalService                                                       │
│  └─ 前端上传功能                                                            │
│                                                                             │
│  Phase 5: 前端交互 (1 周)                                                    │
│  ├─ ResponseBlock 体系                                                      │
│  ├─ ToolResult 改造                                                         │
│  ├─ StreamingResponseFormatter                                              │
│  ├─ LogToolService 改造                                                     │
│  └─ 前端解析器                                                              │
│                                                                             │
│  Phase 6: 新增 Agent (2-3 周)                                                │
│  ├─ TraceAgent + TraceToolService                                           │
│  ├─ TraceAgentHandoffs                                                      │
│  ├─ MonitorAgent + MonitorToolService                                       │
│  ├─ MonitorAgentHandoffs                                                    │
│  └─ 集成测试                                                                │
│                                                                             │
│  Phase 7: 优化与上线 (1-2 周)                                                │
│  ├─ 性能测试                                                                │
│  ├─ 监控指标接入                                                            │
│  ├─ 日志完善                                                                │
│  ├─ 文档编写                                                                │
│  └─ 灰度发布                                                                │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 11.2 各阶段交付物

| 阶段 | 交付物 | 验收标准 |
|-----|--------|---------|
| Phase 1 | 基础架构代码 | 接口定义完整，现有功能不受影响 |
| Phase 2 | CommonAgent + Handoff | 闲聊正常，可 Handoff 到 LogAgent |
| Phase 3 | 高并发组件 | 1000 并发压测通过 |
| Phase 4 | 多模态功能 | 图片上传、识别正常工作 |
| Phase 5 | 前端交互 | 表格/卡片渲染，跳转动作正常 |
| Phase 6 | 新增 Agent | Trace/Monitor Agent 功能可用 |
| Phase 7 | 生产就绪 | 监控完善，灰度上线 |

### 11.3 风险与应对

| 风险 | 影响 | 应对措施 |
|-----|------|---------|
| AgentScope 版本兼容 | 可能需要适配新版本 | 锁定版本，关注更新 |
| LLM 幻觉问题 | Handoff 判断不准确 | 优化 Prompt，添加示例 |
| 前端改动较大 | 联调周期长 | 提前定义协议，并行开发 |
| 性能不达标 | 上线受阻 | 提前压测，预留优化时间 |

---

## 附录

### A. 参考资料

- [OpenAI Swarm](https://github.com/openai/swarm) - Swarm 架构参考
- [AgentScope 文档](https://github.com/modelscope/agentscope) - Agent 框架
- [Project Reactor](https://projectreactor.io/) - 响应式编程
- [Java 21 Virtual Threads](https://openjdk.org/jeps/444) - 虚拟线程

### B. 术语表

| 术语 | 说明 |
|-----|------|
| Swarm | 去中心化多 Agent 协作架构 |
| Handoff | Agent 间任务移交机制 |
| ReActAgent | AgentScope 中的推理+行动 Agent |
| AutoContextMemory | 自动管理上下文的 Memory 组件 |
| Virtual Thread | Java 21 轻量级虚拟线程 |
| WebFlux | Spring 响应式 Web 框架 |

### C. 变更记录

| 版本 | 日期 | 变更内容 | 作者 |
|-----|------|---------|------|
| v1.0 | 2024-01 | 初始版本 | - |
