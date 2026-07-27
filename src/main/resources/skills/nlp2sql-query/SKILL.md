---
name: nlp2sql-query
description: >
  将自然语言业务问题转为语义DSL并查询数据库。
  适用于指标查询、维度分析、明细数据查询。
  当用户询问成绩、人数、平均分、对比、明细等业务数据时使用本 skill。
---

# NLP2SQL 业务查询

加载本 skill 后，按顺序调用业务工具（每次只调一个）：

1. `classify_intent` — 识别意图（METRIC_QUERY / DIMENSION_ANALYSIS / DETAIL_QUERY / NON_BUSINESS）
2. 若为 NON_BUSINESS：直接自然语言回答，不再调用后续工具
3. `retrieve_metadata` — 检索候选元数据
4. `generate_dsl` — 生成语义 DSL（传入 question + intent）
5. `validate_dsl` — 校验 DSL；失败则修正后重试 generate/validate
6. `enrich_dsl` — 富化 DSL（物理表/JOIN）
7. `translate_sql` — 翻译为参数化 SQL
8. `review_sql` — 审查 SQL
9. `execute_sql` — 执行查询
10. 基于查询结果用自然语言给出结论（不要再调工具）

## 规则

- metric/entity/dimensions/filters 必须来自候选元数据，禁止编造
- 不要调用与业务无关的内置工具（文件、Shell 等）
- 最终回答只输出结论本身
