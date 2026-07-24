package org.example.nlp2dsl2sql.models.entity.dsl;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL业务实体定义表，用于描述自然语言中的业务对象，例如学生、成绩、班级
 */
@Data
@TableName("dsl_entity")
public class DslEntity {
    private Integer id;
    private String entityCode;
    private String entityName;
    private String entityType;
    private String physicalTable;
    private String primaryKey;
    private String description;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
