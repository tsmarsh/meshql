package com.meshql.mesher.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meshql.mesher.generate.ProjectGenerator;
import com.meshql.mesher.introspect.DatabaseIntrospector;
import com.meshql.mesher.introspect.IntrospectionResult;
import com.meshql.mesher.llm.ClaudeClient;
import com.meshql.mesher.llm.DomainModelPrompt;
import com.meshql.mesher.llm.ResponseParser;
import com.meshql.mesher.model.DomainModel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Command(
        name = "run",
        description = "Introspect, convert, and generate in one step",
        mixinStandardHelpOptions = true
)
public class RunCommand implements Runnable {

    @Option(names = {"--jdbc-url"}, required = true, description = "JDBC connection URL")
    private String jdbcUrl;

    @Option(names = {"--username", "-u"}, description = "Database username")
    private String username;

    @Option(names = {"--password", "-p"}, description = "Database password")
    private String password;

    @Option(names = {"--project-name"}, required = true, description = "Project name")
    private String projectName;

    @Option(names = {"--package"}, defaultValue = "com.meshql.examples.legacy", description = "Java package name")
    private String packageName;

    @Option(names = {"--port"}, defaultValue = "4066", description = "Server port")
    private int port;

    @Option(names = {"--output", "-o"}, required = true, description = "Output directory")
    private File outputDir;

    @Override
    public void run() {
        try {
            // Step 1: Introspect
            System.out.println("Step 1/3: Introspecting database...");
            DatabaseIntrospector introspector = (username != null)
                    ? new DatabaseIntrospector(jdbcUrl, username, password)
                    : new DatabaseIntrospector(jdbcUrl);
            IntrospectionResult introspection = introspector.introspect();

            // Step 2: Convert via LLM
            System.out.println("Step 2/3: Converting to domain model via Claude...");
            String prompt = DomainModelPrompt.build(introspection, projectName, packageName, port);
            ClaudeClient client = ClaudeClient.fromEnv();
            String response = client.sendMessage(prompt);
            String json = ResponseParser.extractJson(response);
            ObjectMapper mapper = new ObjectMapper();
            DomainModel model = mapper.readValue(json, DomainModel.class);

            // Save intermediate domain model
            File domainModelFile = new File(outputDir, "domain-model.json");
            outputDir.mkdirs();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(domainModelFile, model);
            System.out.println("Domain model saved to " + domainModelFile.getAbsolutePath());

            // Step 3: Generate
            System.out.println("Step 3/3: Generating project...");
            ProjectGenerator generator = new ProjectGenerator();
            generator.generate(model, outputDir.toPath());

            System.out.println("Project generated at " + outputDir.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
