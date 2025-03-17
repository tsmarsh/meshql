package com.meshql.core.config;


import java.net.URL;

public record Resolver(
        String name,
        String id,
        String queryName,
        URL url
) {}