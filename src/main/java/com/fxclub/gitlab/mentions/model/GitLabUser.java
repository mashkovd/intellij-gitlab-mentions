package com.fxclub.gitlab.mentions.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Represents a GitLab user with a stable identity (id) and username.
 */
@Data
@Builder(toBuilder = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@RequiredArgsConstructor
@AllArgsConstructor
public class GitLabUser {
    @EqualsAndHashCode.Include
    private long id;

    @NonNull
    private String username;

    private String name;

    @Override
    public String toString() {
        return username + (name != null && !name.isBlank() ? " (" + name + ")" : "");
    }
}