package com.meshql.core;

import com.tailoredshapes.stash.Stash;

import java.util.List;
import java.util.Map;

public interface Filler {
    Stash fillOne(Map<String, Object> data, long timestamp);

    List<Stash> fillMany(List<Stash> data, long timestamp);
}
