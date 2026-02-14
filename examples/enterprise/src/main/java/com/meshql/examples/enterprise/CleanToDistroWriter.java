package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches MongoDB change streams on distro-owned collections (container, consumer)
 * and writes back to the distribution PostgreSQL when the document does NOT have
 * a legacy_distro_id field (indicating it was created by a user, not by CDC).
 */
public class CleanToDistroWriter {
    private static final Logger logger = LoggerFactory.getLogger(CleanToDistroWriter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String mongoUri;
    private final String mongoDb;
    private final String distroJdbcUrl;
    private final IdResolver idResolver;
    private final AtomicBoolean running;

    private Thread watchThread;

    public CleanToDistroWriter(String mongoUri, String mongoDb, String distroJdbcUrl, IdResolver idResolver) {
        this.mongoUri = mongoUri;
        this.mongoDb = mongoDb;
        this.distroJdbcUrl = distroJdbcUrl;
        this.idResolver = idResolver;
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.getAndSet(true)) return;

        watchThread = new Thread(this::watchLoop, "clean-to-distro-writer");
        watchThread.setDaemon(true);
        watchThread.start();
        logger.info("Started CleanToDistroWriter");
    }

    public void stop() {
        running.set(false);
        if (watchThread != null) {
            watchThread.interrupt();
            try { watchThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        logger.info("CleanToDistroWriter stopped");
    }

    private void watchLoop() {
        try (MongoClient mongoClient = MongoClients.create(mongoUri)) {
            // Watch the container collection for inserts
            watchCollection(mongoClient, "container", this::onContainerInsert);
        } catch (Exception e) {
            if (running.get()) {
                logger.error("CleanToDistroWriter watch loop failed: {}", e.getMessage(), e);
            }
        }
    }

    private void watchCollection(MongoClient mongoClient, String collectionName,
                                  java.util.function.Consumer<Document> handler) {
        MongoCollection<Document> collection = mongoClient.getDatabase(mongoDb).getCollection(collectionName);

        ChangeStreamIterable<Document> changeStream = collection.watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP);

        for (ChangeStreamDocument<Document> change : changeStream) {
            if (!running.get()) break;

            if ("insert".equals(change.getOperationType().getValue())) {
                Document fullDoc = change.getFullDocument();
                if (fullDoc != null) {
                    Document payload = fullDoc.get("payload", Document.class);
                    if (payload != null && payload.containsKey("legacy_distro_id") &&
                            payload.get("legacy_distro_id") != null &&
                            !payload.getString("legacy_distro_id").isEmpty()) {
                        logger.debug("Skipping CDC-originated container insert");
                        continue;
                    }
                    handler.accept(fullDoc);
                }
            }
        }
    }

    private void onContainerInsert(Document doc) {
        try {
            Document payload = doc.get("payload", Document.class);
            if (payload == null) return;

            String name = payload.getString("name");
            String containerType = payload.getString("container_type");
            Integer capacity = payload.getInteger("capacity");
            String zone = payload.getString("zone");

            try (Connection conn = DriverManager.getConnection(distroJdbcUrl, "postgres", "postgres")) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO containers (name, container_type, capacity, zone, location_code) " +
                        "VALUES (?, ?, ?, ?, ?) RETURNING id",
                        PreparedStatement.RETURN_GENERATED_KEYS);
                ps.setString(1, name != null ? name : "New Container");
                ps.setString(2, containerType != null ? containerType : "warehouse");
                ps.setInt(3, capacity != null ? capacity : 100);
                ps.setString(4, zone != null ? zone : "north");
                ps.setString(5, "WH99");
                ps.executeUpdate();

                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    int newDistroId = rs.getInt(1);
                    String meshqlId = doc.getObjectId("_id").toString();
                    idResolver.registerDistroContainer(String.valueOf(newDistroId), meshqlId);
                    logger.info("Wrote back container to distro: id={}, name={}", newDistroId, name);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to write container back to distro: {}", e.getMessage(), e);
        }
    }
}
