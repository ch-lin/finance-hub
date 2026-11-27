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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.support.TransactionTemplate;

import ch.lin.finance.hub.backend.api.app.repository.accounting.category.TransactionCategoryRepository;
import ch.lin.finance.hub.backend.api.app.repository.accounting.insurance.InsurancePolicyRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyGroupRepository;
import ch.lin.finance.hub.backend.api.app.repository.family.FamilyMemberRepository;
import ch.lin.finance.hub.backend.api.app.repository.user.child.ChildProfileRepository;
import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.domain.model.accounting.category.TransactionCategory;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicy;
import ch.lin.finance.hub.backend.api.domain.model.accounting.insurance.InsurancePolicyType;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonItem;
import ch.lin.finance.hub.backend.api.domain.model.accounting.shopping.amazon.AmazonOrder;
import ch.lin.finance.hub.backend.api.domain.model.base.PrivacyLevel;
import ch.lin.finance.hub.backend.api.domain.model.profile.child.ChildProfile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:single_resource_isolation_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.enabled=true"
})
class SingleResourceIsolationIntegrationTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";
    private static final String USER_C = "user-c";
    private static final String TOKEN_A = "token-user-a";
    private static final String TOKEN_C = "token-user-c";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private FamilyService familyService;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private FamilyGroupRepository familyGroupRepository;

    @Autowired
    private InsurancePolicyRepository insurancePolicyRepository;

    @Autowired
    private TransactionCategoryRepository categoryRepository;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    private SecurityContext previousSecurityContext;

    private Long sharedPolicyId;
    private Long privatePolicyId;
    private Long sharedCategoryId;
    private Long privateCategoryId;
    private Long sharedChildId;
    private Long privateChildId;
    private Long sharedAmazonItemId;
    private Long privateAmazonItemId;

    @BeforeEach
    void setUp() {
        previousSecurityContext = SecurityContextHolder.getContext();
        cleanUp();

        when(jwtDecoder.decode(TOKEN_A)).thenReturn(createJwt(USER_A, TOKEN_A));
        when(jwtDecoder.decode(TOKEN_C)).thenReturn(createJwt(USER_C, TOKEN_C));

        FamilyGroupResult group = familyService.createFamilyGroup(USER_A, "Family AB");
        familyService.joinFamily(USER_B, group.getInviteCode());

        setAuthenticatedUser(USER_B);

        InsurancePolicy sharedPolicy = new InsurancePolicy(
                "SharedCo", InsurancePolicyType.LIFE, "P-SHARED", "Shared Policy", 12_000L,
                LocalDate.of(2035, 1, 1));
        sharedPolicy.setPrivacyLevel(PrivacyLevel.SHARED);
        sharedPolicyId = insurancePolicyRepository.save(sharedPolicy).getId();

        InsurancePolicy privatePolicy = new InsurancePolicy(
                "PrivateCo", InsurancePolicyType.LIFE, "P-PRIVATE", "Private Policy", 24_000L,
                LocalDate.of(2035, 1, 1));
        privatePolicy.setPrivacyLevel(PrivacyLevel.PRIVATE);
        privatePolicyId = insurancePolicyRepository.save(privatePolicy).getId();

        TransactionCategory sharedCategory = new TransactionCategory("SharedCat", true, false, null);
        sharedCategory.setPrivacyLevel(PrivacyLevel.SHARED);
        sharedCategoryId = categoryRepository.save(sharedCategory).getId();

        TransactionCategory privateCategory = new TransactionCategory("PrivateCat", true, false, null);
        privateCategory.setPrivacyLevel(PrivacyLevel.PRIVATE);
        privateCategoryId = categoryRepository.save(privateCategory).getId();

        ChildProfile sharedChild = ChildProfile.builder()
                .name("SharedChild")
                .birthDate(LocalDate.of(2020, 4, 1))
                .privacyLevel(PrivacyLevel.SHARED)
                .build();
        sharedChildId = childProfileRepository.save(Objects.requireNonNull(sharedChild)).getId();

        ChildProfile privateChild = ChildProfile.builder()
                .name("PrivateChild")
                .birthDate(LocalDate.of(2021, 4, 1))
                .privacyLevel(PrivacyLevel.PRIVATE)
                .build();
        privateChildId = childProfileRepository.save(Objects.requireNonNull(privateChild)).getId();

        transactionTemplate.executeWithoutResult(status -> {
            AmazonOrder sharedOrder = new AmazonOrder("ORD-SHARED-1", LocalDate.of(2026, 1, 10));
            sharedOrder.setPrivacyLevel(PrivacyLevel.SHARED);
            AmazonItem sharedItem = new AmazonItem("Shared Item");
            sharedOrder.addItem(sharedItem);
            entityManager.persist(sharedOrder);

            AmazonOrder privateOrder = new AmazonOrder("ORD-PRIVATE-1", LocalDate.of(2026, 1, 11));
            privateOrder.setPrivacyLevel(PrivacyLevel.PRIVATE);
            AmazonItem privateItem = new AmazonItem("Private Item");
            privateOrder.addItem(privateItem);
            entityManager.persist(privateOrder);

            entityManager.flush();
            sharedAmazonItemId = sharedItem.getId();
            privateAmazonItemId = privateItem.getId();
        });

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
        transactionTemplate.executeWithoutResult(status -> {
            entityManager.createQuery("DELETE FROM AmazonItem").executeUpdate();
            entityManager.createQuery("DELETE FROM AmazonOrder").executeUpdate();
        });
        childProfileRepository.deleteAll();
        categoryRepository.deleteAll();
        insurancePolicyRepository.deleteAll();
        familyMemberRepository.deleteAll();
        familyGroupRepository.deleteAll();
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

    @Test
    void nonFamilyUser_shouldReceive404ForAllSingleResourceEndpoints() throws Exception {
        String policyPayload = """
                {
                  "companyName": "TamperedCo",
                  "policyType": "LIFE",
                  "policyNumber": "P-TAMPER",
                  "policyName": "Tampered",
                  "annualPremium": 1000
                }
                """;
        String categoryPayload = """
                {
                  "name": "TamperedCat",
                  "isEssential": true,
                  "isSimulationDuplicate": false
                }
                """;

        mockMvc.perform(get("/insurance-policies/{id}", sharedPolicyId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/insurance-policies/{id}", sharedPolicyId)
                .header("Authorization", "Bearer " + TOKEN_C)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(policyPayload))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/insurance-policies/{id}", sharedPolicyId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/transaction-categories/{id}", sharedCategoryId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/transaction-categories/{id}", sharedCategoryId)
                .header("Authorization", "Bearer " + TOKEN_C)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(categoryPayload))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/transaction-categories/{id}", sharedCategoryId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/profile/children/{id}/projection", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/profile/children/{id}", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_C)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content("{\"name\":\"Tampered\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/profile/children/{id}", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/profile/children/{id}/education-settings", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/profile/children/{id}/education-settings", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_C)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/profile/children/{id}/education-settings", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/expenses/AMAZON_{id}/details", sharedAmazonItemId)
                .header("Authorization", "Bearer " + TOKEN_C))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("failure"));
    }

    @Test
    void familyUser_shouldReadSharedButBeRejectedForPrivateAndMutations() throws Exception {
        String policyPayload = """
                {
                  "companyName": "TamperedCo",
                  "policyType": "LIFE",
                  "policyNumber": "P-TAMPER",
                  "policyName": "Tampered",
                  "annualPremium": 1000
                }
                """;
        String categoryPayload = """
                {
                  "name": "TamperedCat",
                  "isEssential": true,
                  "isSimulationDuplicate": false
                }
                """;

        // Family member CAN read SHARED resources
        mockMvc.perform(get("/insurance-policies/{id}", sharedPolicyId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());
        mockMvc.perform(get("/transaction-categories/{id}", sharedCategoryId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());
        mockMvc.perform(get("/profile/children/{id}/projection", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());
        mockMvc.perform(get("/profile/children/{id}/education-settings", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());
        mockMvc.perform(get("/expenses/AMAZON_{id}/details", sharedAmazonItemId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isOk());

        // Family member CANNOT mutate SHARED resources (404)
        mockMvc.perform(put("/insurance-policies/{id}", sharedPolicyId)
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(policyPayload))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/insurance-policies/{id}", sharedPolicyId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/transaction-categories/{id}", sharedCategoryId)
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(categoryPayload))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/transaction-categories/{id}", sharedCategoryId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/profile/children/{id}", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content("{\"name\":\"Tampered\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/profile/children/{id}", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/profile/children/{id}/education-settings", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_A)
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/profile/children/{id}/education-settings", sharedChildId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());

        // Family member CANNOT read PRIVATE resources (404)
        mockMvc.perform(get("/insurance-policies/{id}", privatePolicyId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/transaction-categories/{id}", privateCategoryId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/profile/children/{id}/projection", privateChildId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/profile/children/{id}/education-settings", privateChildId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/expenses/AMAZON_{id}/details", privateAmazonItemId)
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isNotFound());
    }

    @Test
    void expenseDetails_shouldReturn400ForInvalidIdOrUnsupportedSource() throws Exception {
        mockMvc.perform(get("/expenses/AMAZON_abc/details")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/expenses/UNKNOWN_1/details")
                .header("Authorization", "Bearer " + TOKEN_A))
                .andExpect(status().isBadRequest());
    }
}
