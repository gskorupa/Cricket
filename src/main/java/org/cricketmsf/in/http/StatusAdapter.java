/*
 * Copyright 2020 Grzegorz Skorupa <g.skorupa at gmail.com>.
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

import org.cricketmsf.RequestObject;
import org.cricketmsf.event.HttpEvent;
import org.cricketmsf.event.ProcedureCall;

/**
 *
 * @author Grzegorz Skorupa <g.skorupa at gmail.com>
 */
public class StatusAdapter
        extends HttpPortedAdapter /*implements Adapter, HttpAdapterIface, HttpHandler, InboundAdapterIface/*, org.eclipse.jetty.server.Handler*/ {

    public StatusAdapter() {
        super();
    }

    @Override
    protected ProcedureCall preprocess(RequestObject request, long rootEventId) {
        HttpEvent event = new HttpEvent("getStatus", request);
        return ProcedureCall.toForward(event);
    }

}
