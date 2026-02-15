package com.meshql.repositories.merksql;

import com.meshql.repos.certification.RepositoryCertification;

public class MerkSqlRepositoryCertificationTest extends RepositoryCertification {

    @Override
    public void init() {
        MerkSqlConfig config = MerkSqlPlugin.tempConfig("cert-repo-test");
        MerkSqlPlugin plugin = new MerkSqlPlugin();
        MerkSqlStore store = new MerkSqlStore(
                MerkSqlNative.create(config.dataDir), config.topic);
        this.repository = new MerkSqlRepository(store);
    }
}
