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
package ch.lin.finance.hub.backend.api.controller.family;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.controller.family.dto.CreateFamilyRequest;
import ch.lin.finance.hub.backend.api.controller.family.dto.FamilyMapper;
import ch.lin.finance.hub.backend.api.controller.family.dto.JoinFamilyRequest;

@ExtendWith(MockitoExtension.class)
class FamilyControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FamilyService familyService;

    @Spy
    @SuppressWarnings("unused")
    private final FamilyMapper familyMapper = org.mapstruct.factory.Mappers.getMapper(FamilyMapper.class);

    @InjectMocks
    private FamilyController familyController;

    private MockedStatic<ch.lin.finance.hub.backend.api.app.util.SecurityUtil> securityUtilMock;
    private FamilyGroupResult groupResult;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(familyController).build();

        groupResult = new FamilyGroupResult();
        groupResult.setId(100L);
        groupResult.setName("My Family");
        groupResult.setInviteCode("ABCDEF");
        groupResult.setInviteCodeExpiresAt(LocalDateTime.now().plusMinutes(15));

        securityUtilMock = mockStatic(ch.lin.finance.hub.backend.api.app.util.SecurityUtil.class);
    }

    @AfterEach
    @SuppressWarnings("unused")
    void tearDown() {
        securityUtilMock.close();
    }

    @Test
    void createFamilyGroup_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");
        CreateFamilyRequest request = new CreateFamilyRequest();
        request.setName("My Family");

        when(familyService.createFamilyGroup(eq("user1"), eq("My Family"))).thenReturn(groupResult);

        mockMvc.perform(post("/family")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON_VALUE))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("My Family"))
                .andExpect(jsonPath("$.inviteCode").value("ABCDEF"));

        verify(familyService).createFamilyGroup("user1", "My Family");
    }

    @Test
    void refreshInviteCode_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");
        when(familyService.refreshInviteCode(eq("user1"))).thenReturn(groupResult);

        mockMvc.perform(post("/family/invite/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inviteCode").value("ABCDEF"));

        verify(familyService).refreshInviteCode("user1");
    }

    @Test
    void joinFamily_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user2");
        JoinFamilyRequest request = new JoinFamilyRequest();
        request.setInviteCode("ABCDEF");

        mockMvc.perform(post("/family/join")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        verify(familyService).joinFamily("user2", "ABCDEF");
    }

    @Test
    void removeMember_me_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");
        mockMvc.perform(delete("/family/members/me"))
                .andExpect(status().isOk());

        verify(familyService).removeMember("user1", "user1");
    }

    @Test
    void removeMember_other_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("admin1");
        mockMvc.perform(delete("/family/members/user2"))
                .andExpect(status().isOk());

        verify(familyService).removeMember("admin1", "user2");
    }

    @Test
    void getFamilyGroup_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");
        when(familyService.getFamilyGroup(eq("user1"))).thenReturn(Optional.of(groupResult));

        mockMvc.perform(get("/family"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.name").value("My Family"))
                .andExpect(jsonPath("$.inviteCode").value("ABCDEF"));

        verify(familyService).getFamilyGroup("user1");
    }

    @Test
    void getFamilyGroup_notFound() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");
        when(familyService.getFamilyGroup(eq("user1"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/family"))
                .andExpect(status().isNotFound());

        verify(familyService).getFamilyGroup("user1");
    }

    @Test
    void getFamilyMembers_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");
        ch.lin.finance.hub.backend.api.app.service.family.result.FamilyMemberResult memberResult
                = ch.lin.finance.hub.backend.api.app.service.family.result.FamilyMemberResult.builder()
                        .userId("user1")
                        .displayName("Alice")
                        .role(ch.lin.finance.hub.backend.api.domain.model.family.FamilyRole.ADMIN)
                        .joinedAt(LocalDateTime.now())
                        .build();

        when(familyService.getFamilyMembers(eq("user1"))).thenReturn(java.util.List.of(memberResult));

        mockMvc.perform(get("/family/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value("user1"))
                .andExpect(jsonPath("$[0].displayName").value("Alice"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"));

        verify(familyService).getFamilyMembers("user1");
    }

    @Test
    void disbandFamily_success() throws Exception {
        securityUtilMock.when(ch.lin.finance.hub.backend.api.app.util.SecurityUtil::getCurrentUserId)
                .thenReturn("user1");

        mockMvc.perform(delete("/family"))
                .andExpect(status().isOk());

        verify(familyService).disbandFamily("user1");
    }
}
