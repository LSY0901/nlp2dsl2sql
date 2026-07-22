package org.example.nlp2dsl2sql.planner.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM Planner 产出的结构化查询执行计划。
 */
@Data
public class QueryPlan {

    /** 意图类型名，如 METRIC_QUERY */
    private String intent;

    /** 规划原因说明 */
    private String reason;

    /** 查询目标提示 */
    private PlanGoal goal = new PlanGoal();

    /** 有序步骤列表 */
    private List<PlanStep> steps = new ArrayList<>();

    /** 整次查询最大重规划次数，默认 2 */
    private int maxReplan = 2;
}
