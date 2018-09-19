/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.notifier.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.cache.FileTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import io.gravitee.notifier.api.AbstractNotifier;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotificationConfiguration;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.ext.mail.MailClient.createShared;
import static java.lang.String.valueOf;
import static java.nio.file.Files.readAllBytes;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.springframework.ui.freemarker.FreeMarkerTemplateUtils.processTemplateIntoString;

public class EmailNotifier extends AbstractNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotifier.class);

    @Value("${notifiers.email.templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    @Autowired
    private ObjectMapper mapper;

    private Configuration config = new Configuration(Configuration.VERSION_2_3_28);

    @PostConstruct
    public void init() throws IOException {
        config.setTemplateLoader(new FileTemplateLoader(new File(templatesPath)));
    }

    @Override
    protected String getType() {
        return "email";
    }

    @Override
    public CompletableFuture<Void> doSend(final Notification notification, final Map<String, Object> parameters) {
        final CompletableFuture<Void> completeFuture = new CompletableFuture<>();
        try {
            final EmailNotificationConfiguration emailNotificationConfiguration =
                    mapper.readValue(notification.getConfiguration(), EmailNotificationConfiguration.class);

            final MailMessage mailMessage = new MailMessage()
                    .setFrom(emailNotificationConfiguration.getFrom())
                    .setTo(asList(notification.getDestination().split(",|;|\\s")));

            final StringWriter result = new StringWriter();
            final Template t = new Template("subject", new StringReader(getSubject(emailNotificationConfiguration,
                    parameters)), config);
            t.process(parameters, result);
            mailMessage.setSubject(result.toString());

            final Template template = config.getTemplate(getTemplateName(emailNotificationConfiguration, parameters));
            final String html = processTemplateIntoString(template, parameters);
            addContentInMessage(mailMessage, html);

            final MailConfig mailConfig = new MailConfig()
                    .setHostname(emailNotificationConfiguration.getHost())
                    .setPort(emailNotificationConfiguration.getPort())
                    .setUsername(emailNotificationConfiguration.getUsername())
                    .setPassword(emailNotificationConfiguration.getPassword())
                    .setTrustAll(emailNotificationConfiguration.isSslTrustAll());

            if (emailNotificationConfiguration.getSslKeyStore() != null) {
                mailConfig.setKeyStore(emailNotificationConfiguration.getSslKeyStore());
            }
            if (emailNotificationConfiguration.getSslKeyStorePassword() != null) {
                mailConfig.setKeyStorePassword(emailNotificationConfiguration.getSslKeyStorePassword());
            }
            if (emailNotificationConfiguration.isStartTLSEnabled()) {
                mailConfig.setStarttls(StartTLSOptions.REQUIRED);
            }

            createShared(Vertx.currentContext().owner(), mailConfig, valueOf(mailConfig.hashCode()))
                    .sendMail(mailMessage, e -> {
                        if (e.succeeded()) {
                            LOGGER.info("Email sent! " + e.result());
                            completeFuture.complete(null);
                        } else {
                            LOGGER.error("Email failed!", e.cause());
                            completeFuture.completeExceptionally(e.cause());
                        }
                    });
        } catch (final Exception ex) {
            LOGGER.error("Error while sending email notification", ex);
            completeFuture.completeExceptionally(ex);
        }
        return completeFuture;
    }

    private String getSubject(final EmailNotificationConfiguration emailNotificationConfiguration,
                              final Map<String, Object> parameters) {
        String subject = "";
        if (emailNotificationConfiguration.getSubject() == null) {
            final Object emailDefaultSubject = parameters.get("_email_default_subject");
            if (emailDefaultSubject != null) {
                subject = emailDefaultSubject.toString();
            }
        } else {
            subject = emailNotificationConfiguration.getSubject();
        }
        return subject;
    }

    private String getTemplateName(final EmailNotificationConfiguration emailNotificationConfiguration,
                                   final Map<String, Object> parameters) {
        String templateName = "";
        if (emailNotificationConfiguration.getTemplateName() == null) {
            final Object emailDefaultTemplateName = parameters.get("_email_default_template_name");
            if (emailDefaultTemplateName != null) {
                templateName = emailDefaultTemplateName.toString();
            }
        } else {
            templateName = emailNotificationConfiguration.getTemplateName();
        }
        return templateName;
    }

    private void addContentInMessage(final MailMessage mailMessage, final String htmlText) throws Exception {
        final Document document = Jsoup.parse(htmlText);
        final Elements imageElements = document.getElementsByTag("img");

        final List<String> resources = imageElements.stream()
                .filter(imageElement -> imageElement.hasAttr("src"))
                .map(imageElement -> {
                    final String src = imageElement.attr("src");
                    imageElement.attr("src", "cid:" + src);
                    return src;
                }).collect(toList());

        mailMessage.setHtml(document.html());

        if (!resources.isEmpty()) {
            final List<MailAttachment> mailAttachments = new ArrayList<>(resources.size());
            for (final String res : resources) {
                final MailAttachment attachment = new MailAttachment();
                attachment.setContentType(getContentTypeByFileName(res));
                attachment.setData(buffer(readAllBytes(new File(templatesPath, res).toPath())));
                attachment.setDisposition("inline");
                attachment.setContentId("<" + res + ">");
                mailAttachments.add(attachment);
            }
            mailMessage.setInlineAttachment(mailAttachments);
        }
    }

    private String getContentTypeByFileName(final String fileName) {
        if (fileName == null) {
            return "";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        }
        return MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(fileName);
    }
}
