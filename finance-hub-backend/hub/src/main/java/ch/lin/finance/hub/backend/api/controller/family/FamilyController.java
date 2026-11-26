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

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.lin.finance.hub.backend.api.app.service.family.FamilyService;
import ch.lin.finance.hub.backend.api.app.service.family.result.FamilyGroupResult;
import ch.lin.finance.hub.backend.api.app.util.SecurityUtil;
import ch.lin.finance.hub.backend.api.controller.family.dto.CreateFamilyRequest;
import ch.lin.finance.hub.backend.api.controller.family.dto.FamilyGroupResponse;
import ch.lin.finance.hub.backend.api.controller.family.dto.FamilyMapper;
import ch.lin.finance.hub.backend.api.controller.family.dto.FamilyMemberResponse;
import ch.lin.finance.hub.backend.api.controller.family.dto.JoinFamilyRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final FamilyMapper familyMapper;

    @GetMapping
    public ResponseEntity<FamilyGroupResponse> getFamilyGroup() {
        String currentUserId = SecurityUtil.getCurrentUserId();
        return familyService.getFamilyGroup(currentUserId)
                .map(familyMapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FamilyGroupResponse> createFamilyGroup(@Valid @RequestBody CreateFamilyRequest request) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        FamilyGroupResult group = familyService.createFamilyGroup(currentUserId, request.getName());
        return ResponseEntity.ok(familyMapper.toResponse(group));
    }

    @PostMapping("/invite/refresh")
    public ResponseEntity<FamilyGroupResponse> refreshInviteCode() {
        String currentUserId = SecurityUtil.getCurrentUserId();
        FamilyGroupResult group = familyService.refreshInviteCode(currentUserId);
        return ResponseEntity.ok(familyMapper.toResponse(group));
    }

    @PostMapping("/join")
    public ResponseEntity<Void> joinFamily(@Valid @RequestBody JoinFamilyRequest request) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        familyService.joinFamily(currentUserId, request.getInviteCode());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/members/{targetUserId}")
    public ResponseEntity<Void> removeMember(@PathVariable String targetUserId) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        String actualTargetId = "me".equalsIgnoreCase(targetUserId) ? currentUserId : targetUserId;
        familyService.removeMember(currentUserId, actualTargetId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> disbandFamily() {
        String currentUserId = SecurityUtil.getCurrentUserId();
        familyService.disbandFamily(currentUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/members")
    public ResponseEntity<List<FamilyMemberResponse>> getFamilyMembers() {
        String currentUserId = SecurityUtil.getCurrentUserId();
        List<FamilyMemberResponse> members = familyService.getFamilyMembers(currentUserId).stream()
                .map(familyMapper::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(members);
    }
}
