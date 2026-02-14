package com.meshql.examples.enterprise;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches MongoDB change streams on SAP-owned collections (farm, coop, hen, lay_report)
 * and writes back to SAP PostgreSQL when the document does NOT have a legacy_* field
 * (indicating it was created by a user, not by CDC).
 */
public class CleanToSapWriter {
    private static final Logger logger = LoggerFactory.getLogger(CleanToSapWriter.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String mongoUri;
    private final String mongoDb;
    private final String sapJdbcUrl;
    private final IdResolver idResolver;
    private final AtomicBoolean running;

    private Thread watchThread;

    public CleanToSapWriter(String mongoUri, String mongoDb, String sapJdbcUrl, IdResolver idResolver) {
        this.mongoUri = mongoUri;
        this.mongoDb = mongoDb;
        this.sapJdbcUrl = sapJdbcUrl;
        this.idResolver = idResolver;
        this.running = new AtomicBoolean(false);
    }

    public void start() {
        if (running.getAndSet(true)) return;

        watchThread = new Thread(this::watchLoop, "clean-to-sap-writer");
        watchThread.setDaemon(true);
        watchThread.start();
        logger.info("Started CleanToSapWriter");
    }

    public void stop() {
        running.set(false);
        if (watchThread != null) {
            watchThread.interrupt();
            try { watchThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        logger.info("CleanToSapWriter stopped");
    }

    private void watchLoop() {
        try (MongoClient mongoClient = MongoClients.create(mongoUri)) {
            // Watch the farm collection for inserts
            watchCollection(mongoClient, "farm", this::onFarmInsert);
        } catch (Exception e) {
            if (running.get()) {
                logger.error("CleanToSapWriter watch loop failed: {}", e.getMessage(), e);
            }
        }
    }

    private void watchCollection(MongoClient mongoClient, String collectionName, java.util.function.Consumer<Document> handler) {
        MongoCollection<Document> collection = mongoClient.getDatabase(mongoDb).getCollection(collectionName);

        ChangeStreamIterable<Document> changeStream = collection.watch()
                .fullDocument(FullDocument.UPDATE_LOOKUP);

        for (ChangeStreamDocument<Document> change : changeStream) {
            if (!running.get()) break;

            if ("insert".equals(change.getOperationType().getValue())) {
                Document fullDoc = change.getFullDocument();
                if (fullDoc != null) {
                    // Check if this originated from CDC (has legacy field)
                    Document payload = fullDoc.get("payload", Document.class);
                    if (payload != null && payload.containsKey("legacy_werks") &&
                            payload.get("legacy_werks") != null &&
                            !payload.getString("legacy_werks").isEmpty()) {
                        logger.debug("Skipping CDC-originated farm insert");
                        continue;
                    }
                    handler.accept(fullDoc);
                }
            }
        }
    }

    private void onFarmInsert(Document doc) {
        try {
            Document payload = doc.get("payload", Document.class);
            if (payload == null) return;

            String name = payload.getString("name");
            String farmType = payload.getString("farm_type");
            String zone = payload.getString("zone");
            String owner = payload.getString("owner");

            // Reverse-transform farm_type to SAP code
            String typeCode = switch (farmType) {
                case "megafarm" -> "M";
                case "homestead" -> "H";
                default -> "L";
            };

            // Generate a new WERKS (next available)
            String werks = generateNextWerks();
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            try (Connection conn = DriverManager.getConnection(sapJdbcUrl, "postgres", "postgres")) {
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO ZFARM_MSTR (MANDT, WERKS, BUKRS, FARM_NM, FARM_TYP_CD, ZONE_CD, EIGR, " +
                        "ERNAM, ERDAT, ERZET, AENAM, AEDAT, AEZET) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                ps.setString(1, "100");
                ps.setString(2, werks);
                ps.setString(3, "1000");
                ps.setString(4, name != null ? name : "New Farm");
                ps.setString(5, typeCode);
                ps.setString(6, zone != null ? zone : "north");
                ps.setString(7, owner != null ? owner : "MESHQL");
                ps.setString(8, "MESHQL");
                ps.setString(9, today);
                ps.setString(10, "000000");
                ps.setString(11, "MESHQL");
                ps.setString(12, today);
                ps.setString(13, "000000");
                ps.executeUpdate();

                // Pre-register the mapping to prevent dedup issues
                String meshqlId = doc.getObjectId("_id").toString();
                idResolver.registerFarm(werks, meshqlId);

                logger.info("Wrote back farm to SAP: WERKS={}, name={}", werks, name);
            }
        } catch (Exception e) {
            logger.error("Failed to write farm back to SAP: {}", e.getMessage(), e);
        }
    }

    private int werksCounter = 4000;

    private synchronized String generateNextWerks() {
        return String.valueOf(werksCounter++);
    }
}
