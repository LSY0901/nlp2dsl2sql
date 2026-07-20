package org.example.nlp2dsl2sql.semanticdsl.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL指标维度关系表，用于限制指标支持的分析维度，避免LLM生成非法组合
 */
@Data
@TableName("dsl_metric_dimension")
public class DslMetricDimension {
    private Integer id;
    private String metricCode;
    private String dimensionCode;
    private String relationType;
    private String description;
    private Boolean isRequired;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
