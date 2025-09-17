# GitLab Mentions IntelliJ Plugin


Adds `@username` completion for GitLab users inside Markdown files.

@a -> @artur.abdulaev

## Features
* Type `@` followed by at least one character in a Markdown file to trigger completion.
* Fetches users from the configured GitLab instance using the REST API (`/api/v4/users?search=`).
* Simple in‑memory caching with configurable TTL.
* Settings panel: GitLab host URL, Personal Access Token, cache TTL, max results.

## Requirements
* IntelliJ IDEA 2024.2+ (Community or Ultimate)
* Java 17+
* GitLab Personal Access Token with `read_api` scope for private instances or better results.

## Build & Run
```bash
./gradlew build          # build plugin
./gradlew runIde         # launch IntelliJ with the plugin
```
The built plugin archive will be under `build/distributions/`.

## Configuration
Open: Settings / Preferences > Tools > GitLab Mentions (search for "GitLab Mentions").

Fields:
* GitLab Host URL – e.g. `https://gitlab.com` or your self-managed instance.
* Private Token – optional PAT (read_api). Stored in plain text in config; consider using a low-scope token.
* Cache TTL – seconds to reuse search responses.
* Max Users Per Query – API `per_page` and completion cap.

### application.properties override
You can provide defaults via `src/main/resources/application.properties` (or in the plugin classpath at runtime) using a single composite property:

```
GITLAB_URL=https://git.fxclub.org;ID=6567;SCOPE=group;TOKEN=glpat-xxxxxxxx
```

- URL or plain leading value sets the GitLab Host URL.
- TOKEN sets the Personal Access Token.
- ID and SCOPE are parsed and stored for potential future use (not currently used in API calls).

Precedence: values from `application.properties` override built-in defaults on startup, but user-changed settings in the IDE will be persisted and take precedence after they are saved.

## Limitations / Future Ideas
* Add avatar icons in completion popup (needs additional icon loading & caching).
* Support issue / merge request description editors (currently Markdown only but they are Markdown-backed; may extend patterns if needed).
* Handle rate limiting / backoff strategy.
* Persistent disk cache.

## Privacy / Security
The token is only sent to the configured GitLab host over HTTPS. No data is sent elsewhere.

## License
MIT (add a LICENSE file if distributing).


## Where are mentions fetched?
The plugin fetches GitLab users (used for @mention completion) from the following places in the codebase:

- API client: src/main/java/com/fxclub/gitlab/mentions/api/GitLabApiClient.java
  - Method: searchUsers(String query, int limit)
  - Makes an HTTP GET request to: {hostUrl}/api/v4/users?search={query}&per_page={limit}
  - Adds header PRIVATE-TOKEN: <token> if configured in Settings.

- Service layer: src/main/java/com/fxclub/gitlab/mentions/service/GitLabUserService.java
  - Method: searchUsers(String prefix)
    - Delegates to GitLabApiClient and caches results in-memory using a TTL from settings.
  - Method: suggestForPrefix(String prefix)
    - Aggregates cached results and filters by username for fast suggestions.

- Settings: src/main/java/com/fxclub/gitlab/mentions/settings/GitLabSettingsState.java
  - Fields used: hostUrl, privateToken, cacheTtlSeconds, maxUsersPerQuery.

Note: The completion contributor (registered in plugin.xml) relies on the service layer to obtain suggestions.
