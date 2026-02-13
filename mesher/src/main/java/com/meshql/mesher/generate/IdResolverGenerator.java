package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class IdResolverGenerator {
    private final HandlebarsEngine engine;

    public IdResolverGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, Path outputDir) throws IOException {
        Path javaDir = outputDir.resolve("src/main/java/" + model.packageName().replace('.', '/'));
        Files.createDirectories(javaDir);

        Map<String, Object> context = new HashMap<>();
        context.put("model", model);
        context.put("packageName", model.packageName());

        String content = engine.render("id-resolver.java", context);
        Files.writeString(javaDir.resolve("IdResolver.java"), content);
    }
}
