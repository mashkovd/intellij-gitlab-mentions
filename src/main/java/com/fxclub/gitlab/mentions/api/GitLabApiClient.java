package com.fxclub.gitlab.mentions.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fxclub.gitlab.mentions.model.GitLabUser;
import com.fxclub.gitlab.mentions.settings.GitLabSettingsState;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class GitLabApiClient {
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private static volatile long lastInvalidTokenNotifiedAtSec = 0L;

    public GitLabApiClient() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private void notifyInvalidTokenOnce() {
        long now = System.currentTimeMillis() / 1000L;
        if (now - lastInvalidTokenNotifiedAtSec < 60) return; // throttle to 1/min
        lastInvalidTokenNotifiedAtSec = now;
        NotificationGroupManager.getInstance()
                .getNotificationGroup("GitLab Mentions")
                .createNotification("Token is not valid", NotificationType.ERROR)
                .addAction(NotificationAction.createSimpleExpiring("Configure gitLab mentions…", () ->
                        ShowSettingsUtil.getInstance().showSettingsDialog(null, "GitLab Mentions")))
                .notify(null);
    }

    /**
     * Searches GitLab users via REST API.
     * GET {hostUrl}/api/v4/users?search={query}&per_page={limit}
     * Adds PRIVATE-TOKEN header if configured in settings.
     */
    public List<GitLabUser> searchUsers(@NotNull String query, int limit) {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        String base = settings.hostUrl == null ? "https://gitlab.com" : settings.hostUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = base + "/api/v4/users?search=" + encoded + "&per_page=" + limit;
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .GET();
            if (settings.privateToken != null && !settings.privateToken.isBlank()) {
                builder.header("PRIVATE-TOKEN", settings.privateToken.trim());
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc == 401 || sc == 403) {
                notifyInvalidTokenOnce();
                log.warn("GitLab user search unauthorized/forbidden status={}", sc);
                return Collections.emptyList();
            }
            if (sc >= 200 && sc < 300) {
                return mapper.readValue(resp.body(), new TypeReference<List<GitLabUser>>(){});
            } else {
                log.warn("GitLab user search failed status={}", sc);
            }
        } catch (IOException | InterruptedException e) {
            log.debug("GitLab user search exception", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("Unexpected error calling GitLab API", ex);
        }
        return Collections.emptyList();
    }

    /**
     * Lists all members of a group (including inherited) using /groups/{id}/members/all with pagination.
     * Returns an empty list on any error. Requires a private token with appropriate access.
     */
    public List<GitLabUser> listAllGroupMembers(String groupId) {
        if (groupId == null || groupId.isBlank()) return Collections.emptyList();
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        String base = settings.hostUrl == null ? "https://gitlab.com" : settings.hostUrl.trim();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        List<GitLabUser> all = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        try {
            while (true) {
                String url = base + "/api/v4/groups/" + URLEncoder.encode(groupId, StandardCharsets.UTF_8) + "/members/all?per_page=" + perPage + "&page=" + page;
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET();
                if (settings.privateToken != null && !settings.privateToken.isBlank()) {
                    builder.header("PRIVATE-TOKEN", settings.privateToken.trim());
                }
                HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int sc = resp.statusCode();
                if (sc == 401 || sc == 403) {
                    notifyInvalidTokenOnce();
                    log.warn("Group members fetch unauthorized/forbidden status={} page={}", sc, page);
                    return Collections.emptyList();
                }
                if (sc >= 200 && sc < 300) {
                    List<GitLabUser> pageUsers = mapper.readValue(resp.body(), new TypeReference<List<GitLabUser>>(){});
                    if (pageUsers.isEmpty()) break;
                    all.addAll(pageUsers);
                    if (pageUsers.size() < perPage) break; // last page
                    page++;
                    if (page > 1000) break; // safety cap
                } else {
                    log.warn("Group members fetch failed status={} page={}", sc, page);
                    break;
                }
            }
        } catch (Exception ex) {
            log.warn("Error fetching group members", ex);
            return Collections.emptyList();
        }
        log.info("Fetched {} group members for group={}", all.size(), groupId);
        return all;
    }
}
