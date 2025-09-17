package com.fxclub.gitlab.mentions.actions;

import com.fxclub.gitlab.mentions.service.GitLabUserService;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/** Clears search cache and group members cache. */
@Slf4j
public class ClearGitLabCacheAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        GitLabUserService service = ApplicationManager.getApplication().getService(GitLabUserService.class);
        service.clearCache();
        NotificationGroupManager.getInstance()
                .getNotificationGroup("GitLab Mentions")
                .createNotification("GitLab Mentions caches cleared.", NotificationType.INFORMATION)
                .notify(e.getProject());
    }
}
