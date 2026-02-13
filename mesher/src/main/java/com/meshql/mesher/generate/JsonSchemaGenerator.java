package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;
import com.meshql.mesher.model.EntityModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class JsonSchemaGenerator {
    private final HandlebarsEngine engine;

    public JsonSchemaGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, EntityModel entity, Path outputDir) throws IOException {
        Path jsonDir = outputDir.resolve("config/json");
        Files.createDirectories(jsonDir);

        Map<String, Object> context = new HashMap<>();
        context.put("entity", entity);
        context.put("model", model);

        String content = engine.render("json-schema", context);
        Files.writeString(jsonDir.resolve(entity.cleanName() + ".schema.json"), content);
    }
}
