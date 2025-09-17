package com.fxclub.gitlab.mentions.service;

import com.fxclub.gitlab.mentions.api.GitLabApiClient;
import com.fxclub.gitlab.mentions.model.GitLabUser;
import com.fxclub.gitlab.mentions.settings.GitLabSettingsState;
import com.intellij.openapi.components.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

@Service(Service.Level.APP)
@Slf4j
public final class GitLabUserService {
    private final GitLabApiClient apiClient = new GitLabApiClient();

    private volatile List<GitLabUser> groupMembers = Collections.emptyList();
    private volatile long groupMembersFetchedAt = 0L;

    /** Clears cached group members. */
    public void clearCache() { groupMembers = Collections.emptyList(); groupMembersFetchedAt = 0L; }

    private boolean shouldRefreshGroupMembers(GitLabSettingsState settings) {
        long now = Instant.now().getEpochSecond();
        return groupMembers.isEmpty() || (now - groupMembersFetchedAt) > settings.cacheTtlSeconds;
    }

    /** Loads group members (if group id configured) into cache respecting TTL. */
    public synchronized void ensureGroupMembersLoaded() {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        if (settings.id == null || settings.id.isBlank()) return; // no group configured
        if (!shouldRefreshGroupMembers(settings)) return;
        List<GitLabUser> fetched = apiClient.listAllGroupMembers(settings.id.trim());
        if (!fetched.isEmpty()) {
            groupMembers = fetched;
            groupMembersFetchedAt = Instant.now().getEpochSecond();
        }
    }

    /** Force reload group members ignoring TTL; returns count fetched. */
    public synchronized int forceReloadGroupMembers() {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        if (settings.id == null || settings.id.isBlank()) return 0;
        List<GitLabUser> fetched = apiClient.listAllGroupMembers(settings.id.trim());
        if (!fetched.isEmpty()) {
            groupMembers = fetched;
            groupMembersFetchedAt = Instant.now().getEpochSecond();
            return fetched.size();
        }
        return 0;
    }

    public List<GitLabUser> getGroupMembersSnapshot() { return groupMembers; }

    /** Filters cached group members by prefix (case-insensitive). */
    public List<GitLabUser> filterGroupMembers(String prefix) {
        if (groupMembers.isEmpty()) return Collections.emptyList();
        String lower = prefix.toLowerCase(Locale.ROOT);
        return groupMembers.stream()
                .filter(u -> u.getUsername() != null && u.getUsername().toLowerCase(Locale.ROOT).startsWith(lower))
                .limit(GitLabSettingsState.getInstance().maxUsersPerQuery)
                .toList();
    }

    @Override
    public String toString() { return "GitLabUserService"; }
}
