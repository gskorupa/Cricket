/*
 * Copyright 2016 Grzegorz Skorupa .
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

import org.cricketmsf.api.ResultIface;
import org.cricketmsf.Adapter;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.cricketmsf.RequestObject;
import org.cricketmsf.event.Procedures;
import org.cricketmsf.event.HttpEvent;
import org.cricketmsf.event.ProcedureCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Grzegorz Skorupa 
 */
public class HtmlGenAdapter extends HttpPortedAdapter implements HtmlGenAdapterIface, Adapter {
    private static final Logger logger = LoggerFactory.getLogger(HtmlGenAdapter.class);

    private boolean useCache = false;
    private boolean processingVariables = false;
    
    public HtmlGenAdapter(){
        super();
        mode = WEBSITE_MODE;
    }

    /**
     * This method is executed while adapter is instantiated during the service
     * start. It's used to configure the adapter according to the configuration.
     *
     * @param properties map of properties readed from the configuration file
     * @param adapterName name of the adapter set in the configuration file (can
     * be different from the interface and class name.
     */
    @Override
    public void loadProperties(HashMap<String, String> properties, String adapterName) {
        super.loadProperties(properties, adapterName);
        //super.getServiceHooks(adapterName);
        setContext(properties.get("context"));
        logger.info("\tcontext=" + getContext());
        useCache = properties.getOrDefault("use-cache", "false").equalsIgnoreCase("true");
        logger.info("\tuse-cache=" + useCache());
        processingVariables= (properties.getOrDefault("page-processor", "false").equalsIgnoreCase("true"));
        logger.info("\tpage-processor=" + processingVariables);
    }

    @Override
    public boolean useCache() {
        return useCache;
    }

    /**
     * Formats response sent back by this adapter
     *
     * @param type TODO doc
     * @param result received back from the service
     * @return the payload field of the result modified with parameters
     */
    @Override
    public byte[] formatResponse(String type, ResultIface result) {
        if (HTML.equalsIgnoreCase(type) && processingVariables) {
            // && !Boolean.parseBoolean(result.getHeaders().getFirst("X-from-cache"))
            return updateHtml((ParameterMapResult) result);
        } else {
            return result.getPayload();
        }
    }

    @Override
    protected String setResponseType(String oryginalResponseType, String fileExt) {
        if (fileExt != null) {
            switch (fileExt) {
                case ".html":
                case ".htm":
                    return "text/html";
                default:
                    return "";
            }
        } else {
            return oryginalResponseType;
        }
    }

    private byte[] updateHtml(ParameterMapResult result) {
        if (result.getData() != null && result.getPayload()!=null) {
            HashMap map = (HashMap) result.getData();
            if (result.getPayload().length>0 && !map.isEmpty()) {
                Pattern p = Pattern.compile("(\\$\\w+)");
                Matcher m = p.matcher(new String(result.getPayload()));
                StringBuffer res = new StringBuffer();
                String paramName;
                String replacement;
                while (m.find()) {
                    paramName = m.group().substring(1);
                    replacement = (String) map.getOrDefault(paramName, m.group());
                    try {
                        m.appendReplacement(res, replacement);
                    } catch (Exception e) {
                    }
                }
                m.appendTail(res);
                return res.toString().getBytes();
            }
        }
        return result.getPayload();
    }

    @Override
    protected ProcedureCall preprocess(RequestObject request) {
        HttpEvent event = new HttpEvent(Procedures.WWW,request);
        return ProcedureCall.toForward(event);
    }

}
