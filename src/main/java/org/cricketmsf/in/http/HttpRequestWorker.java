package org.cricketmsf.in.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.cricketmsf.Kernel;
import org.cricketmsf.RequestObject;
import org.cricketmsf.api.ResponseCode;
import org.cricketmsf.api.ResultIface;
import org.cricketmsf.api.StandardResult;
import org.cricketmsf.event.ProcedureCall;
import static org.cricketmsf.in.http.HttpPortedAdapter.JSON;
import static org.cricketmsf.in.http.HttpPortedAdapter.WEBSITE_MODE;
import org.cricketmsf.util.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author greg
 */
public class HttpRequestWorker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(HttpRequestWorker.class);
    private HttpExchange exchange;
    private HttpPortedAdapter adapter;

    public HttpRequestWorker(HttpExchange exchange, HttpPortedAdapter adapter) {
        this.exchange = exchange;
        this.adapter = adapter;
    }

    @Override
    public void run() {

        long rootEventId = Kernel.getEventId();
        try {
            Stopwatch timer = null;
            if (logger.isDebugEnabled()) {
                timer = new Stopwatch();
            }
            String acceptedResponseType = JSON;
            try {
                acceptedResponseType
                        = adapter.acceptedTypesMap.getOrDefault(exchange.getRequestHeaders().get("Accept").get(0), JSON);
            } catch (Exception e) {
            }
            // cerating Result object
            RequestObject requestObject = buildRequestObject(exchange, acceptedResponseType);
            if (null != adapter.properties.get("dump-request") && "true".equalsIgnoreCase(adapter.properties.get("dump-request"))) {
                logger.info(dumpRequest(requestObject));
            }

            ResultIface result = createResponse(requestObject, rootEventId);

            acceptedResponseType = adapter.setResponseType(acceptedResponseType, result.getFileExtension());
            //set content type and print response to string format as JSON if needed
            Headers headers = exchange.getResponseHeaders();
            byte[] responseData;
            Iterator it = result.getHeaders().keySet().iterator();
            String key;
            while (it.hasNext()) {
                key = (String) it.next();
                List<String> values = result.getHeaders().get(key);
                for (int i = 0; i < values.size(); i++) {
                    headers.set(key, values.get(i));
                }
            }
            switch (result.getCode()) {
                case ResponseCode.MOVED_PERMANENTLY:
                case ResponseCode.MOVED_TEMPORARY:
                    if (!headers.containsKey("Location")) {
                        String newLocation = result.getMessage() != null ? result.getMessage() : "/";
                        headers.set("Location", newLocation);
                        responseData = ("moved to ".concat(newLocation)).getBytes("UTF-8");
                    } else {
                        responseData = "".getBytes();
                    }
                    break;
                case ResponseCode.NOT_FOUND:
                    headers.set("Content-type", "text/html");
                    responseData = result.getPayload();
                    break;
                default:
                    if (!headers.containsKey("Content-type")) {
                        if (adapter.acceptedTypesMap.containsKey(acceptedResponseType)) {
                            headers.set("Content-type", acceptedResponseType.concat("; charset=UTF-8"));
                            responseData = adapter.formatResponse(acceptedResponseType, result);
                        } else {
                            headers.set("Content-type", adapter.getMimeType(result.getFileExtension()));
                            responseData = result.getPayload();
                        }
                    } else {
                        String rContentType = headers.getFirst("Content-type");
                        headers.set("Content-type", rContentType.concat("; charset=UTF-8"));
                        if (adapter.acceptedTypesMap.containsKey(rContentType)) {
                            responseData = adapter.formatResponse(rContentType, result);
                        } else {
                            responseData = result.getPayload();
                        }
                    }
                    headers.set("Last-Modified", result.getModificationDateFormatted());
                    //TODO: get max age and no-cache info from the result object
                    if (result.getMaxAge() > 0) {
                        headers.set("Cache-Control", "max-age=" + result.getMaxAge());  // 1 hour
                    } else {
                        headers.set("Pragma", "no-cache");
                    }
                    if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
                        CorsProcessor.getResponseHeaders(headers, exchange.getRequestHeaders(), Kernel.getInstance().getCorsHeaders());
                    } else if (exchange.getRequestURI().getPath().startsWith("/api/")) { //TODO: this is workaround
                        CorsProcessor.getResponseHeaders(headers, exchange.getRequestHeaders(), Kernel.getInstance().getCorsHeaders());
                    } else if (exchange.getRequestURI().getPath().endsWith(".tag")) { //TODO: this is workaround
                        CorsProcessor.getResponseHeaders(headers, exchange.getRequestHeaders(), Kernel.getInstance().getCorsHeaders());
                    }
                    if (result.getCode() == 0) {
                        result.setCode(ResponseCode.OK);
                    } else {
                        if (responseData.length == 0) {
                            if (result.getMessage() != null) {
                                responseData = result.getMessage().getBytes("UTF-8");
                            }
                        }
                    }
                    break;
            }

            if (logger.isDebugEnabled()) {
                logger.debug("event " + rootEventId + " processing takes " + timer.time(TimeUnit.MILLISECONDS) + "ms");
            }

            if (responseData.length > 0) {
                exchange.sendResponseHeaders(result.getCode(), responseData.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(responseData);
                }
            } else {
                exchange.sendResponseHeaders(result.getCode(), -1);
            }
            //sendLogEvent(exchange, responseData.length);
            result = null;
        } catch (IOException e) {
            e.printStackTrace();
            logger.warn(exchange.getRequestURI().getPath() + " " + e.getMessage());
        }
        exchange.close();

    }

    RequestObject buildRequestObject(HttpExchange exchange, String acceptedResponseType) {
        // Remember that "parameters" attribute is created by filter
        Map<String, Object> parameters = (Map<String, Object>) exchange.getAttribute("parameters");
        String method = exchange.getRequestMethod();
        String pathExt = exchange.getRequestURI().getPath();
        if (null != pathExt) {
            pathExt = pathExt.substring(exchange.getHttpContext().getPath().length());
            while (pathExt.startsWith("/")) {
                pathExt = pathExt.substring(1);
            }
        }

        RequestObject requestObject = new RequestObject();
        requestObject.method = method;
        requestObject.parameters = parameters;
        requestObject.uri = exchange.getRequestURI().toString();
        requestObject.pathExt = pathExt;
        requestObject.headers = exchange.getRequestHeaders();
        requestObject.clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
        requestObject.acceptedResponseType = acceptedResponseType;
        requestObject.body = (String) exchange.getAttribute("body");
        if (null == requestObject.body) {
            requestObject.body = "";
        }
        return requestObject;
    }
    
    private ResultIface createResponse(RequestObject requestObject, long rootEventId) {
        String methodName = null;
        ResultIface result = new StandardResult();
        if (adapter.mode == WEBSITE_MODE) {
            if (!requestObject.uri.endsWith("/")) {
                if (requestObject.uri.lastIndexOf("/") > requestObject.uri.lastIndexOf(".")) {
                    // redirect to index.file but only if property index.file is not null
                    result.setCode(ResponseCode.MOVED_PERMANENTLY);
                    result.setMessage(requestObject.uri.concat("/"));
                    return result;
                }
            }
        }

        try {
            ProcedureCall pCall = adapter.preprocess(requestObject, Kernel.getEventId());
            if (pCall.requestHandled) { // request processed by the adapter
                if (pCall.responseCode < 100 || pCall.responseCode > 1000) {
                    result.setCode(ResponseCode.BAD_REQUEST);
                } else {
                    result.setCode(pCall.responseCode);
                }
                result.setData(pCall.response);
                result.setHeader("Content-type", pCall.contentType);
            } else { // pCall must be processed by the Kernel
                logger.debug("sending request to hook method " + pCall.procedure + "@" + pCall.event.getClass().getSimpleName());
                result = (ResultIface) Kernel.getInstance().getEventProcessingResult(
                        pCall.event,
                        pCall.procedure
                );
                if (null != result) {
                    if (pCall.responseCode != 0) {
                        result.setCode(pCall.responseCode);
                    } else {
                        result = adapter.postprocess(result);
                        if (null!=result && (result.getCode() < 100 || result.getCode() > 1000)) {
                            result.setCode(ResponseCode.BAD_REQUEST);
                        }
                    }
                }
            }
        } catch (ClassCastException e) {
            logger.warn(e.getMessage());
            result.setCode(ResponseCode.INTERNAL_SERVER_ERROR);
            result.setMessage("handler method error");
            result.setFileExtension(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (null == result) {
            result = new StandardResult("null result returned by the service");
            result.setCode(ResponseCode.INTERNAL_SERVER_ERROR);
        }
        return result;
    }
    
        public static String dumpRequest(RequestObject req) {
        StringBuilder sb = new StringBuilder();
        sb.append("************** REQUEST ****************").append("\r\n");
        sb.append("URI:").append(req.uri).append("\r\n");
        sb.append("PATHEXT:").append(req.pathExt).append("\r\n");
        sb.append("METHOD:").append(req.method).append("\r\n");
        sb.append("ACCEPT:").append(req.acceptedResponseType).append("\r\n");
        sb.append("CLIENT IP:").append(req.clientIp).append("\r\n");
        sb.append("***BODY:").append("\r\n");
        sb.append(req.body).append("\r\n");
        sb.append("***BODY.").append("\r\n");
        sb.append("***HEADERS:").append("\r\n");
        req.headers.keySet().forEach(key -> {
            sb.append(key)
                    .append(":")
                    .append(req.headers.getFirst(key))
                    .append("\r\n");
        });
        sb.append("***HEADERS.").append("\r\n");
        sb.append("***PARAMETERS:").append("\r\n");
        req.parameters.keySet().forEach(key -> {
            sb.append(key)
                    .append(":")
                    .append(req.parameters.get(key))
                    .append("\r\n");
        });
        sb.append("***PARAMETERS.").append("\r\n");
        return sb.toString();
    }


}
