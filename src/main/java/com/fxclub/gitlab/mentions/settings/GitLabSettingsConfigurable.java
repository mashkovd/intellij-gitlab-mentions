package com.fxclub.gitlab.mentions.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

@Slf4j
public class GitLabSettingsConfigurable implements Configurable {
    private JPanel panel;
    private JTextField hostUrlField;
    private JPasswordField tokenField;
    private JSpinner cacheTtlSpinner;
    private JSpinner maxUsersSpinner;
    private JTextField groupIdField; // new field

    private GitLabSettingsState state;

    public GitLabSettingsConfigurable() {
        state = GitLabSettingsState.getInstance();
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Title) String getDisplayName() {
        return "GitLab Mentions";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (panel == null) {
            panel = new JPanel(new GridBagLayout());
            panel.setBorder(JBUI.Borders.empty(8));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4,4,4,4);
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.NORTHWEST;
            panel.add(new JLabel("GitLab Host URL:"), gbc);
            hostUrlField = new JTextField(state.hostUrl, 30);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            panel.add(hostUrlField, gbc);

            gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Private Token:"), gbc);
            tokenField = new JPasswordField(state.privateToken, 30);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            panel.add(tokenField, gbc);

            gbc.gridx = 0; gbc.gridy++; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Group ID (optional):"), gbc);
            groupIdField = new JTextField(state.id == null ? "" : state.id, 15);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(groupIdField, gbc);

            gbc.gridx = 0; gbc.gridy++; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(new JLabel("Cache TTL (s):"), gbc);
            cacheTtlSpinner = new JSpinner(new SpinnerNumberModel(state.cacheTtlSeconds, 30, 3600, 30));
            gbc.gridx = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
            panel.add(cacheTtlSpinner, gbc);

            gbc.gridx = 0; gbc.gridy++; gbc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Max users for autocomplete:"), gbc);
            maxUsersSpinner = new JSpinner(new SpinnerNumberModel(state.maxUsersPerQuery, 1, 200, 1));
            gbc.gridx = 1;
            panel.add(maxUsersSpinner, gbc);

            gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2;
            JLabel info = new JLabel("Token requires read_api scope. Group ID is optional.");
            info.setFont(info.getFont().deriveFont(Font.ITALIC, info.getFont().getSize() - 1));
            panel.add(info, gbc);

            gbc.gridy++; gbc.gridx = 0; gbc.gridwidth = 2;
            gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
            panel.add(Box.createVerticalGlue(), gbc);
        }
        return panel;
    }

    @Override
    public boolean isModified() {
        if (hostUrlField == null) return false;
        boolean modified = !hostUrlField.getText().equals(state.hostUrl)
                || !new String(tokenField.getPassword()).equals(state.privateToken)
                || (groupIdField != null && !Objects.equals(groupIdField.getText(), state.id))
                || (cacheTtlSpinner != null && (int) cacheTtlSpinner.getValue() != state.cacheTtlSeconds);
        if (maxUsersSpinner != null) {
            modified = modified || (int) maxUsersSpinner.getValue() != state.maxUsersPerQuery;
        }
        return modified;
    }

    @Override
    public void apply() throws ConfigurationException {
        if (hostUrlField == null) return;
        String host = hostUrlField.getText().trim();
        String token = new String(tokenField.getPassword()).trim();
        String groupId = groupIdField != null ? groupIdField.getText().trim() : "";
        if (host.isEmpty()) throw new ConfigurationException("GitLab Host URL is required");
        if (token.isEmpty()) throw new ConfigurationException("Private Token is required");
        // Group ID is optional

        state.hostUrl = host;
        state.privateToken = token;
        state.id = groupId; // may be empty => will trigger fetching active users instead of group members
        state.cacheTtlSeconds = (int) cacheTtlSpinner.getValue();
        if (maxUsersSpinner != null) {
            state.maxUsersPerQuery = (int) maxUsersSpinner.getValue();
        }
    }

    @Override
    public void reset() {
        if (hostUrlField == null) return;
        hostUrlField.setText(state.hostUrl);
        tokenField.setText(state.privateToken);
        if (groupIdField != null) groupIdField.setText(state.id == null ? "" : state.id);
        if (cacheTtlSpinner != null) cacheTtlSpinner.setValue(state.cacheTtlSeconds);
        if (maxUsersSpinner != null) maxUsersSpinner.setValue(state.maxUsersPerQuery);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return hostUrlField; // may be null until createComponent
    }

    @Override
    public void disposeUIResources() {
        panel = null;
        hostUrlField = null;
        tokenField = null;
        cacheTtlSpinner = null;
        maxUsersSpinner = null;
        groupIdField = null;
    }
}
