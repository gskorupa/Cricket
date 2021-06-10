package org.cricketmsf.in.openapi;

import org.cricketmsf.Kernel;

/**
 *
 * @author greg
 */
public interface OpenApiIface {
    
    public void init(Kernel service);
    public String toJson();
    public String toYaml();
    
}
