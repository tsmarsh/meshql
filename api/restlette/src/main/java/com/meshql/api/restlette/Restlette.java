package com.meshql.api.restlette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.tailoredshapes.stash.Stash;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Route;
import spark.Service;

import java.util.Collections;
import java.util.Map;

public class Restlette {
    private static final Logger logger = LoggerFactory.getLogger(Restlette.class);


    /**
     * Initialize a Restlette with the given configuration
     * 
     * @param sparkService The Spark service to use
     * @param crud         The CRUD handler
     * @param apiPath      The API path
     * @param port         The port to run on
     * @param jsonSchema   The JSON schema
     * @return The configured Spark service
     */
    public static Service init(
            Service sparkService,
            CrudHandler crud,
            String apiPath,
            int port,
            Stash jsonSchema) {
        logger.info("API Docs are available at: http://localhost:{}{}api-docs", port, apiPath);
        Gson gson = new Gson();

        sparkService.defaultResponseTransformer(gson::toJson);

        // Setup routes with Stash middleware for JSON conversion
        sparkService.post(apiPath + "/bulk", crud::bulkCreate);
        sparkService.get(apiPath + "/bulk", crud::bulkRead);
        sparkService.post(apiPath, crud::create);
        sparkService.get(apiPath, crud::list);
        sparkService.get(apiPath + "/:id", crud::read);
        sparkService.put(apiPath + "/:id",crud::update);
        sparkService.delete(apiPath + "/:id", crud::remove);

        // Setup Swagger documentation
        OpenAPI openAPI = createSwaggerDocument(apiPath, port, jsonSchema);
        String swaggerJson = openAPI.getOpenapi();

        sparkService.get(apiPath + "/api-docs/swagger.json", (req, res) -> swaggerJson, Object::toString);
        sparkService.get(apiPath + "/api-docs", new SwaggerUIHandler(apiPath), Object::toString);

        return sparkService;
    }

    /**
     * Create the Swagger document
     */
    private static OpenAPI createSwaggerDocument(String apiPath, int port, Stash schema) {
        OpenAPI openAPI = new OpenAPI();

        Info info = new Info()
                .title(apiPath + " API")
                .version("0.1.0")
                .description("API for mutating " + apiPath);

        openAPI.setInfo(info);

        Server server = new Server();
        server.setUrl("http://localhost:" + port);
        openAPI.setServers(Collections.singletonList(server));

        // Add security schemes and schemas
        SwaggerConfig.configureSwagger(openAPI, schema);

        return openAPI;
    }

}