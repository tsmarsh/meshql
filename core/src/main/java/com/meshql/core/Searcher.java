package com.meshql.core;

import com.github.jknack.handlebars.Template;
import com.tailoredshapes.stash.Stash;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface Searcher {
    Stash find(
            Template queryTemplate,
            Stash args,
            List<String> creds,
            long timestamp
    );

    List<Stash> findAll(
            Template queryTemplate,
            Stash args,
            List<String> creds,
            long timestamp
    );
}