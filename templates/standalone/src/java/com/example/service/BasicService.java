/*
 * Copyright 2015 Grzegorz Skorupa <g.skorupa at gmail.com>.
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
package com.example.service;

import com.gskorupa.cricket.Event;
import com.gskorupa.cricket.EventHook;
import com.gskorupa.cricket.HttpAdapterHook;
import com.gskorupa.cricket.Kernel;
import com.gskorupa.cricket.RequestObject;
import com.gskorupa.cricket.in.HttpAdapter;
import com.gskorupa.cricket.in.ParameterMapResult;
import com.gskorupa.cricket.out.LoggerAdapterIface;
import java.util.HashMap;
import java.util.Map;
import com.gskorupa.cricket.in.EchoHttpAdapterIface;
import com.gskorupa.cricket.in.HtmlGenAdapterIface;
import com.gskorupa.cricket.in.SchedulerIface;
import com.gskorupa.cricket.out.HtmlReaderAdapterIface;
import com.gskorupa.cricket.out.KeyValueCacheAdapterIface;

/**
 * EchoService
 *
 * @author greg
 */
public class BasicService extends Kernel {

    // adapterClasses
    LoggerAdapterIface logAdapter = null;
    EchoHttpAdapterIface httpAdapter = null;
    KeyValueCacheAdapterIface cache = null;
    SchedulerIface scheduler = null;
    HtmlGenAdapterIface htmlAdapter = null;
    HtmlReaderAdapterIface htmlReaderAdapter = null;

    public BasicService() {
        adapters = new Object[6];
        adapters[0] = logAdapter;
        adapters[1] = httpAdapter;
        adapters[2] = cache;
        adapters[3] = scheduler;
        adapters[4] = htmlAdapter;
        adapters[5] = htmlReaderAdapter;
        adapterClasses = new Class[6];
        adapterClasses[0] = LoggerAdapterIface.class;
        adapterClasses[1] = EchoHttpAdapterIface.class;
        adapterClasses[2] = KeyValueCacheAdapterIface.class;
        adapterClasses[3] = SchedulerIface.class;
        adapterClasses[4] = HtmlGenAdapterIface.class;
        adapterClasses[5] = HtmlReaderAdapterIface.class;
    }

    @Override
    public void getAdapters() {
        logAdapter = (LoggerAdapterIface) super.adapters[0];
        httpAdapter = (EchoHttpAdapterIface) super.adapters[1];
        cache = (KeyValueCacheAdapterIface) super.adapters[2];
        scheduler = (SchedulerIface) super.adapters[3];
        htmlAdapter = (HtmlGenAdapterIface) super.adapters[4];
        htmlReaderAdapter = (HtmlReaderAdapterIface) super.adapters[5];
    }

    @Override
    public void runOnce() {
        super.runOnce();
        System.out.println("Hello from EchoService.runOnce()");
    }

    @HttpAdapterHook(handlerClassName = "EchoHttpAdapterIface", requestMethod = "GET")
    public Object doGet(Event requestEvent) {
        return sendEcho((RequestObject) requestEvent.getPayload());
    }

    @HttpAdapterHook(handlerClassName = "EchoHttpAdapterIface", requestMethod = "POST")
    public Object doPost(Event requestEvent) {
        return sendEcho((RequestObject) requestEvent.getPayload());
    }

    @HttpAdapterHook(handlerClassName = "EchoHttpAdapterIface", requestMethod = "PUT")
    public Object doPut(Event requestEvent) {
        return sendEcho((RequestObject) requestEvent.getPayload());
    }

    @HttpAdapterHook(handlerClassName = "EchoHttpAdapterIface", requestMethod = "DELETE")
    public Object doDelete(Event requestEvent) {
        return sendEcho((RequestObject) requestEvent.getPayload());
    }

    @EventHook(eventCategory = "LOG")
    public void logEvent(Event event) {
        logAdapter.log(event);
    }

    @EventHook(eventCategory = "*")
    public void processEvent(Event event) {
        if(event.getTimePoint()!=null){
            scheduler.handleEvent(event);
        }else{
            System.out.println(event.getPayload().toString());
        }
        //does nothing
    }

    public Object sendEcho(RequestObject request) {
        
        //
        Long counter;
        counter = (Long) cache.get("counter", new Long(0));
        counter++;
        cache.put("counter", counter);

        ParameterMapResult r = new ParameterMapResult();
        HashMap<String, Object> data = new HashMap();
        Map<String, Object> map = request.parameters;
        data.put("request.method", request.method);
        data.put("request.pathExt", request.pathExt);
        data.put("echo counter", cache.get("counter"));
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            //System.out.println(entry.getKey() + "=" + entry.getValue());
            data.put(entry.getKey(), (String) entry.getValue());
        }
        if (data.containsKey("error")) {
            r.setCode(HttpAdapter.SC_INTERNAL_SERVER_ERROR);
            data.put("error", "error forced by request");
        } else {
            r.setCode(HttpAdapter.SC_OK);
        }
        r.setData(data);
        return r;
    }
    
}