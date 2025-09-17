package com.fxclub.gitlab.mentions.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

@Service(Service.Level.APP)
@State(name = "GitLabMentionsSettings", storages = @Storage("gitlab-mentions.xml"))
@Slf4j
public final class GitLabSettingsState implements PersistentStateComponent<GitLabSettingsState> {
    public String hostUrl = "https://gitlab.com"; // safe default
    public String privateToken = ""; // no hardcoded token
    public int cacheTtlSeconds = 300;
    public int maxUsersPerQuery = 10;

    public String scope = "";
    public String id = "";

    public GitLabSettingsState() {
        // Try to load overrides from application.properties on class construction
        loadFromApplicationProperties();
    }

    private void loadFromApplicationProperties() {
        Properties props = new Properties();
        try (InputStream in = GitLabSettingsState.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) return;
            props.load(in);
        } catch (IOException ignored) {
            return;
        }

        // 1) Preferred: separate properties
        String urlP = trimOrNull(props.getProperty("GITLAB_URL"));
        String tokenP = trimOrNull(props.getProperty("GITLAB_TOKEN"));
        String groupIdP = trimOrNull(props.getProperty("GITLAB_GROUP_ID"));
        String ttlP = trimOrNull(props.getProperty("GITLAB_CACHE_TTL"));
        String maxUsersP = trimOrNull(props.getProperty("GITLAB_MAX_USERS_PER_QUERY"));
        String scopeP = trimOrNull(props.getProperty("GITLAB_SCOPE"));

        boolean anySeparate = urlP != null || tokenP != null || groupIdP != null || ttlP != null || maxUsersP != null || scopeP != null;
        if (anySeparate) {
            if (urlP != null) hostUrl = urlP;
            if (tokenP != null) privateToken = tokenP;
            if (groupIdP != null) id = groupIdP;
            if (scopeP != null) scope = scopeP;
            if (ttlP != null) {
                try { cacheTtlSeconds = Math.max(0, Integer.parseInt(ttlP)); } catch (NumberFormatException ignored) {}
            }
            if (maxUsersP != null) {
                try { maxUsersPerQuery = Math.max(1, Integer.parseInt(maxUsersP)); } catch (NumberFormatException ignored) {}
            }
            return; // done
        }

        // 2) Backward compatibility: composite GITLAB_URL with URL;ID=...;SCOPE=...;TOKEN=...
        String composite = trimOrNull(props.getProperty("GITLAB_URL"));
        if (composite == null) return;
        // If the property contains no semicolons or key-value pairs, treat it as plain URL
        boolean looksComposite = composite.contains(";");
        if (!looksComposite) {
            hostUrl = composite;
            return;
        }
        String[] parts = composite.split(";");
        String urlCandidate = null;
        String tokenCandidate = null;
        String idCandidate = null;
        String scopeCandidate = null;
        for (String p : parts) {
            String part = p.trim();
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq > 0) {
                String key = part.substring(0, eq).trim().toUpperCase(Locale.ROOT);
                String val = part.substring(eq + 1).trim();
                switch (key) {
                    case "URL": urlCandidate = val; break;
                    case "TOKEN": tokenCandidate = val; break;
                    case "ID": idCandidate = val; break;
                    case "SCOPE": scopeCandidate = val; break;
                    default: // ignore
                }
            } else {
                // token without '=', assume it's the base URL
                urlCandidate = part;
            }
        }
        if (urlCandidate != null && !urlCandidate.isBlank()) hostUrl = urlCandidate;
        if (tokenCandidate != null && !tokenCandidate.isBlank()) privateToken = tokenCandidate;
        if (idCandidate != null) id = idCandidate;
        if (scopeCandidate != null) scope = scopeCandidate;
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    public static GitLabSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(GitLabSettingsState.class);
    }

    @Override
    public @Nullable GitLabSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull GitLabSettingsState state) {
        this.hostUrl = state.hostUrl;
        this.privateToken = state.privateToken;
        this.cacheTtlSeconds = state.cacheTtlSeconds;
        this.maxUsersPerQuery = state.maxUsersPerQuery;
        this.scope = state.scope;
        this.id = state.id;
    }
}
