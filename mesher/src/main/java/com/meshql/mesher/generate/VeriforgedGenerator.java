package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class VeriforgedGenerator {
    private final HandlebarsEngine engine;

    public VeriforgedGenerator(HandlebarsEngine engine) {
        this.engine = engine;
    }

    public void generate(DomainModel model, Path outputDir) throws IOException {
        Path veriforgedDir = outputDir.resolve("veriforged");
        Files.createDirectories(veriforgedDir);

        Map<String, Object> context = new HashMap<>();
        context.put("model", model);

        String schemaContent = engine.render("veriforged-schema.pl", context);
        Files.writeString(veriforgedDir.resolve("schema.pl"), schemaContent);

        String configContent = engine.render("veriforged-gen.toml", context);
        Files.writeString(veriforgedDir.resolve("gen.toml"), configContent);
    }
}
