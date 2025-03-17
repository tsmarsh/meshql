package com.meshql.core;


import com.meshql.core.config.Graphlette;
import com.meshql.core.config.Restlette;

import java.util.List;

public record Config(
        List<String> casbinParams,
        List<Graphlette> graphlettes,
        int port,
        List<Restlette> restlettes
) {}