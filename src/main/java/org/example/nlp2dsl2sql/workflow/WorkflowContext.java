package org.example.nlp2dsl2sql.workflow;

import lombok.Data;
import org.example.nlp2dsl2sql.planner.model.QueryPlan;
import org.example.nlp2dsl2sql.planner.model.StepType;
import org.example.nlp2dsl2sql.semanticdsl.model.DslCandidate;
import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Workflow 跨步骤中间状态。
 */
@Data
public class WorkflowContext {

    /** 用户原始问题 */
    private String question;

    /** 当前生效计划 */
    private QueryPlan plan;

    /** 已重规划次数 */
    private int replanCount;

    /** 检索候选集 */
    private DslCandidate candidate;

    /** 语义 DSL */
    private SemanticQueryDSL semanticDSL;

    /** 富化后 DSL */
    private EnrichedQueryDSL enrichedDSL;

    /** 参数化 SQL */
    private String sql;

    /** SQL 参数 */
    private List<Object> params = new ArrayList<>();

    /** 查询结果 */
    private List<Map<String, Object>> queryResult;

    /** SQL 审查是否通过 */
    private boolean reviewPassed;

    /** 最近一次失败步骤 */
    private StepType failedStep;

    /** 最近一次错误信息 */
    private String lastError;

    /** SSE 进度缓冲（非流式步骤的文本） */
    private final List<String> progressMessages = new ArrayList<>();

    /**
     * 追加一条进度消息。
     *
     * @param message 进度文本
     */
    public void addProgress(String message) {
        if (message != null && !message.isBlank()) {
            progressMessages.add(message);
        }
    }

    /**
     * 重规划前清空半成品状态。
     *
     * @param clearCandidate 是否同时清空 candidate
     */
    public void clearDerivedState(boolean clearCandidate) {
        if (clearCandidate) {
            this.candidate = null;
        }
        this.semanticDSL = null;
        this.enrichedDSL = null;
        this.sql = null;
        this.params = new ArrayList<>();
        this.queryResult = null;
        this.reviewPassed = false;
    }
}
