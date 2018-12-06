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
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotificationConfiguration;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@RunWith(PowerMockRunner.class)
@PrepareForTest({MailClient.class, Vertx.class})
public class EmailNotifierTest {

    @InjectMocks
    private EmailNotifier emailNotifier = new EmailNotifier();

    @Mock
    private ObjectMapper mapper;
    @Mock
    private Notification notification;
    @Mock
    private EmailNotificationConfiguration emailNotificationConfiguration;
    @Mock
    private MailClient mailClient;
    @Mock
    private Context context;

    private final Map<String, Object> parameters = new HashMap<>();

    @Before
    public void init() throws IOException {
        initMocks(this);
        setField(emailNotifier, "templatesPath", this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.init();

        mockStatic(MailClient.class);
        when(MailClient.createShared(any(), any(), any())).thenReturn(mailClient);
        mockStatic(Vertx.class);
        when(Vertx.currentContext()).thenReturn(context);
    }

    @Test
    public void shouldSend() throws Exception {
        when(notification.getType()).thenReturn("email");
        when(notification.getDestination()).thenReturn("to@mail.com");
        when(mapper.readValue(anyString(), eq(EmailNotificationConfiguration.class)))
                .thenReturn(emailNotificationConfiguration);
        when(emailNotificationConfiguration.getFrom()).thenReturn("from@mail.com");
        when(emailNotificationConfiguration.getSubject()).thenReturn("subject of email");
        when(emailNotificationConfiguration.getTemplateName()).thenReturn("template_sample.html");
        when(emailNotificationConfiguration.getHost()).thenReturn("smtp.host.fr");
        when(emailNotificationConfiguration.getPort()).thenReturn(587);
        when(emailNotificationConfiguration.getUsername()).thenReturn("user");
        when(emailNotificationConfiguration.getPassword()).thenReturn("password");

        emailNotifier.send(notification, parameters);

        final MailMessage mailMessage = new MailMessage();
        mailMessage.setFrom("from@mail.com");
        mailMessage.setTo("to@mail.com");
        mailMessage.setSubject("subject of email");
        mailMessage.setHtml(
                "<html>\n" +
                " <head></head>\n" +
                " <body>\n" +
                "  <div>\n" +
                "   test\n" +
                "  </div>\n" +
                " </body>\n" +
                "</html>"
        );
        verify(mailClient).sendMail(eq(mailMessage), any());
    }

    @Test
    public void shouldNotSend() {
        when(notification.getType()).thenReturn("unknown");
        emailNotifier.send(notification, parameters);
    }
}
