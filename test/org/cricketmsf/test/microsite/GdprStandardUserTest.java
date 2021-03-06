/*
 * Copyright 2017 Grzegorz Skorupa <g.skorupa at gmail.com>.
 * Licensed under the Apache License, Version 2.0. See LICENSE file.
 */
package org.cricketmsf.test.microsite;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import org.cricketmsf.Kernel;
import org.cricketmsf.Runner;
import org.cricketmsf.in.http.StandardResult;
import org.cricketmsf.out.http.HttpClient;
import org.cricketmsf.out.http.Request;
import org.junit.*;
import org.junit.runners.MethodSorters;
import com.cedarsoftware.util.io.JsonReader;
import java.util.HashMap;


/**
 *
 * @author Grzegorz Skorupa <g.skorupa at gmail.com>
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GdprStandardUserTest {

    private static Kernel service;
    private static String sessionToken;
    private static String LOGIN = "tester";
    private static String PASSWORD = "cricket";

    public GdprStandardUserTest() {
    }

    @Test
    public void checkValidTokenOK() {
        // Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("GET")
                .setProperty("Accept", "application/json")
                .setUrl("http://localhost:"+port+"/api/auth/"+sessionToken);
        // When
        StandardResult response = (StandardResult) client.send(req, false);
        // Then
        Assert.assertEquals(200, response.getCode());
    }

    @Test
    public void checkFakeTokenNOK() {
        // Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("GET")
                .setProperty("Accept", "application/json")
                .setUrl("http://localhost:"+port+"/api/auth/faketoken");
        // When
        StandardResult response = (StandardResult) client.send(req, false);
        // Then
        Assert.assertEquals(403, response.getCode());
    }

    @Test
    public void readingPersonalDataOK() {
        // Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("GET")
                .setProperty("Accept", "application/json")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/"+LOGIN);
        // When
        StandardResult response = (StandardResult) client.send(req, false);
        // Then
        Assert.assertEquals(200, response.getCode());
        String data = null;
        try {
            data = new String(response.getPayload(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Assert.fail(ex.getMessage());
        }
        Assert.assertFalse(data == null || data.isEmpty());
    }

    @Test
    public void readingOtherUserPersonalDataNOK() {
        //Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("GET")
                .setProperty("Accept", "application/json")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/admin");
        // When
        StandardResult response = (StandardResult) client.send(req);
        // Then
        Assert.assertEquals(403, response.getCode());
    }

    @Test
    public void updatingPersonalDataOK() {
        String newEmail="X@xx.yy.zz";
        //Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("PUT")
                .setProperty("Content-Type", "application/x-www-form-urlencoded")
                .setProperty("Accept", "application/json")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/"+LOGIN)
                .setData("email="+newEmail);
        // When
        StandardResult response = (StandardResult) client.send(req,true);
        // Then
        Assert.assertEquals(200, response.getCode());
        String data = null;
        try {
            data = new String(response.getPayload(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Assert.fail(ex.getMessage());
        }
        HashMap userData = (HashMap)JsonReader.jsonToJava(data);
        Assert.assertEquals("expecting "+newEmail,newEmail,userData.get("email"));
    }

    @Test
    public void updatingOtherUserPersonalDataNOK() {
        String newEmail="X@xx.yy.zz";
        //Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("PUT")
                .setProperty("Accept", "application/json")
                .setProperty("Content-Type", "application/x-www-form-urlencoded")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/admin")
                .setData("email="+newEmail);
        // When
        StandardResult response = (StandardResult) client.send(req);
        // Then
        Assert.assertEquals(403, response.getCode());
    }
    
    @Test
    public void y_unregisteringUserOK() {
        String newEmail="X@xx.yy.zz";
        //Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("PUT")
                .setProperty("Accept", "application/json")
                .setProperty("Content-Type", "application/x-www-form-urlencoded")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/"+LOGIN)
                .setData("unregisterRequested=true");
        // When
        StandardResult response = (StandardResult) client.send(req);
        // Then
        Assert.assertEquals(200, response.getCode());
        String data = null;
        try {
            data = new String(response.getPayload(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Assert.fail(ex.getMessage());
        }
        System.out.println(data);
        HashMap userData = (HashMap)JsonReader.jsonToJava(data);
        Assert.assertEquals("expecting true",Boolean.TRUE,userData.get("unregisterRequested"));
    }

    @Test
    public void deletingOtherUserPersonalDataNOK() {
        //Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("DELETE")
                .setProperty("Accept", "application/json")
                .setProperty("Content-Type", "application/x-www-form-urlencoded")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/admin")
                .setData("aa=zz");
        // When
        StandardResult response = (StandardResult) client.send(req);
        // Then
        Assert.assertEquals(403, response.getCode());
    }

    @Test
    public void z_deletingPersonalDataNOK() {
        //Given
        int port = service.getPort();
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("DELETE")
                .setProperty("Accept", "application/json")
                .setProperty("Content-Type", "application/x-www-form-urlencoded")
                .setProperty("Authentication", sessionToken)
                .setUrl("http://localhost:"+port+"/api/user/"+LOGIN)
                .setData("aa=zz");
        // When
        StandardResult response = (StandardResult) client.send(req);
        // Then
        Assert.assertEquals(403, response.getCode());
    }

    /**
     *
     */
    private static String getSessionToken() {
        // Given
        int port = service.getPort();
        String login = LOGIN;
        String password = PASSWORD;
        String credentials = Base64.getEncoder().encodeToString((login + ":" + password).getBytes());
        HttpClient client = new HttpClient().setCertificateCheck(false);
        Request req = new Request()
                .setMethod("POST")
                .setProperty("Accept", "text/plain")
                .setProperty("Authentication", "Basic " + credentials)
                .setData("p=ignotethis") /*data must be added to POST or PUT requests */
                .setUrl("http://localhost:"+port+"/api/auth");
        // When
        StandardResult response = (StandardResult) client.send(req);
        // Then
        Assert.assertEquals(200, response.getCode());
        String token = "";
        try {
            token = new String(response.getPayload(), "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            Assert.fail(ex.getMessage());
        }
        Assert.assertFalse(token == null || token.isEmpty());
        return token;
    }

    @Before
    public void checkService() {
        System.out.println("@before");
        Assert.assertNotNull(service);
    }

    @BeforeClass
    public static void setup() {
        System.out.println("@setup");
        String[] args = {"-r", "-s", "Microsite"};
        service = Runner.getService(args);
        while (!service.isInitialized()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("service is running");
        sessionToken = getSessionToken();
    }

    @AfterClass
    public static void shutdown() {
        System.out.println("@shutdown");
        service.shutdown();
    }

}
