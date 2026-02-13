package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class InitSqlGenerator {
    private final HandlebarsEngine engine;

    public InitSqlGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, String originalDdl, Path outputDir) throws IOException {
        Path dbDir = outputDir.resolve("legacy-db");
        Files.createDirectories(dbDir);

        Map<String, Object> context = new HashMap<>();
        context.put("model", model);
        context.put("originalDdl", originalDdl);

        String content = engine.render("init.sql", context);
        Files.writeString(dbDir.resolve("init.sql"), content);
    }
}
