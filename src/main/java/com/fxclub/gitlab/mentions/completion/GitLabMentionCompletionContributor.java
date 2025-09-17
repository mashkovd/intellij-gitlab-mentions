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
import org.intellij.plugins.markdown.lang.MarkdownLanguage;

import java.util.List;

/**
 * Provides @mention completion in Markdown files using a local members cache.
 */
@Slf4j
public class GitLabMentionCompletionContributor extends CompletionContributor {

    public GitLabMentionCompletionContributor() {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().withLanguage(MarkdownLanguage.INSTANCE),
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
            if (!isMarkdownFile) return;

            Document doc = p.getEditor().getDocument();
            int offset = p.getOffset();
            CharSequence cs = doc.getCharsSequence();
            Integer atPos = findAtPrefixStart(cs, offset);
            if (atPos == null) return;

            String userPrefix = cs.subSequence(atPos + 1, offset).toString();
            if (userPrefix.isEmpty()) return;

            GitLabUserService service = ApplicationManager.getApplication().getService(GitLabUserService.class);
            service.ensureGroupMembersLoaded();
            List<GitLabUser> fromGroup = service.filterGroupMembers(userPrefix);
            if (fromGroup.isEmpty()) return;

            CompletionResultSet withPrefix = r.withPrefixMatcher(userPrefix);
            for (GitLabUser u : fromGroup) {
                String username = u.getUsername();
                if (username.isBlank()) continue;
                String label = (u.getName() == null || u.getName().isBlank()) ? username : u.getName();
                withPrefix.addElement(
                    LookupElementBuilder.create(username)
                        .withPresentableText(label)
                        .withTypeText("@" + username + " • GitLab", true)
                        .withLookupString("@" + username)
                        .withInsertHandler((context, item) -> {
                            Document d = context.getDocument();
                            int caret = context.getEditor().getCaretModel().getOffset();
                            CharSequence seqNow = d.getCharsSequence();
                            Integer atNow = findAtPrefixStart(seqNow, caret);
                            int startReplace = atNow != null ? atNow : context.getStartOffset();
                            int endReplace = context.getTailOffset();
                            String handle = "@" + username;
                            d.replaceString(startReplace, endReplace, handle);
                            context.getEditor().getCaretModel().moveToOffset(startReplace + handle.length());
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
                if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '.' || ch == '-') {
                    i--;
                    continue;
                }
                return null;
            }
            return null;
        }
    }
}