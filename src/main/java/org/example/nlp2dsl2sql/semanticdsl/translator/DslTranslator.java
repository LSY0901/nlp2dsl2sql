package org.example.nlp2dsl2sql.semanticdsl.translator;

import org.example.nlp2dsl2sql.semanticdsl.model.EnrichedQueryDSL;

import java.util.Collections;
import java.util.List;

public interface DslTranslator {

    TranslatedSql translate(EnrichedQueryDSL enrichedDSL);

    SqlDialect getDialect();

    record TranslatedSql(String sql, List<Object> parameters) {
        public TranslatedSql(String sql) {
            this(sql, Collections.emptyList());
        }

        @Override
        public List<Object> parameters() {
            return parameters == null ? Collections.emptyList() : parameters;
        }
    }
}
