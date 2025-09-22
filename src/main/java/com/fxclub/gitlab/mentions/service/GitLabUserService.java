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

    /** Loads group members or all active users into cache respecting TTL (synchronous). */
    public synchronized void ensureGroupMembersLoaded() {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        if (!shouldRefreshGroupMembers(settings)) return;
        try {
            List<GitLabUser> fetched = apiClient.listAllMembersOrActiveUsers();
            if (!fetched.isEmpty()) {
                groupMembers = fetched;
                groupMembersFetchedAt = Instant.now().getEpochSecond();
                log.info("Loaded {} users into cache", fetched.size());
            } else {
                log.warn("Synchronous load returned 0 users");
            }
        } catch (Exception ex) {
            log.warn("Synchronous load failed", ex);
        }
    }

    /** Force reload group members or all active users ignoring TTL; returns count fetched. */
    public synchronized int forceReloadMembers() {
        List<GitLabUser> fetched = apiClient.listAllMembersOrActiveUsers();
        if (!fetched.isEmpty()) {
            groupMembers = fetched;
            groupMembersFetchedAt = Instant.now().getEpochSecond();
            return fetched.size();
        }
        return 0;
    }

    public List<GitLabUser> getGroupMembersSnapshot() { return groupMembers; }

    /** Filters cached group members by substring (case-insensitive) against username or name. */
    public List<GitLabUser> filterGroupMembers(String query) {
        if (groupMembers.isEmpty()) return Collections.emptyList();
        String q = query.toLowerCase(Locale.ROOT);
        int limit = GitLabSettingsState.getInstance().maxUsersPerQuery;
        return groupMembers.stream()
                .filter(u -> {
                    String uname = u.getUsername();
                    String name = u.getName();
                    return uname.toLowerCase(Locale.ROOT).contains(q) || name != null && name.toLowerCase(Locale.ROOT).contains(q);
                })
                .limit(limit)
                .toList();
    }

    @Override
    public String toString() { return "GitLabUserService"; }
}
