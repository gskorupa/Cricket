/*
 * Copyright 2017 Grzegorz Skorupa .
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
package org.cricketmsf.microsite.out.notification;

import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.cricketmsf.Adapter;
import org.cricketmsf.event.Event;
import org.cricketmsf.Kernel;
import org.cricketmsf.out.OutboundAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author greg
 */
public class SmtpSender extends OutboundAdapter implements EmailSenderIface, Adapter {
    private static final Logger logger = LoggerFactory.getLogger(SmtpSender.class);
    String from = "";
    String cc = null;
    String bcc = null;
    String mailhost = "";
    String mailer = "";
    String protocol = "SMTP";
    String user = "";
    String password = "";
    String debugSession = null;
    boolean starttls = true;
    int port = 465;
    boolean ready = true;

    protected HashMap<String, String> statusMap = null;

    @Override
    public void loadProperties(HashMap<String, String> properties, String adapterName) {
        super.loadProperties(properties, adapterName);
        from = properties.getOrDefault("from", "");
        cc = properties.getOrDefault("cc", "");
        bcc = properties.getOrDefault("bcc", "");
        mailhost = properties.getOrDefault("mailhost", "");
        mailer = properties.getOrDefault("mailer", "signode");
        protocol = properties.getOrDefault("protocol", "SMTP");
        user = properties.getOrDefault("user", "");
        password = properties.getOrDefault("password", "");
        debugSession = properties.getOrDefault("debug-session", "false");
        if (from.isEmpty() || mailhost.isEmpty() || user.isEmpty() || password.isEmpty()) {
            ready = false;
        }
        starttls = Boolean.parseBoolean(properties.getOrDefault("starttls", "true"));
        try {
            port = Integer.parseInt(properties.getOrDefault("port", "465"));
        } catch (NumberFormatException e) {
        }
        logger.info("\tfrom: " + from);
        logger.info("\tcc: " + cc);
        logger.info("\tbcc: " + bcc);
        logger.info("\tmailhost: " + mailhost);
        logger.info("\tport: " + port);
        logger.info("\tprotocol: " + protocol);
        logger.info("\tstarttls: " + starttls);
        logger.info("\tmailer: " + mailer);
        logger.info("\tuser: " + user);
        logger.info("\tpassword: " + (password.isEmpty() ? "" : "******"));
        logger.info("\tdebug-session: " + debugSession);
    }

    @Override
    public String send(String recipient, String topic, String content) {
        if (!ready) {
            logger.warn("not configured");
            return "ERROR: not configured";
        }
        if (recipient == null || recipient.isEmpty()) {
            return null;
        }
        boolean debug = "true".equalsIgnoreCase(debugSession);
        String subject = topic;
        String to = recipient;
        String text = content;

        String result = "OK";

        try {

            /*
	     * Initialize the JavaMail Session.
             */
            Properties props = System.getProperties();

            if (mailhost != null) {
                props.put("mail.smtp.host", mailhost);
            }
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.socketFactory.port", "" + port);
            if (starttls) {
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            }

            // Get a Session object
            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                }
            });

            if (debug) {
                session.setDebug(true);
            }

            /*
	     * Construct the message and send it.
             */
            Message msg = new MimeMessage(session);
            if (from != null) {
                msg.setFrom(new InternetAddress(from));
            } else {
                msg.setFrom();
            }

            msg.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(to, false));
            if (cc != null) {
                msg.setRecipients(Message.RecipientType.CC,
                        InternetAddress.parse(cc, false));
            }
            if (bcc != null) {
                msg.setRecipients(Message.RecipientType.BCC,
                        InternetAddress.parse(bcc, false));
            }

            msg.setSubject(subject);
            //msg.setText(text);
            msg.setContent(text, "text/html");

            msg.setHeader("X-Mailer", mailer);
            msg.setSentDate(new Date());

            // send the thing off
            Transport.send(msg);

        } catch (Exception e) {
            //e.printStackTrace();
            logger.warn(e.getMessage());
            result = "ERROR: " + e.getMessage();
        }

        return result;
    }

    @Override
    public void updateStatusItem(String key, String value) {
        statusMap.put(key, value);
    }

    @Override
    public String send(String to, String cc, String bcc, String subject, String text) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String send(String[] recipients, String[] names, String subject, String text) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
