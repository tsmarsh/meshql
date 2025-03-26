package com.meshql.repos.sqlite;

import com.meshql.core.Auth;
import com.meshql.core.Plugin;
import com.meshql.core.Repository;
import com.meshql.core.Searcher;
import com.meshql.core.config.StorageConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.tailoredshapes.underbar.ocho.Die.rethrow;
import static com.tailoredshapes.underbar.ocho.UnderBar.each;

public class SQLitePlugin implements Plugin {
    private final Auth auth;
    Map<String, SQLiteDataSource> dataSources = new ConcurrentHashMap<>();

    public SQLitePlugin(Auth auth) {
        this.auth = auth;
    }


    @Override
    public Searcher createSearcher(StorageConfig config) {
        assert config instanceof SQLiteConfig;
        SQLiteConfig c = (SQLiteConfig)config;
        var dataSource = buildDatasource(c);
        return new SQLiteSearcher(dataSource, c.table, auth);
    }

    private synchronized DataSource buildDatasource(SQLiteConfig c) {
        if(dataSources.containsKey(c.file)){
            return dataSources.get(c.file);
        } else {
            var dataSource = new SQLiteDataSource();
            dataSource.setUrl("jdbc:sqlite:" + c.file);
            dataSources.put(c.file, dataSource);
            return dataSource;
        }
    }

    @Override
    public Repository createRepository(StorageConfig config, Auth auth) {
        assert config instanceof SQLiteConfig;
        SQLiteConfig c = (SQLiteConfig)config;
        var dataSource = buildDatasource(c);
        SQLiteRepository sqLiteRepository = new SQLiteRepository(rethrow(() -> dataSource.getConnection()), c.table);
        sqLiteRepository.initialize();
        return sqLiteRepository;
    }

    @Override
    public void cleanUp() {
        each(dataSources, (k,v) -> {
           if(!k.startsWith(":memory:")){
               File file = new File(k);
               file.delete();
           }
        });
    }
}
