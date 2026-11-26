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
package ch.lin.finance.hub.backend.api.controller.accounting.pension;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import ch.lin.finance.hub.backend.api.app.service.accounting.pension.PensionProjectionService;
import ch.lin.finance.hub.backend.api.app.service.accounting.pension.result.PensionProjectionYearlyResult;
import ch.lin.finance.hub.backend.api.controller.accounting.pension.dto.PensionProjectionYearlyResponse;
import ch.lin.finance.hub.backend.api.controller.accounting.pension.dto.PensionRecordMapper;

@ExtendWith(MockitoExtension.class)
class PensionRecordControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PensionProjectionService pensionProjectionService;

    @Mock
    private PensionRecordMapper pensionRecordMapper;

    @InjectMocks
    private PensionRecordController controller;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        HandlerMethodArgumentResolver jwtArgumentResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(@org.springframework.lang.NonNull MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && parameter.getParameterType().equals(Jwt.class);
            }

            @Override
            public Object resolveArgument(@org.springframework.lang.NonNull MethodParameter parameter,
                    @org.springframework.lang.Nullable ModelAndViewContainer mavContainer,
                    @org.springframework.lang.NonNull NativeWebRequest webRequest,
                    @org.springframework.lang.Nullable WebDataBinderFactory binderFactory) {
                return Jwt.withTokenValue("token").header("alg", "none").subject("user-1").build();
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtArgumentResolver)
                .build();

        org.springframework.security.oauth2.jwt.Jwt jwt = org.springframework.security.oauth2.jwt.Jwt
                .withTokenValue("token")
                .header("alg", "none")
                .claim("sub", "user-1")
                .build();
        org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwtAuth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(
                jwt);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(jwtAuth);
    }

    @Test
    void getProjection_shouldReturnProjections() throws Exception {
        PensionProjectionYearlyResult result = new PensionProjectionYearlyResult(0, 795000, 1200000, 1995000,
                39900000L);
        List<PensionProjectionYearlyResult> results = List.of(result);

        PensionProjectionYearlyResponse response = new PensionProjectionYearlyResponse(0, 795000L, 1200000L, 1995000L,
                39900000L);

        when(pensionProjectionService.calculateProjection(eq("user-1"), eq(40), eq(null)))
                .thenReturn(results);
        when(pensionRecordMapper.toMaskedResponse(eq(result), org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(get("/assets/pensions/projection").param("maxYears", "40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].yearsAdditionalWorked").value(0))
                .andExpect(jsonPath("$[0].projectedBasicPensionAnnual").value(795000));
    }
}
