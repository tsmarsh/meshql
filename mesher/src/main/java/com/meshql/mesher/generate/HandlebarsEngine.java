package com.meshql.mesher.generate;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.io.ClassPathTemplateLoader;

import java.io.IOException;

/**
 * Handlebars template engine with custom helpers for code generation.
 */
public class HandlebarsEngine {
    private final Handlebars handlebars;

    public HandlebarsEngine() {
        ClassPathTemplateLoader loader = new ClassPathTemplateLoader();
        loader.setPrefix("/templates");
        loader.setSuffix(".hbs");
        this.handlebars = new Handlebars(loader);
        registerHelpers();
    }

    private void registerHelpers() {
        handlebars.registerHelper("pascalCase", (Helper<String>) (value, options) -> {
            if (value == null || value.isEmpty()) return "";
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = true;
            for (char c : value.toCharArray()) {
                if (c == '_' || c == '-' || c == ' ') {
                    capitalizeNext = true;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        });

        handlebars.registerHelper("camelCase", (Helper<String>) (value, options) -> {
            if (value == null || value.isEmpty()) return "";
            StringBuilder result = new StringBuilder();
            boolean capitalizeNext = false;
            boolean first = true;
            for (char c : value.toCharArray()) {
                if (c == '_' || c == '-' || c == ' ') {
                    capitalizeNext = true;
                } else if (first) {
                    result.append(Character.toLowerCase(c));
                    first = false;
                } else if (capitalizeNext) {
                    result.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    result.append(c);
                }
            }
            return result.toString();
        });

        handlebars.registerHelper("upperSnake", (Helper<String>) (value, options) -> {
            if (value == null || value.isEmpty()) return "";
            return value.toUpperCase().replace('-', '_').replace(' ', '_');
        });

        handlebars.registerHelper("eq", (Helper<Object>) (value, options) -> {
            Object param = options.param(0);
            boolean isEqual = value != null && value.toString().equals(param != null ? param.toString() : null);
            // When used as sub-expression (eq x "y") inside {{#if}}, fn() is empty — return boolean
            CharSequence fn = options.fn();
            if (fn.length() == 0 && options.inverse().length() == 0) {
                return isEqual;
            }
            // When used as block helper {{#eq x "y"}}...{{/eq}}, return block content
            return isEqual ? fn : options.inverse();
        });

        handlebars.registerHelper("neq", (Helper<Object>) (value, options) -> {
            Object param = options.param(0);
            boolean notEqual = value == null || !value.toString().equals(param != null ? param.toString() : null);
            CharSequence fn = options.fn();
            if (fn.length() == 0 && options.inverse().length() == 0) {
                return notEqual;
            }
            return notEqual ? fn : options.inverse();
        });

        handlebars.registerHelper("json", (Helper<Object>) (value, options) -> {
            if (value == null) return "null";
            // Escape for Java string literal
            return value.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        });

        handlebars.registerHelper("last", (Helper<Object>) (value, options) -> {
            // Check if current iteration is last
            int index = options.hash("index", 0);
            int size = options.hash("size", 0);
            return index == size - 1 ? options.fn() : options.inverse();
        });
    }

    public Template compile(String templateName) throws IOException {
        return handlebars.compile(templateName);
    }

    public String render(String templateName, Object context) throws IOException {
        Template template = compile(templateName);
        return template.apply(context);
    }
}
