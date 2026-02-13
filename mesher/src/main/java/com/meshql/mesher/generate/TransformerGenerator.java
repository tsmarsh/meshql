package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;
import com.meshql.mesher.model.EntityModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class TransformerGenerator {
    private final HandlebarsEngine engine;

    public TransformerGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, EntityModel entity, Path outputDir) throws IOException {
        Path javaDir = outputDir.resolve("src/main/java/" + model.packageName().replace('.', '/'));
        Files.createDirectories(javaDir);

        Map<String, Object> context = new HashMap<>();
        context.put("entity", entity);
        context.put("model", model);
        context.put("packageName", model.packageName());

        String content = engine.render("transformer.java", context);
        Files.writeString(javaDir.resolve(entity.className() + "Transformer.java"), content);
    }
}
