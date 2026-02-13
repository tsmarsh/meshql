package com.meshql.mesher.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meshql.mesher.introspect.IntrospectionResult;
import com.meshql.mesher.llm.ClaudeClient;
import com.meshql.mesher.llm.DomainModelPrompt;
import com.meshql.mesher.llm.ResponseParser;
import com.meshql.mesher.model.DomainModel;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;

@Command(
        name = "convert",
        description = "Convert introspection JSON to a domain model using Claude",
        mixinStandardHelpOptions = true
)
public class ConvertCommand implements Runnable {

    @Parameters(index = "0", description = "Path to introspection.json")
    private File introspectionFile;

    @Option(names = {"--project-name"}, required = true, description = "Project name (e.g. springfield-electric)")
    private String projectName;

    @Option(names = {"--package"}, defaultValue = "com.meshql.examples.legacy", description = "Java package name")
    private String packageName;

    @Option(names = {"--port"}, defaultValue = "4066", description = "Server port")
    private int port;

    @Option(names = {"--output", "-o"}, description = "Output file (default: stdout)")
    private File output;

    @Override
    public void run() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            IntrospectionResult introspection = mapper.readValue(introspectionFile, IntrospectionResult.class);

            String prompt = DomainModelPrompt.build(introspection, projectName, packageName, port);

            ClaudeClient client = ClaudeClient.fromEnv();
            String response = client.sendMessage(prompt);

            String json = ResponseParser.extractJson(response);
            DomainModel model = mapper.readValue(json, DomainModel.class);

            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            if (output != null) {
                mapper.writeValue(output, model);
                System.out.println("Domain model written to " + output.getAbsolutePath());
            } else {
                System.out.println(mapper.writeValueAsString(model));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
