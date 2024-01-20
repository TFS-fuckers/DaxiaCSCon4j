package com.tfs.dxcscon4j;

import com.tfs.dxcscon4j.exceptions.AccessToOfflineUserException;

public class User {
    private final ClientHandler HANDLER;
    private final String IDENTIFIER;
    private boolean isConnected = true;

    public User(ClientHandler handler, String identifier) {
        this.HANDLER = handler;
        this.IDENTIFIER = identifier;
    }

    public ClientHandler getHandler() throws AccessToOfflineUserException {
        return this.HANDLER;
    }

    public String getName() {
        return this.IDENTIFIER;
    }

    public boolean isConnected() {
        return isConnected;
    }

    protected void setConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }
}
