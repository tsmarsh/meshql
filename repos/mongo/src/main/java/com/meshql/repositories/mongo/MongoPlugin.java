package com.meshql.repositories.mongo;

import com.meshql.core.*;
import com.meshql.core.config.StorageConfig;
import com.mongodb.client.*;
import org.bson.Document;

public class MongoPlugin implements Plugin {
    private final Auth auth;

    public MongoPlugin(Auth auth) {
        this.auth = auth;
    }

    @Override
    public Searcher createSearcher(StorageConfig sc) {
        MongoConfig config = (MongoConfig)sc;
        MongoClient mongoClient = MongoClients.create(config.uri);
        MongoDatabase database = mongoClient.getDatabase(config.db);

        MongoCollection<Document> collection = database.getCollection(config.collection);

        MongoSearcher searcher = new MongoSearcher(collection, auth);
        return searcher;
    }

    @Override
    public Repository createRepository(StorageConfig sc, Auth auth) {
        MongoConfig config = (MongoConfig)sc;
        MongoClient mongoClient = MongoClients.create(config.uri);
        MongoDatabase database = mongoClient.getDatabase(config.db);

        MongoCollection<Document> collection = database.getCollection(config.collection);

        MongoRepository repository = new MongoRepository(collection);
        return repository;
    }

    @Override
    public void cleanUp() {

    }
}
