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

import freemarker.cache.FileTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import io.gravitee.notifier.api.AbstractConfigurableNotifier;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static io.vertx.core.buffer.Buffer.buffer;
import static io.vertx.ext.mail.MailClient.createShared;
import static java.lang.String.valueOf;
import static java.nio.file.Files.readAllBytes;
import static java.util.stream.Collectors.toList;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author Azize ELAMRANI (azize.elamrani at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EmailNotifier extends AbstractConfigurableNotifier<EmailNotifierConfiguration> implements InitializingBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotifier.class);

    private static final String TYPE = "email-notifier";

    @Value("${notifiers.email.templates.path:${gravitee.home}/templates}")
    private String templatesPath;

    private Configuration config = new Configuration(Configuration.VERSION_2_3_28);

    public EmailNotifier(EmailNotifierConfiguration configuration) {
        super(TYPE, configuration);
    }

    public void afterPropertiesSet() throws IOException {
        config.setNewBuiltinClassResolver(TemplateClassResolver.SAFER_RESOLVER);
        config.setTemplateLoader(new FileTemplateLoader(new File(URLDecoder.decode(templatesPath, "UTF-8"))));
    }

    @Override
    public CompletableFuture<Void> doSend(final Notification notification, final Map<String, Object> parameters) {
        final CompletableFuture<Void> completeFuture = new CompletableFuture<>();
        try {
            final MailMessage mailMessage = new MailMessage()
                    .setFrom(configuration.getFrom())
                    .setTo(Arrays.asList(configuration.getTo().split(",|;|\\s")));

            mailMessage.setSubject(templatize(configuration.getSubject(), parameters));
            addContentInMessage(mailMessage, templatize(configuration.getBody(), parameters));

            final MailConfig mailConfig = new MailConfig()
                    .setHostname(configuration.getHost())
                    .setPort(configuration.getPort())
                    .setUsername(configuration.getUsername())
                    .setPassword(configuration.getPassword())
                    .setTrustAll(configuration.isSslTrustAll());

            if (configuration.getSslKeyStore() != null) {
                mailConfig.setKeyStore(configuration.getSslKeyStore());
            }
            if (configuration.getSslKeyStorePassword() != null) {
                mailConfig.setKeyStorePassword(configuration.getSslKeyStorePassword());
            }
            if (configuration.isStartTLSEnabled()) {
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

    private void addContentInMessage(final MailMessage mailMessage, final String htmlText) throws Exception {
        final Document document = Jsoup.parse(htmlText);
        final Elements imageElements = document.getElementsByTag("img");

        final List<String> resources = imageElements.stream()
                .filter(imageElement -> imageElement.hasAttr("src"))
                .filter(imageElement -> !imageElement.attr("src").startsWith("http"))
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
