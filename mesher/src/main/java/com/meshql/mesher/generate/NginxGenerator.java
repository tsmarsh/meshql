package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class NginxGenerator {
    private final HandlebarsEngine engine;

    public NginxGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, Path outputDir) throws IOException {
        Map<String, Object> context = new HashMap<>();
        context.put("model", model);

        String content = engine.render("nginx.conf", context);
        Files.writeString(outputDir.resolve("nginx.conf"), content);
    }
}
