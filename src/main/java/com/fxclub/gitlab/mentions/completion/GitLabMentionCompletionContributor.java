package com.fxclub.gitlab.mentions.completion;

import com.fxclub.gitlab.mentions.model.GitLabUser;
import com.fxclub.gitlab.mentions.service.GitLabUserService;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Provides @mention completion in Markdown and CODEOWNERS files using a local members cache.
 */
@Slf4j
public class GitLabMentionCompletionContributor extends CompletionContributor {

    public GitLabMentionCompletionContributor() {
        // Register a single provider; plugin.xml creates instances for each language we declared
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            new Provider()
        );
    }

    private static class Provider extends CompletionProvider<CompletionParameters> {
        @Override
        protected void addCompletions(@NotNull CompletionParameters p,
                                      @NotNull ProcessingContext c,
                                      @NotNull CompletionResultSet r) {
            PsiFile original = p.getOriginalFile();
            boolean isMarkdownFile = original.getName().toLowerCase().endsWith(".md")
                                  || original.getName().toLowerCase().endsWith(".mdx");
            boolean isCodeowners = original.getName().equalsIgnoreCase("CODEOWNERS");
            if (!isMarkdownFile && !isCodeowners) return;

            Document doc = p.getEditor().getDocument();
            int offset = p.getOffset();
            CharSequence cs = doc.getCharsSequence();
            Integer atPos = findAtPrefixStart(cs, offset);
            if (atPos == null) return;

            String userPrefix = cs.subSequence(atPos + 1, offset).toString();
            if (userPrefix.isEmpty()) return;

            GitLabUserService service = ApplicationManager.getApplication().getService(GitLabUserService.class);
            // Synchronous warm-up: call ensureGroupMembersLoaded if available, otherwise fallback to forceReloadMembers
            try {
                service.getClass().getMethod("ensureGroupMembersLoaded").invoke(service);
            } catch (Exception reflectError) {
                // Fallback to a synchronous reload
                service.forceReloadMembers();
            }
            List<GitLabUser> fromGroup = service.filterGroupMembers(userPrefix);
            if (fromGroup.isEmpty()) return;

            // Use case-insensitive prefix matcher so subsequent attempts work reliably
            r = r.withPrefixMatcher(new PlainPrefixMatcher(userPrefix, false));
            // Keep completion session alive while the prefix changes
            r.restartCompletionOnAnyPrefixChange();

            for (GitLabUser u : fromGroup) {
                String username = u.getUsername();
                if (username.isBlank()) continue;
                String label = (u.getName() == null || u.getName().isBlank()) ? username : u.getName();
                r.addElement(
                    LookupElementBuilder.create(username)
                        .withPresentableText(label)
                        .withTypeText("@" + username + " • GitLab", true)
                        .withLookupString("@" + username)
                        .withInsertHandler((context, item) -> {
                            Document d = context.getDocument();
                            int start = context.getStartOffset();
                            int end = context.getTailOffset();
                            CharSequence cur = d.getCharsSequence();
                            boolean hasAtBefore = start > 0 && cur.charAt(start - 1) == '@';
                            String handle = hasAtBefore ? username : ("@" + username);
                            d.replaceString(start, end, handle);
                            context.getEditor().getCaretModel().moveToOffset(start + handle.length());
                        })
                );
            }
        }

        /**
         * Finds the '@' that starts the mention prefix immediately before caret.
         * Returns null if the caret is not within a mention token.
         */
        private static Integer findAtPrefixStart(CharSequence seq, int caret) {
            int i = caret - 1;
            if (i < 0 || i >= seq.length()) return null;
            while (i >= 0) {
                char ch = seq.charAt(i);
                if (ch == '@') return i;
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-' || ch == '/') {
                    i--;
                    continue;
                }
                return null;
            }
            return null;
        }
    }
}