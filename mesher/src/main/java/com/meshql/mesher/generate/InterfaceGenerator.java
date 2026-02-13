package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class InterfaceGenerator {
    private final HandlebarsEngine engine;

    public InterfaceGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, Path outputDir) throws IOException {
        Path javaDir = outputDir.resolve("src/main/java/" + model.packageName().replace('.', '/'));
        Files.createDirectories(javaDir);

        Map<String, Object> context = new HashMap<>();
        context.put("packageName", model.packageName());

        String content = engine.render("legacy-transformer.java", context);
        Files.writeString(javaDir.resolve("LegacyTransformer.java"), content);
    }
}
