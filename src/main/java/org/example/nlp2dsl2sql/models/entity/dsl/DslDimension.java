package org.example.nlp2dsl2sql.models.entity.dsl;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL查询维度定义表，用于描述业务分析中的分组维度，例如年级、班级、科目
 */
@Data
@TableName("dsl_dimension")
public class DslDimension {
    private Integer id;
    private String dimensionCode;
    private String dimensionName;
    private String entityCode;
    private String attributeCode;
    private String dimensionType;
    private String physicalColumn;
    private String description;
    private Boolean isQueryable;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
