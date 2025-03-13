package com.meshql.server;


import java.util.List;

public record Config(
        List<String> casbinParams,
        List<Graphlette> graphlettes,
        int port,
        List<Restlette> restlettes
) {}