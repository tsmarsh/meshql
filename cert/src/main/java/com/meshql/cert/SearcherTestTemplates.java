package com.meshql.cert;

import com.github.jknack.handlebars.Template;

/**
 * Standard query templates used for Searcher certification tests.
 * Each database plugin must provide implementations of these queries
 * using their specific query language (SQL, MongoDB query syntax, etc.).
 */
public class SearcherTestTemplates {
    public final Template findById;
    public final Template findByName;
    public final Template findAllByType;
    public final Template findByNameAndType;

    public SearcherTestTemplates(
            Template findById,
            Template findByName,
            Template findAllByType,
            Template findByNameAndType) {
        this.findById = findById;
        this.findByName = findByName;
        this.findAllByType = findAllByType;
        this.findByNameAndType = findByNameAndType;
    }
}
