package com.meshql.auth.noop;

import com.meshql.core.Auth;
import com.meshql.core.Envelope;
import com.tailoredshapes.stash.Stash;

import java.util.List;

import static com.tailoredshapes.underbar.ocho.UnderBar.list;

public class NoAuth implements Auth {
    List<String> tokens;
    boolean authed;

    public NoAuth() {
        authed = true;
        tokens = list("Token");
    }

    public NoAuth(List<String> tokens, boolean authed) {
        this.tokens = tokens;
        this.authed = authed;
    }

    @Override
    public List<String> getAuthToken(Stash context) {
        return tokens;
    }

    @Override
    public boolean isAuthorized(List<String> credentials, Envelope data) {
        return authed;
    }
}
