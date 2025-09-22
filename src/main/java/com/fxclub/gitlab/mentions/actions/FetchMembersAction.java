package com.fxclub.gitlab.mentions.actions;

import com.fxclub.gitlab.mentions.service.GitLabUserService;
import com.fxclub.gitlab.mentions.settings.GitLabSettingsState;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.notification.NotificationAction;
import lombok.extern.slf4j.Slf4j;

/** Explicitly fetches (or refetches) users: group members if Group ID is set, otherwise active users. */
@Slf4j
public class FetchMembersAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        GitLabUserService service = ApplicationManager.getApplication().getService(GitLabUserService.class);
        int count = service.forceReloadMembers();
        if (count > 0) {
            String msg = (settings.id != null && !settings.id.isBlank())
                    ? ("Fetched " + count + " members for group ID " + settings.id + ".")
                    : ("Fetched " + count + " active users.");
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("GitLab Mentions")
                    .createNotification(msg, NotificationType.INFORMATION)
                    .notify(e.getProject());
        }
        else {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("GitLab Mentions")
                    .createNotification("Failed to fetch users (0 fetched). Check token and settings.", NotificationType.ERROR)
                    .addAction(NotificationAction.createSimpleExpiring("Configure gitLab mentions…", () ->
                            ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), "GitLab Mentions")))
                    .notify(e.getProject());
        }
    }
}
