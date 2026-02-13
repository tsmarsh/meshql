package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;
import com.meshql.mesher.model.EntityModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class GraphQLSchemaGenerator {
    private final HandlebarsEngine engine;

    public GraphQLSchemaGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, EntityModel entity, Path outputDir) throws IOException {
        Path graphDir = outputDir.resolve("config/graph");
        Files.createDirectories(graphDir);

        Map<String, Object> context = new HashMap<>();
        context.put("entity", entity);
        context.put("model", model);

        String content = engine.render("graphql-schema", context);
        Files.writeString(graphDir.resolve(entity.cleanName() + ".graphql"), content);
    }
}
