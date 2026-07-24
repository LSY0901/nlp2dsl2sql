package org.example.nlp2dsl2sql.planner;

import org.example.nlp2dsl2sql.models.entity.planner.QueryPlan;
import org.example.nlp2dsl2sql.enums.planner.StepType;

/**
 * 查询规划器：由 LLM 产出/修正结构化执行计划。
 */
public interface IQueryPlanner {

    /**
     * 根据用户问题生成初始查询计划。
     *
     * @param question 用户自然语言问题
     * @return 查询计划
     */
    QueryPlan plan(String question);

    /**
     * 步骤失败后生成修正计划。
     *
     * @param question       用户问题
     * @param previousPlan   上一份计划
     * @param failedStep     失败步骤
     * @param errorMessage   错误信息
     * @param contextSummary 上下文摘要
     * @return 新查询计划
     */
    QueryPlan replan(String question,
                     QueryPlan previousPlan,
                     StepType failedStep,
                     String errorMessage,
                     String contextSummary);
}
