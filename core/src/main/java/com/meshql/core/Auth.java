package com.meshql.core;

import com.tailoredshapes.stash.Stash;
import java.util.List;

public interface Auth {
    List<String> getAuthToken(Stash context);
    boolean isAuthorized(List<String> credentials, Envelope data);
} 
