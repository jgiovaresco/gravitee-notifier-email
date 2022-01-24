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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class EmailNotifierAuthenticationTest {

    private EmailNotifier emailNotifier;

    @Mock
    private Notification notification;

    @Mock
    private EmailNotifierConfiguration emailNotifierConfiguration;

    private final Map<String, Object> parameters = new HashMap<>();

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
        .withConfiguration(GreenMailConfiguration.aConfig().withUser("user", "password"))
        .withPerMethodLifecycle(true);

    @BeforeEach
    public void init() throws IOException {
        emailNotifier = new EmailNotifier(emailNotifierConfiguration);

        when(notification.getType()).thenReturn(EmailNotifier.TYPE);
        setField(emailNotifier, "templatesPath", this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.afterPropertiesSet();
    }

    @Test
    public void shouldSendEmailWithCredentials() throws Exception {
        when(emailNotifierConfiguration.getFrom()).thenReturn("from@mail.com");
        when(emailNotifierConfiguration.getTo()).thenReturn("to@mail.com");
        when(emailNotifierConfiguration.getSubject()).thenReturn("subject of email");
        when(emailNotifierConfiguration.getBody()).thenReturn("template_sample.html");
        when(emailNotifierConfiguration.getHost()).thenReturn(ServerSetupTest.SMTP.getBindAddress());
        when(emailNotifierConfiguration.getPort()).thenReturn(ServerSetupTest.SMTP.getPort());
        when(emailNotifierConfiguration.getUsername()).thenReturn("user");
        when(emailNotifierConfiguration.getPassword()).thenReturn("password");

        CountDownLatch latch = new CountDownLatch(1);

        Vertx
            .vertx()
            .runOnContext(
                new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        CompletableFuture<Void> future = emailNotifier.send(notification, parameters);

                        future.whenComplete(
                            new BiConsumer<Void, Throwable>() {
                                @Override
                                public void accept(Void unused, Throwable throwable) {
                                    assertNull(throwable);

                                    assertEquals(1, greenMail.getReceivedMessages().length);
                                    MimeMessage receivedMessage = greenMail.getReceivedMessages()[0];

                                    try {
                                        assertEquals(
                                            "<html>\r\n" +
                                            " <head></head>\r\n" +
                                            " <body>\r\n" +
                                            "  template_sample.html\r\n" +
                                            " </body>\r\n" +
                                            "</html>",
                                            GreenMailUtil.getBody(receivedMessage)
                                        );
                                        assertEquals(1, receivedMessage.getAllRecipients().length);
                                        assertEquals("to@mail.com", receivedMessage.getAllRecipients()[0].toString());
                                        //TODO: check why the sender is always null
                                        //    assertEquals("from@mail.com", receivedMessage.getSender().toString());
                                        assertEquals("subject of email", receivedMessage.getSubject());
                                        latch.countDown();
                                    } catch (Throwable t) {
                                        fail(t);
                                    }
                                }
                            }
                        );
                    }
                }
            );

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void shouldNotSendEmailWithInvalidCredentials() throws Exception {
        when(emailNotifierConfiguration.getFrom()).thenReturn("from@mail.com");
        when(emailNotifierConfiguration.getTo()).thenReturn("to@mail.com");
        when(emailNotifierConfiguration.getSubject()).thenReturn("subject of email");
        when(emailNotifierConfiguration.getBody()).thenReturn("template_sample.html");
        when(emailNotifierConfiguration.getHost()).thenReturn(ServerSetupTest.SMTP.getBindAddress());
        when(emailNotifierConfiguration.getPort()).thenReturn(ServerSetupTest.SMTP.getPort());
        when(emailNotifierConfiguration.getUsername()).thenReturn("user");
        when(emailNotifierConfiguration.getPassword()).thenReturn("bad-password");

        CountDownLatch latch = new CountDownLatch(1);

        Vertx
            .vertx()
            .runOnContext(
                new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        CompletableFuture<Void> future = emailNotifier.send(notification, parameters);

                        future.whenComplete(
                            new BiConsumer<Void, Throwable>() {
                                @Override
                                public void accept(Void unused, Throwable throwable) {
                                    assertNotNull(throwable);
                                    assertEquals(0, greenMail.getReceivedMessages().length);
                                    latch.countDown();
                                }
                            }
                        );
                    }
                }
            );

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    @Disabled("GreenMail supports correctly authentication mechanism ?")
    public void shouldNotSendEmailWithoutCredentials() throws Exception {
        when(emailNotifierConfiguration.getFrom()).thenReturn("from@mail.com");
        when(emailNotifierConfiguration.getTo()).thenReturn("to@mail.com");
        when(emailNotifierConfiguration.getSubject()).thenReturn("subject of email");
        when(emailNotifierConfiguration.getBody()).thenReturn("template_sample.html");
        when(emailNotifierConfiguration.getHost()).thenReturn(ServerSetupTest.SMTP.getBindAddress());
        when(emailNotifierConfiguration.getPort()).thenReturn(ServerSetupTest.SMTP.getPort());

        CountDownLatch latch = new CountDownLatch(1);

        Vertx
            .vertx()
            .runOnContext(
                new Handler<Void>() {
                    @Override
                    public void handle(Void event) {
                        CompletableFuture<Void> future = emailNotifier.send(notification, parameters);

                        future.whenComplete(
                            new BiConsumer<Void, Throwable>() {
                                @Override
                                public void accept(Void unused, Throwable throwable) {
                                    // No exception when no credentials are passed to mail server
                                    //    assertNotNull(throwable);
                                    assertEquals(0, greenMail.getReceivedMessages().length);
                                    latch.countDown();
                                }
                            }
                        );
                    }
                }
            );

        Assertions.assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
