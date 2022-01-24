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

import static org.mockito.Mockito.when;

import com.icegreen.greenmail.util.ServerSetupTest;
import io.gravitee.notifier.api.Notification;
import io.gravitee.notifier.email.configuration.EmailNotifierConfiguration;
import io.vertx.ext.mail.MailMessage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class EmailNotifierParametersTest {

    private EmailNotifier emailNotifier;

    @Mock
    private Notification notification;

    @Mock
    private EmailNotifierConfiguration emailNotifierConfiguration;

    private final Map<String, Object> parameters = new HashMap<>();

    @BeforeEach
    public void init() throws IOException {
        emailNotifier = new EmailNotifier(emailNotifierConfiguration);
        emailNotifier.setTemplatesPath(this.getClass().getResource("/io/gravitee/notifier/email/templates").getPath());
        emailNotifier.afterPropertiesSet();
    }

    @Test
    public void shouldSendEmailToSingleRecipient() throws Exception {
        when(emailNotifierConfiguration.getFrom()).thenReturn("from@mail.com");
        when(emailNotifierConfiguration.getTo()).thenReturn("${(entity.metadata['emails'])}");
        when(emailNotifierConfiguration.getSubject()).thenReturn("subject of email");
        when(emailNotifierConfiguration.getBody()).thenReturn("template_sample.html");

        Entity entity = new Entity();
        entity.getMetadata().put("emails", "john.doe@gmail.com,jane.doe@gmail.com");

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("entity", entity);

        MailMessage mailMessage = emailNotifier.prepareMailMessage(parameters);

        Assertions.assertEquals(2, mailMessage.getTo().size());
    }

    public class Entity {

        private final Map<String, Object> metadata = new HashMap<>();

        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
}
