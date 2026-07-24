package org.example.nlp2dsl2sql.enums.planner;

/**
 * 步骤失败后的处理策略。
 */
public enum FailureAction {
    /** 同一步重试，用尽后中止 */
    RETRY,
    /** 耗尽本步 retry 后触发重规划 */
    REPLAN,
    /** 跳过本步（仅非关键步骤可用） */
    SKIP,
    /** 立即中止 */
    ABORT
}
