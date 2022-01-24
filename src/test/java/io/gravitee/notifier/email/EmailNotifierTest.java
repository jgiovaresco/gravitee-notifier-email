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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@Ignore
@RunWith(PowerMockRunner.class)
@PrepareForTest({ MailClient.class, Vertx.class })
@PowerMockIgnore({ "com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*" })
public class EmailNotifierTest {

    @Mock
    private ObjectMapper mapper;

    @Mock
    private Notification notification;

    @Mock
    private EmailNotifierConfiguration emailNotifierConfiguration;

    @Mock
    private MailClient mailClient;

    @Mock
    private Context context;

    private EmailNotifier emailNotifier;

    private final Map<String, Object> parameters = new HashMap<>();

    @Before
    public void init() throws IOException {
        initMocks(this);

        emailNotifier = new EmailNotifier(emailNotifierConfiguration);

        setField(emailNotifier, "templatesPath", this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.afterPropertiesSet();

        mockStatic(MailClient.class);
        when(MailClient.createShared(any(), any(), any())).thenReturn(mailClient);
        mockStatic(Vertx.class);
        when(Vertx.currentContext()).thenReturn(context);
    }

    @Test
    public void shouldSend() throws Exception {
        when(notification.getType()).thenReturn("email");
        when(mapper.readValue(nullable(String.class), eq(EmailNotifierConfiguration.class))).thenReturn(emailNotifierConfiguration);
        when(emailNotifierConfiguration.getFrom()).thenReturn("from@mail.com");
        when(emailNotifierConfiguration.getTo()).thenReturn("to@mail.com");
        when(emailNotifierConfiguration.getSubject()).thenReturn("subject of email");
        when(emailNotifierConfiguration.getBody()).thenReturn("template_sample.html");
        when(emailNotifierConfiguration.getHost()).thenReturn("smtp.host.fr");
        when(emailNotifierConfiguration.getPort()).thenReturn(587);
        when(emailNotifierConfiguration.getUsername()).thenReturn("user");
        when(emailNotifierConfiguration.getPassword()).thenReturn("password");

        emailNotifier.send(notification, parameters);

        final MailMessage mailMessage = new MailMessage();
        mailMessage.setFrom("from@mail.com");
        mailMessage.setTo("to@mail.com");
        mailMessage.setSubject("subject of email");
        mailMessage.setHtml(
            "<html>\n" + " <head></head>\n" + " <body>\n" + "  <div>\n" + "   test\n" + "  </div>\n" + " </body>\n" + "</html>"
        );
        verify(mailClient).sendMail(eq(mailMessage), any());
    }

    @Test
    public void shouldNotSend() {
        when(notification.getType()).thenReturn("unknown");
        emailNotifier.send(notification, parameters);
    }
}
