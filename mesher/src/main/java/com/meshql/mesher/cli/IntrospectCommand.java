package com.meshql.mesher.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.meshql.mesher.introspect.DatabaseIntrospector;
import com.meshql.mesher.introspect.IntrospectionResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;

@Command(
        name = "introspect",
        description = "Introspect a PostgreSQL database and produce introspection JSON",
        mixinStandardHelpOptions = true
)
public class IntrospectCommand implements Runnable {

    @Option(names = {"--jdbc-url"}, required = true, description = "JDBC connection URL")
    private String jdbcUrl;

    @Option(names = {"--username", "-u"}, description = "Database username")
    private String username;

    @Option(names = {"--password", "-p"}, description = "Database password")
    private String password;

    @Option(names = {"--schema", "-s"}, defaultValue = "public", description = "Schema to introspect (default: public)")
    private String schema;

    @Option(names = {"--output", "-o"}, description = "Output file (default: stdout)")
    private File output;

    @Override
    public void run() {
        try {
            DatabaseIntrospector introspector = (username != null)
                    ? new DatabaseIntrospector(jdbcUrl, username, password)
                    : new DatabaseIntrospector(jdbcUrl);

            IntrospectionResult result = introspector.introspect();

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            if (output != null) {
                mapper.writeValue(output, result);
                System.out.println("Introspection written to " + output.getAbsolutePath());
            } else {
                System.out.println(mapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
