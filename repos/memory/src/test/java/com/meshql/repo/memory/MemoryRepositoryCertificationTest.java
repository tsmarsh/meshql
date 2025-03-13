package com.meshql.repo.memory;

import com.meshql.repos.certification.RepositoryCertification;
import com.meshql.repositories.memory.InMemoryRepository;

public class MemoryRepositoryCertificationTest extends RepositoryCertification {

    @Override
    public void init() {
        this.repository = new InMemoryRepository();
    }

}
