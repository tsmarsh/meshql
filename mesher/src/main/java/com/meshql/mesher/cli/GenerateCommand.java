package com.meshql.mesher.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.mesher.generate.ProjectGenerator;
import com.meshql.mesher.model.DomainModel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;

@Command(
        name = "generate",
        description = "Generate a MeshQL anti-corruption layer project from a domain model",
        mixinStandardHelpOptions = true
)
public class GenerateCommand implements Runnable {

    @Parameters(index = "0", description = "Path to domain-model.json")
    private File domainModelFile;

    @Option(names = {"--output", "-o"}, required = true, description = "Output directory")
    private File outputDir;

    @Override
    public void run() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            DomainModel model = mapper.readValue(domainModelFile, DomainModel.class);

            ProjectGenerator generator = new ProjectGenerator();
            generator.generate(model, outputDir.toPath());

            System.out.println("Project generated at " + outputDir.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
