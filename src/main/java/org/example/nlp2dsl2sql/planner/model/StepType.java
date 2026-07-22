package org.example.nlp2dsl2sql.planner.model;

/**
 * 查询计划步骤类型（封闭枚举，Planner 只能从此集合选择）。
 */
public enum StepType {
    /** 语义检索 */
    RETRIEVE,
    /** 语义 DSL 生成（独立 LLM Service） */
    GENERATE_DSL,
    /** DSL 校验 */
    VALIDATE,
    /** DSL 富化 */
    ENRICH,
    /** SQL 翻译 */
    TRANSLATE,
    /** SQL 审查 */
    REVIEW,
    /** SQL 执行 */
    EXECUTE,
    /** 自然语言回答 */
    ANSWER
}
