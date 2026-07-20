package org.example.nlp2dsl2sql.semanticdsl.metadata.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * DSL业务同义词定义表，用于将用户自然语言表达映射到标准DSL对象
 */
@Data
@TableName("dsl_synonym")
public class DslSynonym {
    private Integer id;
    private String synonymText;
    private String objectType;
    private String objectCode;
    private String standardName;
    private String description;
    private Boolean isDeleted;
    private String creator;
    private Long createdDt;
    private String lastEditor;
    private Long lastEditedDt;
}
