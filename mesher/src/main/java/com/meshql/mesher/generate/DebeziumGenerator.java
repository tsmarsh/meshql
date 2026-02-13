package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class DebeziumGenerator {
    private final HandlebarsEngine engine;

    public DebeziumGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, Path outputDir) throws IOException {
        Path debeziumDir = outputDir.resolve("debezium");
        Files.createDirectories(debeziumDir);

        Map<String, Object> context = new HashMap<>();
        context.put("model", model);

        String content = engine.render("debezium-application.properties", context);
        Files.writeString(debeziumDir.resolve("application.properties"), content);
    }
}
