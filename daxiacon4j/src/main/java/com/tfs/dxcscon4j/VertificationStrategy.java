package com.tfs.dxcscon4j;

import com.tfs.dxcscon4j.protocol.Vertification;

@FunctionalInterface
public interface VertificationStrategy {
    public boolean vertify(Vertification vertification);    
}
