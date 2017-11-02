package com.gmail.socraticphoenix.tiobot;

import java.util.HashMap;
import java.util.Map;

public class SessionCache {
    private Map<Long, CodeSession> sessions = new HashMap<>();

    public CodeSession get(long id) {
        return this.sessions.get(id);
    }

    public CodeSession remove(long id) {
        return this.sessions.remove(id);
    }

    public void create(long id, CodeSession session) {
        this.sessions.put(id, session);
    }

    public boolean contains(long id) {
        return this.sessions.containsKey(id);
    }

}
