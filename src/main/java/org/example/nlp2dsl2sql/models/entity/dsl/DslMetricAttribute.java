package org.example.nlp2dsl2sql.models.entity.dsl;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL指标属性依赖关系表，用于描述指标计算依赖的业务字段
 */
@Data
@TableName("dsl_metric_attribute")
public class DslMetricAttribute {
    private Integer id;
    private String metricCode;
    private String entityCode;
    private String attributeCode;
    private String roleType;
    private String description;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
