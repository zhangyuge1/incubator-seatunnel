/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.email.sink;

import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.common.sink.AbstractSinkWriter;
import org.apache.seatunnel.connectors.seatunnel.email.config.EmailSinkConfig;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import com.sun.mail.util.MailSSLSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

public class EmailSinkWriter extends AbstractSinkWriter<SeaTunnelRow, Void> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSinkWriter.class);

    private final SeaTunnelRowType seaTunnelRowType;
    private EmailSinkConfig config;
    private StringBuffer stringBuffer;

    public EmailSinkWriter(SeaTunnelRowType seaTunnelRowType, Config pluginConfig) {
        this.seaTunnelRowType = seaTunnelRowType;
        this.config = new EmailSinkConfig(pluginConfig);
        this.stringBuffer = new StringBuffer();
    }

    @Override
    public void write(SeaTunnelRow element) {
        Object[] fields = element.getFields();

        for (Object field : fields) {
            stringBuffer.append(field.toString() + ",");
        }
        stringBuffer.deleteCharAt(fields.length - 1);
        stringBuffer.append("\n");

    }

    @Override
    public void close() {
        createFile();
        Properties properties = new Properties();

        properties.setProperty("mail.host", config.getEmailHost());

        properties.setProperty("mail.transport.protocol", config.getEmailTransportProtocol());

        properties.setProperty("mail.smtp.auth", config.getEmailSmtpAuth());

        try {
            MailSSLSocketFactory sf = new MailSSLSocketFactory();
            sf.setTrustAllHosts(true);
            properties.put("mail.smtp.ssl.enable", "true");
            properties.put("mail.smtp.ssl.socketFactory", sf);
            Session session = Session.getDefaultInstance(properties, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.getEmailFromAddress(), config.getEmailAuthorizationCode());
                }
            });
            //Create the default MimeMessage object
            MimeMessage message = new MimeMessage(session);

            // Set the email address
            message.setFrom(new InternetAddress(config.getEmailFromAddress()));

            // Set the recipient email address
            message.addRecipient(Message.RecipientType.TO,
                    new InternetAddress(config.getEmailToAddress()));

            // Setting the Email subject
            message.setSubject(config.getEmailMessageHeadline());

            // Create Message
            BodyPart messageBodyPart = new MimeBodyPart();

            // Set Message content
            messageBodyPart.setText(config.getEmailMessageContent());

            // Create multiple messages
            Multipart multipart = new MimeMultipart();
            // Set up the text message section
            multipart.addBodyPart(messageBodyPart);
            // accessory
            messageBodyPart = new MimeBodyPart();
            String filename = "emailsink.csv";
            DataSource source = new FileDataSource(filename);
            messageBodyPart.setDataHandler(new DataHandler(source));
            messageBodyPart.setFileName(filename);
            multipart.addBodyPart(messageBodyPart);
            message.setContent(multipart);

            //   send a message
            Transport.send(message);
            LOGGER.info("Sent message successfully....");
        } catch (Exception e) {
            LOGGER.warn("send email Fail.", e);
            throw new RuntimeException("send email Fail.", e);
        }
    }

    public void createFile() {
        try {
            String data = stringBuffer.toString();
            File file = new File("emailsink.csv");
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file.getName());
            fileWritter.write(data);
            fileWritter.close();
            LOGGER.info("Create File successfully....");
        } catch (IOException e) {
            LOGGER.warn("Create File Fail.", e);
            throw new RuntimeException("Create File Fail.", e);
        }

    }
}
