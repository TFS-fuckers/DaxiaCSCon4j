package com.tfs.dxcscon4j.exceptions;

public class AccessToOfflineUserException extends Exception{
    public AccessToOfflineUserException() {
        super("Trying to access an offline user");
    }
}
