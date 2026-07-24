package org.example.nlp2dsl2sql.models.entity.planner;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询目标提示（供 GENERATE_DSL 参考，不含完整 SemanticQueryDSL）。
 */
@Data
public class PlanGoal {

    /** 指标提示，如「平均分」 */
    private String metricHint;

    /** 维度提示列表 */
    private List<String> dimensionHints = new ArrayList<>();

    /** 过滤条件提示列表 */
    private List<String> filterHints = new ArrayList<>();
}
