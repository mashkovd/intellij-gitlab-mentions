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

/** Explicitly fetches (or refetches) all group members for the configured group id. */
@Slf4j
public class FetchGroupMembersAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GitLabSettingsState settings = GitLabSettingsState.getInstance();
        if (settings.id == null || settings.id.isBlank()) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("GitLab Mentions")
                    .createNotification("Group ID not configured in Settings > GitLab Mentions.", NotificationType.WARNING)
                    .addAction(NotificationAction.createSimpleExpiring("Configure gitLab mentions…", () ->
                            ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), "GitLab Mentions")))
                    .notify(e.getProject());
            return;
        }
        GitLabUserService service = ApplicationManager.getApplication().getService(GitLabUserService.class);
        int count = service.forceReloadGroupMembers();
        if (count > 0) {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("GitLab Mentions")
                    .createNotification("Fetched " + count + " members for group ID " + settings.id + ".", NotificationType.INFORMATION)
                    .notify(e.getProject());
        } else {
            NotificationGroupManager.getInstance()
                    .getNotificationGroup("GitLab Mentions")
                    .createNotification("Failed to fetch group members (0 fetched). Check token and settings.", NotificationType.ERROR)
                    .addAction(NotificationAction.createSimpleExpiring("Configure gitLab mentions…", () ->
                            ShowSettingsUtil.getInstance().showSettingsDialog(e.getProject(), "GitLab Mentions")))
                    .notify(e.getProject());
        }
    }
}
