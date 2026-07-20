package org.example.nlp2dsl2sql.semanticdsl.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL实体关系定义表，用于描述业务实体之间关联关系以及SQL JOIN规则
 */
@Data
@TableName("dsl_relation")
public class DslRelation {
    private Integer id;
    private String relationCode;
    private String sourceEntity;
    private String targetEntity;
    private String relationType;
    private String joinType;
    private String joinCondition;
    private Integer priority;
    private String description;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
