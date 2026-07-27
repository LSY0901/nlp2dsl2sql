# Rule-First Intent Classification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 业务意图先走关键词规则；唯一命中则跳过 LLM，否则回退 LLM；V2 与 ReAct 统一经 `IntentTool`。

**Architecture:** 新增 `RuleIntentClassifier.tryMatch`；`IntentTool.classify` 先规则后 LLM；`SemanticDslAgentServiceImpl.classifyIntent` 委托 `IntentTool`。

**Tech Stack:** Java 21、Spring `@Component`、JUnit 5

**Spec:** `docs/superpowers/specs/2026-07-27-rule-first-intent-design.md`

---

## File Structure

```
src/main/java/org/example/nlp2dsl2sql/intent/
  RuleIntentClassifier.java          # 新建：规则匹配
src/main/java/org/example/nlp2dsl2sql/tools/
  IntentTool.java                    # 修改：规则优先
src/main/java/org/example/nlp2dsl2sql/service/impl/
  SemanticDslAgentServiceImpl.java   # 修改：委托 IntentTool
src/test/java/org/example/nlp2dsl2sql/intent/
  RuleIntentClassifierTest.java      # 新建
```

---

### Task 1: RuleIntentClassifier（TDD）

**Files:**
- Create: `src/test/java/org/example/nlp2dsl2sql/intent/RuleIntentClassifierTest.java`
- Create: `src/main/java/org/example/nlp2dsl2sql/intent/RuleIntentClassifier.java`

- [ ] **Step 1: 写失败测试**

覆盖：
- `三年级数学平均分是多少` → METRIC_QUERY
- `各年级数学平均分对比` → DIMENSION_ANALYSIS
- `列出三年级学生成绩` → DETAIL_QUERY
- `你好` → NON_BUSINESS
- `对比一下并列出明细` → empty
- `null` / `""` → empty

断言：`isPresent()`、`get().getIntent()`、confidence≈0.9

- [ ] **Step 2: 跑测确认失败**

```bash
mvn -q -Dtest=RuleIntentClassifierTest test
```

Expected: 编译失败或测试失败

- [ ] **Step 3: 实现 RuleIntentClassifier**

```java
package org.example.nlp2dsl2sql.intent;

import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 高置信关键词意图规则：唯一命中才返回结果，否则 empty 交 LLM。
 */
@Component
public class RuleIntentClassifier {

    private static final Set<String> CHITCHAT = Set.of(
            "你好", "您好", "你是谁", "谢谢", "天气");
    private static final Set<String> DIMENSION = Set.of(
            "对比", "比较", "分别", "各年级", "各个", "同比", "环比");
    private static final Set<String> DETAIL = Set.of(
            "列出", "明细", "清单", "有哪些学生", "详细列表", "逐条");
    private static final Set<String> METRIC = Set.of(
            "是多少", "有多少", "平均分", "总分", "最高分", "最低分", "数量");
    private static final Set<String> BUSINESS = Set.of(
            "平均分", "总分", "最高分", "多少", "对比", "列出", "明细",
            "学生", "成绩", "年级");

    /**
     * 尝试用规则识别意图。
     *
     * @param question 用户问题
     * @return 唯一命中时的结果；未命中或冲突为空
     */
    public Optional<IntentResult> tryMatch(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String q = question.trim().toLowerCase(Locale.ROOT);
        // 中文无大小写，toLowerCase 仍安全；匹配用原文 contains
        String text = question.trim();

        List<Hit> hits = new ArrayList<>();
        if (matchChitchat(text)) {
            hits.add(hit(IntentResult.IntentType.NON_BUSINESS, "闲聊关键词"));
        }
        String dimKw = firstKeyword(text, DIMENSION);
        if (dimKw != null) {
            hits.add(hit(IntentResult.IntentType.DIMENSION_ANALYSIS, dimKw));
        }
        String detKw = firstKeyword(text, DETAIL);
        if (detKw != null) {
            hits.add(hit(IntentResult.IntentType.DETAIL_QUERY, detKw));
        }
        String metKw = firstKeyword(text, METRIC);
        if (metKw != null) {
            hits.add(hit(IntentResult.IntentType.METRIC_QUERY, metKw));
        }

        // 去掉与业务冲突的闲聊
        hits.removeIf(h ->
                h.type == IntentResult.IntentType.NON_BUSINESS
                        && containsAny(text, BUSINESS));

        List<Hit> businessHits = hits.stream()
                .filter(h -> h.type != IntentResult.IntentType.NON_BUSINESS)
                .toList();
        if (businessHits.size() > 1) {
            return Optional.empty();
        }
        if (businessHits.size() == 1) {
            return Optional.of(toResult(businessHits.get(0)));
        }
        if (hits.size() == 1
                && hits.get(0).type == IntentResult.IntentType.NON_BUSINESS) {
            return Optional.of(toResult(hits.get(0)));
        }
        return Optional.empty();
    }

    // firstKeyword / containsAny / matchChitchat / hit / toResult
    // confidence = 0.9, reason = "规则命中: " + keyword
}
```

