package org.example.nlp2dsl2sql.models.entity.dsl;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL维度值定义表，用于描述业务维度中的具体枚举值以及数据库映射
 */
@Data
@TableName("dsl_dimension_value")
public class DslDimensionValue {
    private Integer id;
    private String dimensionCode;
    private String valueCode;
    private String valueName;
    private String physicalValue;
    private String description;
    private String dimensionName;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
