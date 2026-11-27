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
package ch.lin.finance.hub.backend.api.controller.simulation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.lin.finance.hub.backend.api.app.repository.accounting.dc.DefinedContributionUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.mortgage.MortgageRecordRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.nisa.NisaUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.salary.SalaryUserSettingsRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyGroupRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyMemberRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.UserProfileRepository;
import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.dc.DefinedContributionUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.InterestRateType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.MortgageRecord;
import ch.lin.finance.hub.backend.api.domain.model.accounting.mortgage.RepaymentMethod;
import ch.lin.finance.hub.backend.api.domain.model.accounting.nisa.NisaUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.accounting.salary.SalaryUserSettings;
import ch.lin.finance.hub.backend.api.domain.model.profile.user.UserProfile;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:simulation_isolation_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.enabled=true",
        "finance.hub.system-config.pension-expected-monthly-salary=500000"
})
class AssetSimulationFamilyIsolationIntegrationTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";
    private static final String TOKEN_A = "token-user-a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private FamilyGroupRepository familyGroupRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private MortgageRecordRepository mortgageRecordRepository;

    @Autowired
    private NisaUserSettingsRepository nisaUserSettingsRepository;

    @Autowired
    private DefinedContributionUserSettingsRepository dcUserSettingsRepository;

    @Autowired
    private SalaryUserSettingsRepository salaryUserSettingsRepository;

    private SecurityContext previousSecurityContext;

    @BeforeEach
    void setUp() {
        previousSecurityContext = SecurityContextHolder.getContext();
        cleanUp();
        Jwt jwtA = createJwt(USER_A, TOKEN_A);
        when(jwtDecoder.decode(TOKEN_A)).thenReturn(jwtA);

        // Create a shared family group containing both User A and User B
        FamilyGroupResult group = familyService.createFamilyGroup(USER_A, "Test Family");
        familyService.joinFamily(USER_B, group.getInviteCode());

        // Seed User A's profile & assets
        seedUserData(USER_A, 10_000_000L, 50_000L, 10_000L, 1_000_000L);

        // Seed User B's profile & much larger assets (must NOT leak into User A's
        // simulation)
        seedUserData(USER_B, 80_000_000L, 200_000L, 50_000L, 9_000_000L);

        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        if (previousSecurityContext != null) {
            SecurityContextHolder.setContext(previousSecurityContext);
        } else {
            SecurityContextHolder.clearContext();
        }
    }

    private void cleanUp() {
        mortgageRecordRepository.deleteAll();
        nisaUserSettingsRepository.deleteAll();
        dcUserSettingsRepository.deleteAll();
        salaryUserSettingsRepository.deleteAll();
        familyMemberRepository.deleteAll();
        familyGroupRepository.deleteAll();
        userProfileRepository.deleteAll();
    }

    private Jwt createJwt(String userId, String tokenValue) {
        return new Jwt(
                tokenValue,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("sub", userId));
    }

    private void setAuthenticatedUser(String userId) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(createJwt(userId, "seed-" + userId), List.of()));
        SecurityContextHolder.setContext(context);
    }

    private void seedUserData(String userId, long mortgageBalance, long nisaMonthlyGrowth,
            long idecoMonthly, long expectedBonus) {
        setAuthenticatedUser(userId);

        UserProfile profile = new UserProfile(userId, LocalDate.now().minusYears(30), 150_000L, 65);
        userProfileRepository.save(profile);

        MortgageRecord mortgage = new MortgageRecord(
                "TestBank-" + userId,
                "C-" + userId,
                "HomeLoan-" + userId,
                mortgageBalance,
                mortgageBalance,
                LocalDate.now().minusYears(1),
                LocalDate.now().plusYears(34),
                LocalDate.now(),
                420,
                0.01,
                InterestRateType.FIXED,
                RepaymentMethod.EQUAL_PAYMENT,
                mortgageBalance / 400,
                List.of(),
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                true);
        mortgageRecordRepository.save(mortgage);

        NisaUserSettings nisaSettings = new NisaUserSettings();
        nisaSettings.setMonthlyGrowthContribution(nisaMonthlyGrowth);
        nisaSettings.setMonthlyTsumitateContribution(0L);
        nisaSettings.setNisaExpectedReturnRate(0.0);
        nisaUserSettingsRepository.save(nisaSettings);

        DefinedContributionUserSettings dcSettings = new DefinedContributionUserSettings();
        dcSettings.setMonthlyIdecoContribution(idecoMonthly);
        dcSettings.setIdecoExpectedReturnRate(0.0);
        dcSettings.setMonthlyDcMatchingContribution(0L);
        dcUserSettingsRepository.save(dcSettings);

        SalaryUserSettings salarySettings = new SalaryUserSettings();
        salarySettings.setUseHistoricalBonusForProjection(false);
        salarySettings.setExpectedAnnualBonus(expectedBonus);
        salaryUserSettingsRepository.save(salarySettings);
    }

    @Test
    void simulationWithFamilyViewScope_shouldIsolateUserADataFromFamilyMemberUserB() throws Exception {
        MvcResult personalResult = mockMvc.perform(post("/simulation/asset-simulation/32")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult familyScopeResult = mockMvc.perform(post("/simulation/asset-simulation/32")
                .header("Authorization", "Bearer " + TOKEN_A)
                .header("X-View-Scope", "FAMILY"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode personalData = objectMapper.readTree(personalResult.getResponse().getContentAsString()).get("data");
        JsonNode familyData = objectMapper.readTree(familyScopeResult.getResponse().getContentAsString()).get("data");

        assertThat(personalData.isArray()).isTrue();
        assertThat(familyData).isEqualTo(personalData);

        // Verify Year 1 (full projection year) values reflect ONLY User A's settings,
        // not User B's
        JsonNode year1 = familyData.get(1);
        assertThat(year1.get("projectedAnnualSalary").asLong()).isEqualTo(500_000L * 12);
        assertThat(year1.get("netIncome").asLong()).isGreaterThan(4_500_000L).isLessThanOrEqualTo(7_000_000L);
        assertThat(year1.get("remainingMortgageBalance").asLong()).isLessThan(10_000_000L);
        assertThat(year1.get("projectedNisaBalance").asLong()).isGreaterThan(0L)
                .isLessThanOrEqualTo(50_000L * 12 * 2);
        assertThat(year1.get("projectedDcBalance").asLong()).isGreaterThan(0L)
                .isLessThanOrEqualTo(10_000L * 12 * 2);
    }
}
