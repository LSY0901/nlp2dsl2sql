package org.example.nlp2dsl2sql.semanticdsl.model;

import lombok.Data;

import java.util.List;

@Data
public class SemanticQueryDSL {
    private String metric;
    private String entity;
    private List<String> dimensions;
    private List<SemanticFilter> filters;
}
