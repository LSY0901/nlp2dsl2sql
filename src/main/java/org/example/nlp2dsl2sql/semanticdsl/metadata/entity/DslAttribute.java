package org.example.nlp2dsl2sql.semanticdsl.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL业务属性定义表，用于描述实体字段语义以及SQL字段映射
 */
@Data
@TableName("dsl_attribute")
public class DslAttribute {
    private Integer id;
    private String entityCode;
    private String attributeCode;
    private String attributeName;
    private String physicalColumn;
    private String dataType;
    private String attributeType;
    private String description;
    private String entityName;
    private Boolean isQueryable;
    private Boolean isAggregatable;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
