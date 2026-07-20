package org.example.nlp2dsl2sql.semanticdsl.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL业务指标定义表，用于描述自然语言中的业务指标、计算逻辑以及语义向量
 */
@Data
@TableName("dsl_metric")
public class DslMetric {
    private Integer id;
    private String metricCode;
    private String metricName;
    private String metricType;
    private String entityCode;
    private String aggregationType;
    private String expression;
    private String unit;
    private Integer precisionValue;
    private String resultType;
    private String description;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
