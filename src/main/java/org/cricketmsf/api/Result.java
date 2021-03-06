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
package org.cricketmsf.api;

import com.sun.net.httpserver.Headers;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author greg
 */
@XmlRootElement(name = "result")
@XmlAccessorType(XmlAccessType.FIELD)
public class Result implements ResultIface {

    private Object data = null;
    private int code;
    //private String procedureName=null;
    private int procedure;

    public Result() {
        setCode(ResponseCode.OK);
    }

    public Result(Object data) {
        setCode(ResponseCode.OK);
        setData(data);
    }

    public Result(Object data, int procedure) {
        setCode(ResponseCode.OK);
        setData(data);
        this.procedure = procedure;
    }

    /*
    public Result(Object data, String procedureName) {
        setCode(ResponseCode.OK);
        setData(data);
        setProcedureName(procedureName);
    }
*/

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(Object data) {
        this.data = data;
    }
    
    public String getContentType(){
        return getHeaders().getFirst("Content-type");
    }
    
    public void setContentType(String contentType){
        getHeaders().set("Content-type", contentType);
    } 

    /*
    @Override
    public String getProcedureName() {
        return procedureName;
    }

    @Override
    public void setProcedureName(String procedureName) {
        this.procedureName = procedureName;
    }
     */

    @Override
    public String getMessage() {
        return null;
    }

    @Override
    public void setMessage(String message) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public byte[] getPayload() {
        return null;
    }

    @Override
    public void setPayload(byte[] payload) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getFileExtension() {
        return null;
    }

    @Override
    public void setFileExtension(String fileExt) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setModificationDate(Date date) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Date getModificationDate() {
        return null;
    }

    @Override
    public String getModificationDateFormatted() {
        SimpleDateFormat dt1 = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z",Locale.ENGLISH);
        return dt1.format(new Date());
    }

    @Override
    public int getMaxAge() {
        return 0;
    }

    @Override
    public void setMaxAge(int maxAge) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setHeader(String name, String value) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setHeader(String name, List values) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Headers getHeaders() {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setResponseTime(long time) {
        throw new UnsupportedOperationException("not supported"); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public long getResponseTime() {
        return -1;
    }

    /*
    @Override
    public Result procedureName(String procedureName) {
        this.procedureName = procedureName;
        return this;
    }
    */

    /**
     * @return the procedure
     */
    @Override
    public int getProcedure() {
        return procedure;
    }

    /**
     * @param procedure the procedure to set
     */
    @Override
    public void setProcedure(int procedure) {
        this.procedure = procedure;
    }

    @Override
    public Result procedure(int procedure) {
        this.procedure = procedure;
        return this;
    }


}
