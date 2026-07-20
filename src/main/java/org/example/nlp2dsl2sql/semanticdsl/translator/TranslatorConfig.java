package org.example.nlp2dsl2sql.semanticdsl.translator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TranslatorConfig {

    @Value("${dsl.translator.dialect:postgresql}")
    private String dialect;

    @Bean
    public DslTranslator dslTranslator() {
        SqlDialect sqlDialect = SqlDialect.fromValue(dialect);
        return switch (sqlDialect) {
            case POSTGRESQL -> new PostgreSqlTranslator();
            case MYSQL -> new PostgreSqlTranslator();
            case SQLSERVER -> new PostgreSqlTranslator();
        };
    }
}
