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
package ch.lin.finance.hub.backend.api.controller.security;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.repository.family.FamilyGroupRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyMemberRepository;
import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:multi_user_isolation_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.enabled=true"
})
public abstract class AbstractMultiUserIsolationIntegrationTest {

    protected static final String USER_A = "user-a";
    protected static final String USER_B = "user-b";
    protected static final String USER_C = "user-c";
    protected static final String TOKEN_A = "token-user-a";
    protected static final String TOKEN_B = "token-user-b";
    protected static final String TOKEN_C = "token-user-c";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private FamilyGroupRepository familyGroupRepository;

    private SecurityContext previousSecurityContext;

    @BeforeEach
    protected void setUpBase() {
        previousSecurityContext = SecurityContextHolder.getContext();
        cleanUpAll();

        when(jwtDecoder.decode(TOKEN_A)).thenReturn(createJwt(USER_A, TOKEN_A));
        when(jwtDecoder.decode(TOKEN_B)).thenReturn(createJwt(USER_B, TOKEN_B));
        when(jwtDecoder.decode(TOKEN_C)).thenReturn(createJwt(USER_C, TOKEN_C));

        FamilyGroupResult group = familyService.createFamilyGroup(USER_A, "Family AB");
        familyService.joinFamily(USER_B, group.getInviteCode());

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    protected void tearDownBase() {
        cleanUpAll();
        if (previousSecurityContext != null) {
            SecurityContextHolder.setContext(previousSecurityContext);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    private void cleanUpAll() {
        cleanUpModuleData();
        familyMemberRepository.deleteAll();
        familyGroupRepository.deleteAll();
    }

    protected abstract void cleanUpModuleData();

    protected Jwt createJwt(String userId, String tokenValue) {
        return new Jwt(
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", userId));
    }

    protected void setAuthenticatedUser(String userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(createJwt(userId, "seed-" + userId), List.of()));
        SecurityContextHolder.setContext(context);
    }
}
