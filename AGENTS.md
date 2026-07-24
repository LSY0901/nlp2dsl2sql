# AGENTS.md

> 企业级自然语言数据查询系统上下文文档

## 项目概览

**Nlp2dsl2sql** 基于 Spring Boot + AgentScope 构建，通过**语义层管线**将用户自然语言问题转换为结构化 DSL，再翻译为安全 SQL 执行，以自然语言返回查询结论。核心价值在于用语义层解耦业务语义与物理 SQL，避免 LLM 直接生成 SQL 的幻觉与注入风险。

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.5.15 |
| AI Agent | AgentScope | 2.0.0 |
| ORM | MyBatis-Plus | 3.5.7 |
| 数据库 | PostgreSQL + pgvector | - |
| 向量检索 | pgvector（1024 维） | 0.1.4 |
| 构建 | Maven | - |

## 核心架构

**7 阶段流式管线**（入口：`SemanticDslAgentServiceImpl#nlp2Dsl2SqlAgentV2`）：

```
用户问题 → [1.意图识别 → 2.语义检索 → 3.DSL生成 → 4.DSL校验 → 5.DSL富化 → 6.SQL生成 → 7.SQL审查+执行] → 自然语言回答
```

## 核心架构

```
src/main/java/org/example/nlp2dsl2sql/
├── semanticdsl/                       # ★ 语义层核心模块
│   ├── agent/                         # 管线编排器
│   ├── enricher/                      # DSL富化（BFS JOIN路径）
│   ├── metadata/                      # 元数据服务
│   ├── model/                         # DSL模型定义
│   ├── translator/                    # SQL翻译器
│   └── validator/                     # DSL校验器
├── tools/                             # 工具函数
│   ├── ReviewTool.java                # LLM SQL审查
│   └── SqlExecuteTool.java            # 安全SQL执行
├── controller/                        # API控制器
├── config/                            # 配置类
└── resources/
    ├── application.yaml               # 应用配置
    └── mapper/dsl/                    # MyBatis映射
```

## 数据库模型

PostgreSQL `agent` schema 核心表：
- `dsl_entity`：业务实体（物理表映射）
- `dsl_metric`：业务指标（聚合表达式）
- `dsl_dimension`：查询维度（分组字段）
- `dsl_relation`：实体关系（JOIN 规则）
- `dsl_synonym`：业务同义词（Query Rewrite）

支持向量检索的表采用 1024 维 BGE-M3 编码。

## 外部服务

| 服务 | 地址 | 用途 |
|------|------|------|
| LLM（DeepSeek） | `https://api.deepseek.com` | 意图识别、DSL生成、SQL审查 |
| Embedding | `http://localhost:8082` | BGE-M3 向量化 |
| Rerank | `http://localhost:8083` | 相关性重排 |
| PostgreSQL | `localhost:5432/agent_db` | 元数据 + 业务数据 |

## 关键特性

1. **语义解耦**：LLM 生成语义 DSL，确定性代码翻译为 SQL
2. **向量检索**：BGE-M3 + Rerank 两阶段 Top-K 召回
3. **JOIN 求解**：BFS 自动查找最短 JOIN 路径
4. **SQL 安全**：强制 SELECT-only + 黑名单 + 参数绑定
5. **流式响应**：SSE 推送自然语言回答

## API 接口

| 端点 | 方法 | 说明 |
|------|------|------|
| `/agent/chat` | GET | 基础 Agent 对话 |
| `/aiChat/nlp2Dsl2SqlAgentV2` | GET (SSE) | V2 语义管线 |
| `/aiChat/nlp2Dsl2SqlMultiAgent` | GET (SSE) | 多 Agent 协作 |

## 开发规范

- **包结构**：按职责分层（semanticdsl/tools/controller/config）
- **依赖注入**：构造器注入（`@RequiredArgsConstructor`）
- **日志**：`@Slf4j` + 关键阶段分隔符
- **异常**：`PipelineException` / `MultiAgentException`
- **LLM**：`OpenAIChatModel.stream()` + `jsonObject` 格式
- **实体**：Lombok `@Data` + DSL 元数据实体

## 部署运行

```bash
# 编译打包
./mvnw clean package -DskipTests

# 启动服务
java -jar target/Nlp2dsl2sql-0.0.1-SNAPSHOT.jar
```

访问：`http://localhost:8079/nlp2dsl2sqlV2.html`

## 环境要求

- `agentscope.openai.api-key` 必须配置
- 数据库执行 `ai_agent.sql` 建表
- Embedding/Rerank 服务需先启动
