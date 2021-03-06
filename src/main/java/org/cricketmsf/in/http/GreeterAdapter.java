/*
 * Copyright 2020 Grzegorz Skorupa .
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cricketmsf.in.http;

import java.util.HashMap;
import org.cricketmsf.RequestObject;
import org.cricketmsf.event.Procedures;
import org.cricketmsf.api.ResultIface;
import org.cricketmsf.api.StandardResult;
import org.cricketmsf.event.GreeterEvent;
import org.cricketmsf.event.ProcedureCall;

/**
 *
 * @author Grzegorz Skorupa 
 */
public class GreeterAdapter
        extends HttpPortedAdapter /*implements Adapter, HttpAdapterIface, HttpHandler, InboundAdapterIface/*, org.eclipse.jetty.server.Handler*/ {

    public static int PARAM_NOT_FOUND = 1;

    public GreeterAdapter() {
        super();
    }

    @Override
    protected ProcedureCall preprocess(RequestObject request) {
        // validation and translation 
        String name = (String) request.parameters.getOrDefault("name", "");
        if (name.isEmpty() || !"world".equalsIgnoreCase(name)) {
            HashMap<String, Object> err = new HashMap<>();
            err.put("code", PARAM_NOT_FOUND); //code<100 || code >1000
            // http status codes can be used directly:
            // err.put("code", 404);
            err.put("message", "unknown name or name parameter not found (must be 'world')");
            return ProcedureCall.toRespond(PARAM_NOT_FOUND, err);
        }
        // building resulting call
        HashMap<String,String>data=new HashMap<>();
        data.put("name", name);
        GreeterEvent event = new GreeterEvent(data);
        event.setProcedure(Procedures.GREET);
        return ProcedureCall.toForward(event);
    }
    
    @Override
    protected ResultIface postprocess(ResultIface fromService){
        StandardResult result=new StandardResult(fromService.getData());
        result.setHeader("Content-type", "text/plain");
        return result;
    }

}
