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
import java.util.function.Function;

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

    // Helpers
    private String normalizeBase(String base) {
        String b = (base == null || base.isBlank()) ? "https://gitlab.com" : base.trim();
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    private HttpRequest.Builder newRequest(String url, GitLabSettingsState settings) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (settings.privateToken != null && !settings.privateToken.isBlank()) {
            builder.header("PRIVATE-TOKEN", settings.privateToken.trim());
        }
        return builder;
    }

    private List<GitLabUser> fetchUsersPaged(Function<Integer, String> urlForPage,
                                             GitLabSettingsState settings,
                                             int perPage,
                                             String contextLogName) {
        List<GitLabUser> all = new ArrayList<>();
        int page = 1;
        try {
            while (true) {
                String url = urlForPage.apply(page);
                HttpResponse<String> resp = httpClient.send(newRequest(url, settings).build(), HttpResponse.BodyHandlers.ofString());
                int sc = resp.statusCode();
                if (sc == 401 || sc == 403) {
                    notifyInvalidTokenOnce();
                    log.warn("{} fetch unauthorized/forbidden status={} page={}", contextLogName, sc, page);
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
                    log.warn("{} fetch failed status={} page={}", contextLogName, sc, page);
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("{} fetch error", contextLogName, e);
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Unexpected {} fetch error", contextLogName, ex);
            return Collections.emptyList();
        }
        return all;
    }

    /**
     * Lists all members of a group (including inherited) using /groups/{id}/members/all with pagination,
     * or all active users using /users?active=true if groupId is null/blank.
     * Returns an empty list on any error. Requires a private token with appropriate access.
     */
    public List<GitLabUser> listAllMembersOrActiveUsers() {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        String groupId = settings.id;
        String base = normalizeBase(settings.hostUrl);
        final int perPage = 100;

        if (groupId != null && !groupId.isBlank()) {
            Function<Integer, String> urlForPage = page -> base + "/api/v4/groups/" + URLEncoder.encode(groupId, StandardCharsets.UTF_8)
                    + "/members/all?per_page=" + perPage + "&page=" + page;
            List<GitLabUser> users = fetchUsersPaged(urlForPage, settings, perPage, "Group members");
            log.info("Fetched {} group members for group={}", users.size(), groupId);
            return users;
        } else {
            Function<Integer, String> urlForPage = page -> base + "/api/v4/users?active=true&per_page=" + perPage + "&page=" + page;
            List<GitLabUser> users = fetchUsersPaged(urlForPage, settings, perPage, "Active users");
            log.info("Fetched {} active users", users.size());
            return users;
        }
    }
}
