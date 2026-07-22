package org.example.nlp2dsl2sql.service.pipeline.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nlp2dsl2sql.semanticdsl.model.IntentResult;
import org.example.nlp2dsl2sql.semanticdsl.model.SemanticQueryDSL;
import org.example.nlp2dsl2sql.semanticdsl.validator.SemanticDslValidator;
import org.example.nlp2dsl2sql.service.pipeline.IValidationPipelineService;
import org.springframework.stereotype.Service;

/**
 * DSL 校验 Pipeline Service 实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationPipelineServiceImpl implements IValidationPipelineService {

    private final SemanticDslValidator dslValidator;

    /**
     * 校验语义 DSL。
     *
     * @param dsl    语义 DSL
     * @param intent 意图类型
     * @return 校验结果
     */
    @Override
    public SemanticDslValidator.ValidationResult validate(SemanticQueryDSL dsl,
                                                          IntentResult.IntentType intent) {
        log.info("━━━ [Pipeline] VALIDATE 开始 ━━━");
        SemanticDslValidator.ValidationResult result =
                dslValidator.validate(dsl, intent);
        log.info("━━━ [Pipeline] VALIDATE 完成: valid={} ━━━", result.valid());
        return result;
    }
}
