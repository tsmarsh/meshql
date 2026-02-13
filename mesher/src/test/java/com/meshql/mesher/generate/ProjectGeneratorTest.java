package com.meshql.mesher.generate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meshql.mesher.model.DomainModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectGeneratorTest {

    private DomainModel model;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        InputStream is = getClass().getResourceAsStream("/springfield-domain-model.json");
        model = mapper.readValue(is, DomainModel.class);
    }

    @Test
    void generatesCompleteProject(@TempDir Path outputDir) throws Exception {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate(model, outputDir);

        // Config files
        assertTrue(Files.exists(outputDir.resolve("config/graph/customer.graphql")));
        assertTrue(Files.exists(outputDir.resolve("config/graph/meter_reading.graphql")));
        assertTrue(Files.exists(outputDir.resolve("config/graph/bill.graphql")));
        assertTrue(Files.exists(outputDir.resolve("config/graph/payment.graphql")));
        assertTrue(Files.exists(outputDir.resolve("config/json/customer.schema.json")));
        assertTrue(Files.exists(outputDir.resolve("config/json/meter_reading.schema.json")));
        assertTrue(Files.exists(outputDir.resolve("config/json/bill.schema.json")));
        assertTrue(Files.exists(outputDir.resolve("config/json/payment.schema.json")));

        // Java source files
        String javaBase = "src/main/java/com/meshql/examples/legacy";
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/Main.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/LegacyTransformer.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/LegacyToCleanProcessor.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/IdResolver.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/CustomerTransformer.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/MeterReadingTransformer.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/BillTransformer.java")));
        assertTrue(Files.exists(outputDir.resolve(javaBase + "/PaymentTransformer.java")));

        // Infrastructure files
        assertTrue(Files.exists(outputDir.resolve("pom.xml")));
        assertTrue(Files.exists(outputDir.resolve("docker-compose.yml")));
        assertTrue(Files.exists(outputDir.resolve("Dockerfile")));
        assertTrue(Files.exists(outputDir.resolve("nginx.conf")));
        assertTrue(Files.exists(outputDir.resolve("debezium/application.properties")));
        assertTrue(Files.exists(outputDir.resolve("legacy-db/init.sql")));
        assertTrue(Files.exists(outputDir.resolve("veriforged/schema.pl")));
        assertTrue(Files.exists(outputDir.resolve("veriforged/gen.toml")));
    }

    @Test
    void generatedMainContainsAllEntities(@TempDir Path outputDir) throws Exception {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate(model, outputDir);

        String main = Files.readString(outputDir.resolve(
                "src/main/java/com/meshql/examples/legacy/Main.java"));

        assertTrue(main.contains("package com.meshql.examples.legacy;"));
        assertTrue(main.contains("/customer/graph"));
        assertTrue(main.contains("/meter_reading/graph"));
        assertTrue(main.contains("/bill/graph"));
        assertTrue(main.contains("/payment/graph"));
        assertTrue(main.contains("/customer/api"));
        assertTrue(main.contains("MongoConfig"));
        assertTrue(main.contains("IdResolver"));
        assertTrue(main.contains("LegacyToCleanProcessor"));
    }

    @Test
    void generatedDockerComposeHasCorrectPort(@TempDir Path outputDir) throws Exception {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate(model, outputDir);

        String dockerCompose = Files.readString(outputDir.resolve("docker-compose.yml"));

        assertTrue(dockerCompose.contains("4066:4066"));
        assertTrue(dockerCompose.contains("springfield_electric"));
        assertTrue(dockerCompose.contains("wal_level=logical"));
    }

    @Test
    void generatedDebeziumConfig(@TempDir Path outputDir) throws Exception {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate(model, outputDir);

        String debezium = Files.readString(outputDir.resolve("debezium/application.properties"));

        assertTrue(debezium.contains("springfield_electric"));
        assertTrue(debezium.contains("topic.prefix=legacy"));
        assertTrue(debezium.contains("cust_acct"));
        assertTrue(debezium.contains("pgoutput"));
    }

    @Test
    void generatedCustomerSchemaHasRequiredFields(@TempDir Path outputDir) throws Exception {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate(model, outputDir);

        String schema = Files.readString(outputDir.resolve("config/json/customer.schema.json"));

        assertTrue(schema.contains("\"account_number\""));
        assertTrue(schema.contains("\"first_name\""));
        assertTrue(schema.contains("\"required\""));
        assertTrue(schema.contains("\"legacy_acct_id\""));
    }

    @Test
    void generatedProcessorHasAllPhases(@TempDir Path outputDir) throws Exception {
        ProjectGenerator generator = new ProjectGenerator();
        generator.generate(model, outputDir);

        String processor = Files.readString(outputDir.resolve(
                "src/main/java/com/meshql/examples/legacy/LegacyToCleanProcessor.java"));

        assertTrue(processor.contains("Phase 1"));
        assertTrue(processor.contains("Phase 2"));
        assertTrue(processor.contains("Phase 3"));
        assertTrue(processor.contains("legacy.public.cust_acct"));
        assertTrue(processor.contains("legacy.public.bill_hdr"));
        assertTrue(processor.contains("CustomerTransformer"));
        assertTrue(processor.contains("BillTransformer"));
    }
}
