# ADR-0001：SQL 只能由 DSL 确定性翻译生成，LLM 绝不写 SQL

## 状态

已采纳（项目立身之本）

## 背景

自然语言到 SQL 最常见的做法是让 LLM 直接输出 SQL。这条路在演示里很顺，但一旦进入真实业务就崩：

- 幻觉表名/列名，生成的 SQL 在库里直接报错，而报错点离产生它的自然语言很远；
- 同一句问法两次生成的 SQL 不一样，无法回归测试；
- 无从施加"只允许 SELECT""禁止 N+1 查询""必须走 JOIN 而不许子查询"这类业务约束；
- 业务方要看懂并审核 SQL 时，LLM 的自由发挥让语义层形同虚设。

## 决策

把"理解自然语言"和"生成 SQL"彻底分成两段，中间插入一层自有的语义 DSL：

```
自然语言 ──LLM──> 语义 DSL ──确定性代码──> SQL
                  ↑              ↑
          validator 校验    enricher BFS 求 JOIN
```

- LLM 的产物只有 `SemanticQueryDSL`（metric / entity / dimensions / filters），取值必须命中 `agent` schema 里的语义元数据；
- `SemanticDslValidator` 逐意图校验合法性，非法直接终止，不带着病往下走；
- `SemanticDslEnricher` 用 BFS 最短路径求 JOIN，不手写 JOIN；
- `DslTranslator` 从富化后的 DSL 确定性翻译出 SQL，并返回 JDBC 参数绑定；
- 顺序是 校验 → 富化 → 翻译，任一环失败即终止并回错误文本；
- SQL 执行只走 `SqlExecuteTool`：仅 SELECT、拒分号、黑名单关键字拦截、一律参数绑定。

## 后果

- 换 LLM 不会改变 SQL 形状，DSL 层的测试对模型升级免疫；
- 方言差异收敛在 `AbstractDslTranslator` 的一个子类里；
- 代价：多一层 DSL 要维护，且向量检索 + rerank 的召回质量直接决定上限——检索不到正确 metric，整条链路再确定也答不对。

## 关联

- `semanticdsl/` 全部
- `AGENTS.md` → Never Do 前两条
