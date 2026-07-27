# 规则优先业务意图识别设计

> 日期：2026-07-27  
> 状态：已批准  
> 目标：业务意图（4 类）先走高置信关键词规则；唯一命中则跳过 LLM，未命中或冲突再调 LLM。  
> 统一入口：`IntentTool`；V2 `classifyIntent` 委托同一实现。

## 1. 背景与目标

### 1.1 现状

| 入口 | 行为 |
|------|------|
| `IntentTool.classify` | 每次 LLM |
| `SemanticDslAgentServiceImpl.classifyIntent` | 每次 LLM（与 IntentTool 逻辑重复） |

意图类型：`METRIC_QUERY` / `DIMENSION_ANALYSIS` / `DETAIL_QUERY` / `NON_BUSINESS`。

### 1.2 目标

1. 规则能**唯一、高置信**识别时，不调用 LLM  
2. 规则未命中或多类冲突时，回退现有 LLM 逻辑  
3. V2 与 ReAct 共用同一套分类，避免双份实现  

### 1.3 已确认决策

| 决策点 | 选择 |
|--------|------|
| 范围 | 仅业务 4 类意图（不含 A2A Host 路由） |
| 规则形态 | 高置信关键词互斥命中 |
| 实现 | 共享 `RuleIntentClassifier` + 统一走 `IntentTool` |

### 1.4 非目标

- 不做 yaml/DB 可配置规则表（可后续演进）
- 不改 A2A Host 拆解逻辑  
- 不改 DSL 生成/校验语义  
- 不强制改造 Planner 内嵌 intent（若未走 IntentTool 则本版不动）

---

## 2. 流程

```
question
  → RuleIntentClassifier.tryMatch(question)
       ├─ Optional 有值（唯一命中）
       │     → IntentResult(intent, confidence=0.9, reason=匹配说明)
       │     → 返回（跳过 LLM）
       └─ Optional 空（未命中或冲突）
             → 现有 LLM + JSON 解析
             → IntentResult
```

日志示例：

```
[Intent] RULE hit: METRIC_QUERY keywords=[平均分]
[Intent] RULE miss → LLM
```

---

## 3. 模块

| 类 | 职责 |
|----|------|
| `org.example.nlp2dsl2sql.intent.RuleIntentClassifier` | 规则匹配；`Optional<IntentResult> tryMatch(String)` |
| `IntentTool` | 先 `tryMatch`，未命中再 LLM；日志标明 RULE/LLM |
| `SemanticDslAgentServiceImpl#classifyIntent` | 委托 `intentTool.classify(question)`，删除重复 LLM 代码 |

建议包：`intent/`（或放 `tools/` 旁，优先独立 `intent` 包便于单测）。

---

## 4. 规则表（首版硬编码）

按优先级顺序检查；**同一问题若命中 ≥2 个业务类（或冲突）则判不确定 → LLM**。  
NON_BUSINESS 仅在「闲聊特征命中且无明显业务词」时成立。

### 4.1 优先级与关键词（示意，实现时可微调同义词）

| 优先级 | 意图 | 正例关键词（含其一即可） | 负向/互斥 |
|--------|------|--------------------------|-----------|
| 1 | NON_BUSINESS | 你好、您好、你是谁、谢谢、天气 | 同时含平均分/多少/对比/列出等业务词则不作 NON_BUSINESS |
| 2 | DIMENSION_ANALYSIS | 对比、比较、分别、各年级、各个、按…分布、同比、环比 | — |
| 3 | DETAIL_QUERY | 列出、明细、清单、有哪些学生、详细列表、逐条 | — |
| 4 | METRIC_QUERY | 是多少、有多少、平均分、总分、最高分、最低分、数量 | 已命中 2/3 则不走本类 |

### 4.2 命中结果字段

- `intent`：枚举名  
- `confidence`：规则固定 `0.9`  
- `reason`：如 `规则命中: 平均分`  

### 4.3 冲突判定

- 同时命中 DIMENSION 与 DETAIL → miss  
- 同时命中 DIMENSION 与 METRIC（且都有强特征）→ miss  
- 仅 METRIC 特征 → hit METRIC  
- 全无特征 → miss → LLM  

---

## 5. 接入点

### 5.1 IntentTool

```text
classify(q):
  opt = ruleClassifier.tryMatch(q)
  if opt.present: log RULE; return opt.get
  else: log RULE miss; return llmClassify(q)  // 现有逻辑
```

### 5.2 V2

`classifyIntent(q)` → `return intentTool.classify(q);`

注入 `IntentTool`（构造器注入）。

### 5.3 ReAct

`AgentToolRegistry.classify_intent` 已调 `IntentTool`，自动受益，无需改工具签名。

---

## 6. 测试

单测 `RuleIntentClassifierTest`（不调 LLM）：

| 输入 | 期望 |
|------|------|
| 三年级数学平均分是多少 | METRIC_QUERY |
| 各年级数学平均分对比 | DIMENSION_ANALYSIS |
| 列出三年级学生成绩 | DETAIL_QUERY |
| 你好 | NON_BUSINESS |
| 对比一下并列出明细（冲突） | empty → 需 LLM |
| 空串 / null | empty 或 NON_BUSINESS（实现选定一种：建议 empty→LLM，LLM 侧再处理空问） |

`IntentTool` 可用 mock 规则命中路径做轻量单测（可选）。

---

## 7. 成功标准

- 常见模板问句 RULE 命中且不调 LLM（日志可证）  
- 冲突/模糊句仍走 LLM  
- V2 与 ReAct 意图结果来源一致  
- 现有管线行为除「少打 LLM」外无语义回归  

---

## 8. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 规则误杀 | 冲突即 miss；关键词保守；日志可观测 |
| 漏召回 | miss 后 LLM 兜底 |
| 关键词膨胀 | 首版硬编码小集合；后续再配置化 |
