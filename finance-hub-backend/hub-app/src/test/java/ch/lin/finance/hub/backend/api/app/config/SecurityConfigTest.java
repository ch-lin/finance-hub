/*
 * Copyright (c) 2026 Che-Hung Lin. All rights reserved.
 *
 * This software and associated documentation files (the "Software") are the
 * proprietary and confidential property of Che-Hung Lin. Unauthorized copying,
 * modification, distribution, reverse engineering, or use of the Software,
 * via any medium, is strictly prohibited without prior written permission.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ch.lin.finance.hub.backend.api.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

class SecurityConfigTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withBean("mvcHandlerMappingIntrospector", HandlerMappingIntrospector.class,
                    () -> Mockito.mock(HandlerMappingIntrospector.class))
            .withBean(JwtDecoder.class, () -> Mockito.mock(JwtDecoder.class));

    @Test
    void testSecurityConfigEnabled() {
        contextRunner.withPropertyValues("app.security.enabled=true", "app.security.swagger-public=true")
                .run(context -> {
                    assertThat(context).hasBean("enabledSecurityFilterChain");
                    assertThat(context).doesNotHaveBean("disabledSecurityFilterChain");
                });
    }

    @Test
    void testSecurityConfigDisabled() {
        contextRunner.withPropertyValues("app.security.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("disabledSecurityFilterChain");
                    assertThat(context).doesNotHaveBean("enabledSecurityFilterChain");
                });
    }

    @Test
    void testSecurityConfigEnabledWithSwaggerNotPublic() {
        contextRunner.withPropertyValues("app.security.enabled=true", "app.security.swagger-public=false")
                .run(context -> {
                    assertThat(context).hasBean("enabledSecurityFilterChain");
                    assertThat(context).doesNotHaveBean("disabledSecurityFilterChain");
                });
    }
}
