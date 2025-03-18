package com.meshql.repositories.rdbms;

import com.github.jknack.handlebars.Template;

import java.util.List;

public record RequiredTemplates(
        List<Template> createScripts,
        Template insert,
        Template insertToken,
        Template read,
        Template readMany,
        Template remove,
        Template removeMany,
        Template list
){ }
