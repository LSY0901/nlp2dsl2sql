package org.example.nlp2dsl2sql.service.pipeline;

import org.example.nlp2dsl2sql.models.entity.planner.PlanGoal;
import org.example.nlp2dsl2sql.models.dto.dsl.DslCandidate;
import org.example.nlp2dsl2sql.models.dto.dsl.IntentResult;
import org.example.nlp2dsl2sql.models.dto.dsl.SemanticQueryDSL;

/**
 * 语义 DSL 生成 Pipeline Service（独立 LLM 调用）。
 */
public interface IDslGeneratePipelineService {

    /**
     * 基于候选元数据与规划目标生成语义 DSL。
     *
     * @param question  用户问题
     * @param candidate 检索候选
     * @param intent    意图类型
     * @param goal      规划目标提示（可为 null）
     * @return 语义 DSL
     */
    SemanticQueryDSL generate(String question,
                              DslCandidate candidate,
                              IntentResult.IntentType intent,
                              PlanGoal goal);
}
