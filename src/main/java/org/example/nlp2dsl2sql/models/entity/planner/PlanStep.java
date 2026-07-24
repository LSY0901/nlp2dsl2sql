package org.example.nlp2dsl2sql.models.entity.planner;

import lombok.Data;
import org.example.nlp2dsl2sql.enums.planner.FailureAction;
import org.example.nlp2dsl2sql.enums.planner.StepType;

/**
 * 查询计划中的单步定义。
 */
@Data
public class PlanStep {

    /** 步骤类型 */
    private StepType type;

    /** 是否跳过（REVIEW 实际不可跳过，由 Workflow 门禁强制） */
    private boolean skip;

    /** 同一步 Workflow 内重试次数（不含重规划） */
    private int retry;

    /** 失败策略 */
    private FailureAction onFailure = FailureAction.ABORT;
}