实现时补全私有方法；行宽 ≤100；方法带注释。  
「按…分布」可用 `text.contains("分布") && text.contains("按")` 作为 DIMENSION 附加条件（可选）。

- [ ] **Step 4: 跑测通过**

```bash
mvn -q -Dtest=RuleIntentClassifierTest test
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/intent/RuleIntentClassifier.java \
  src/test/java/org/example/nlp2dsl2sql/intent/RuleIntentClassifierTest.java
git commit -m "feat: add RuleIntentClassifier for keyword intent matching"
```

---

### Task 2: IntentTool 规则优先

**Files:**
- Modify: `src/main/java/org/example/nlp2dsl2sql/tools/IntentTool.java`

- [ ] **Step 1: 注入 RuleIntentClassifier**

在构造注入中增加 `RuleIntentClassifier`（已有 `@RequiredArgsConstructor`）。

- [ ] **Step 2: 改 classify 入口**

```java
public IntentResult classify(String question) {
    log.info("━━━ [Multi-Agent] IntentTool 启动 ━━━");
    Optional<IntentResult> ruled =
            ruleIntentClassifier.tryMatch(question);
    if (ruled.isPresent()) {
        IntentResult r = ruled.get();
        log.info("[Intent] RULE hit: {} reason={}",
                r.getIntent(), r.getReason());
        return r;
    }
    log.info("[Intent] RULE miss → LLM");
    return classifyByLlm(question);
}
```

将原 LLM 逻辑抽为 `classifyByLlm`（private）。

- [ ] **Step 3: 编译**

```bash
mvn -q -DskipTests compile
```

- [ ] **Step 4: Commit**

```bash
git commit -am "feat: prefer rule intent before LLM in IntentTool"
```

（或精确 git add IntentTool.java）

---

### Task 3: V2 委托 IntentTool

**Files:**
- Modify: `src/main/java/org/example/nlp2dsl2sql/service/impl/SemanticDslAgentServiceImpl.java`

- [ ] **Step 1: 注入 IntentTool**

`private final IntentTool intentTool;`（`@RequiredArgsConstructor` 自动生成）

- [ ] **Step 2: 替换 classifyIntent**

```java
@Override
public IntentResult classifyIntent(String question) {
    return intentTool.classify(question);
}
```

删除本类中仅服务于意图识别的重复 LLM/JSON 代码（若 `callLlm`/`extractJson` 仍被 DSL 等使用则保留）。

- [ ] **Step 3: 编译 + 全量相关测试**

```bash
mvn -q test
```

- [ ] **Step 4: 更新设计文档状态为已批准**

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/nlp2dsl2sql/service/impl/SemanticDslAgentServiceImpl.java \
  docs/superpowers/specs/2026-07-27-rule-first-intent-design.md
git commit -m "refactor: V2 intent classification delegates to IntentTool"
```

---

## Spec Coverage

| Spec | Task |
|------|------|
| RuleIntentClassifier | 1 |
| IntentTool 先规则后 LLM | 2 |
| V2 委托 | 3 |
| 单测用例表 | 1 |
| 不改 A2A / 枚举 | — |

## Placeholder Scan

无 TBD。
