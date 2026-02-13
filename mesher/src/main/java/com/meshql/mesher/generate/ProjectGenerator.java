package com.meshql.mesher.generate;

import com.meshql.mesher.model.DomainModel;
import com.meshql.mesher.model.EntityModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates all code generators to produce a complete MeshQL
 * anti-corruption layer project from a domain model.
 */
public class ProjectGenerator {
    private static final Logger logger = LoggerFactory.getLogger(ProjectGenerator.class);

    private final HandlebarsEngine engine;
    private final GraphQLSchemaGenerator graphqlGenerator;
    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final TransformerGenerator transformerGenerator;
    private final MainGenerator mainGenerator;
    private final InterfaceGenerator interfaceGenerator;
    private final ProcessorGenerator processorGenerator;
    private final IdResolverGenerator idResolverGenerator;
    private final PomGenerator pomGenerator;
    private final DockerComposeGenerator dockerComposeGenerator;
    private final DockerfileGenerator dockerfileGenerator;
    private final NginxGenerator nginxGenerator;
    private final DebeziumGenerator debeziumGenerator;
    private final InitSqlGenerator initSqlGenerator;
    private final VeriforgedGenerator veriforgedGenerator;

    public ProjectGenerator() {
        this.engine = new HandlebarsEngine();
        this.graphqlGenerator = new GraphQLSchemaGenerator(engine);
        this.jsonSchemaGenerator = new JsonSchemaGenerator(engine);
        this.transformerGenerator = new TransformerGenerator(engine);
        this.mainGenerator = new MainGenerator(engine);
        this.interfaceGenerator = new InterfaceGenerator(engine);
        this.processorGenerator = new ProcessorGenerator(engine);
        this.idResolverGenerator = new IdResolverGenerator(engine);
        this.pomGenerator = new PomGenerator(engine);
        this.dockerComposeGenerator = new DockerComposeGenerator(engine);
        this.dockerfileGenerator = new DockerfileGenerator(engine);
        this.nginxGenerator = new NginxGenerator(engine);
        this.debeziumGenerator = new DebeziumGenerator(engine);
        this.initSqlGenerator = new InitSqlGenerator(engine);
        this.veriforgedGenerator = new VeriforgedGenerator(engine);
    }

    public void generate(DomainModel model, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);

        logger.info("Generating project '{}' to {}", model.projectName(), outputDir);

        // Per-entity generators
        for (EntityModel entity : model.entities()) {
            logger.info("  Generating entity: {}", entity.cleanName());
            graphqlGenerator.generate(model, entity, outputDir);
            jsonSchemaGenerator.generate(model, entity, outputDir);
            transformerGenerator.generate(model, entity, outputDir);
        }

        // Cross-cutting generators
        logger.info("  Generating Main.java");
        mainGenerator.generate(model, outputDir);

        logger.info("  Generating LegacyTransformer.java interface");
        interfaceGenerator.generate(model, outputDir);

        logger.info("  Generating LegacyToCleanProcessor.java");
        processorGenerator.generate(model, outputDir);

        logger.info("  Generating IdResolver.java");
        idResolverGenerator.generate(model, outputDir);

        // Infrastructure generators
        logger.info("  Generating pom.xml");
        pomGenerator.generate(model, outputDir);

        logger.info("  Generating docker-compose.yml");
        dockerComposeGenerator.generate(model, outputDir);

        logger.info("  Generating Dockerfile");
        dockerfileGenerator.generate(model, outputDir);

        logger.info("  Generating nginx.conf");
        nginxGenerator.generate(model, outputDir);

        logger.info("  Generating debezium/application.properties");
        debeziumGenerator.generate(model, outputDir);

        logger.info("  Generating legacy-db/init.sql");
        initSqlGenerator.generate(model, null, outputDir);

        logger.info("  Generating veriforged files");
        veriforgedGenerator.generate(model, outputDir);

        logger.info("Project generation complete: {} entities", model.entities().size());
    }
}
