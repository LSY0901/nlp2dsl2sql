package org.example.nlp2dsl2sql.service.pipeline;

import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.validator.SemanticDslValidator;

/**
 * DSL 校验 Pipeline Service。
 */
public interface IValidationPipelineService {

    /**
     * 校验语义 DSL。
     *
     * @param dsl    语义 DSL
     * @param intent 意图类型
     * @return 校验结果
     */
    SemanticDslValidator.ValidationResult validate(SemanticQueryDSL dsl,
                                                   IntentResult.IntentType intent);
}
