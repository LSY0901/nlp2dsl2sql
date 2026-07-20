package org.example.nlp2dsl2sql.semanticdsl.translator;

public class PostgreSqlTranslator extends AbstractDslTranslator {

    @Override
    public SqlDialect getDialect() {
        return SqlDialect.POSTGRESQL;
    }

    @Override
    protected String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return identifier;
        }
        if (identifier.contains("(") || identifier.contains(" ")
                || identifier.contains("?") || identifier.contains("=")) {
            return identifier;
        }

        String[] parts = identifier.split("\\.", -1);
        StringBuilder quoted = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = stripQuotes(parts[i]);
            if (!part.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                throw new IllegalArgumentException("Invalid identifier: " + identifier);
            }
            if (i > 0) {
                quoted.append('.');
            }
            quoted.append('"').append(part.replace("\"", "\"\"")).append('"');
        }
        return quoted.toString();
    }

    private String stripQuotes(String raw) {
        if (raw != null && raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }
}
