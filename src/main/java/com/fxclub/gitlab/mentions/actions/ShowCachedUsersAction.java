package com.fxclub.gitlab.mentions.actions;

import com.fxclub.gitlab.mentions.model.GitLabUser;
import com.fxclub.gitlab.mentions.service.GitLabUserService;
import com.fxclub.gitlab.mentions.settings.GitLabSettingsState;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Shows a brief snapshot of cached GitLab users (group members if Group ID is set, otherwise active users). */
@Slf4j
public class ShowCachedUsersAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GitLabUserService service = ApplicationManager.getApplication().getService(GitLabUserService.class);
        List<GitLabUser> cached = service.getGroupMembersSnapshot();
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        boolean hasGroup = settings.id != null && !settings.id.isBlank();

        StringBuilder msg = new StringBuilder();
        if (hasGroup) {
            msg.append("Group Members Cached: ").append(cached.size()).append(" (group ").append(settings.id).append(")\n");
        } else {
            msg.append("Active Users Cached: ").append(cached.size()).append('\n');
        }
        // Append first few entries for quick glance
        int preview = Math.min(15, cached.size());
        if (preview > 0) {
            msg.append("\n-- Preview (first ").append(preview).append(") --\n");
            for (int i = 0; i < preview; i++) {
                GitLabUser u = cached.get(i);
                msg.append("@").append(u.getUsername());
                if (u.getName() != null && !u.getName().isBlank()) msg.append("  ").append(u.getName());
                msg.append('\n');
            }
            if (cached.size() > preview) msg.append("...");
        }
        NotificationGroupManager.getInstance()
                .getNotificationGroup("GitLab Mentions")
                .createNotification(msg.toString(), NotificationType.INFORMATION)
                .notify(e.getProject());
    }
}
