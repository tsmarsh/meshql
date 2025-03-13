package com.meshql.core;

import com.tailoredshapes.stash.Stash;

import java.time.Instant;
import java.util.List;

public record Envelope(
        String id,
        Stash payload,
        Instant createdAt,
        boolean deleted,
        List<String> authorizedTokens
){};