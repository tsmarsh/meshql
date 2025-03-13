package com.meshql.repositories.mongo;

import com.meshql.repos.certification.RepositoryCertification;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.bwaldvogel.mongo.MongoServer;
import de.bwaldvogel.mongo.backend.memory.MemoryBackend;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class MongoRepositoryCertificationTest extends RepositoryCertification {
    private static final Logger logger = LoggerFactory.getLogger(MongoRepositoryCertificationTest.class);
    private static final String DATABASE_NAME = "test_db";

    private static MongoServer server;
    private static MongoClient mongoClient;
    private MongoCollection<Document> collection;

    public static void setupMongo() {
        try {
            server = new MongoServer(new MemoryBackend());

            // Connect to the MongoDB container
            String connectionString = server.bindAndGetConnectionString();
            mongoClient = MongoClients.create(connectionString);

            logger.info("Connected to MongoDB container at {}", connectionString);
        } catch (Exception e) {
            logger.error("Failed to connect to MongoDB container", e);
            throw e;
        }
    }

    public static void tearDownMongo() {
        if (mongoClient != null) {
            mongoClient.close();
            logger.info("Closed MongoDB connection");
        }

        if (server != null) {
            server.shutdown();
            logger.info("Stopped MongoDB");
        }
    }

    /**
     * Initialize the repository with a new MongoDB collection before each test.
     */
    @Override
    public void init() {
        if(server == null) {
            setupMongo();
        }
        try {
            // Create a new database for testing
            MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);

            // Create a new collection with a random name for each test
            String collectionName = "test_collection_" + UUID.randomUUID().toString().replace("-", "");
            collection = database.getCollection(collectionName);

            // Create the repository with the collection
            repository = new MongoRepository(collection);

            logger.info("Initialized MongoDB repository with collection: {}", collectionName);
        } catch (Exception e) {
            logger.error("Failed to initialize MongoDB repository", e);
            throw e;
        }
    }

    /**
     * Clean up after each test.
     */
    @AfterAll
    public static void tini() {
        if(server != null) {
            server.shutdownNow();
        }
    }
}