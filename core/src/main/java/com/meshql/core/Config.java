package com.meshql.core;


import com.meshql.core.config.GraphletteConfig;
import com.meshql.core.config.RestletteConfig;

import java.util.List;

public record Config(
        List<String> casbinParams,
        List<GraphletteConfig> graphlettes,
        int port,
        List<RestletteConfig> restlettes
) {}