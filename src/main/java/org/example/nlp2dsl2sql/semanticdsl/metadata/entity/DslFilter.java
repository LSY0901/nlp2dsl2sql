package org.example.nlp2dsl2sql.semanticdsl.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL业务过滤规则定义表，用于描述固定业务条件和SQL过滤逻辑
 */
@Data
@TableName("dsl_filter")
public class DslFilter {
    private Integer id;
    private String filterCode;
    private String filterName;
    private String entityCode;
    private String attributeCode;
    private String operatorType;
    private String expression;
    private String description;
    private Boolean isSystem;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
