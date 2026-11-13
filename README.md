# Atlassian Spring Boot Starter

Spring Boot 4.1 starters (Java 25) for Atlassian Cloud, under `io.github.a-n-o-d-e-r`. Clients are Spring
HTTP interfaces (`@HttpExchange`) over `RestClient`, with records for the JSON they use.

| Module | Content |
|---|---|
| `atlassian-jira` | `JiraClient`: issues, JQL search, comments, transitions (REST API v3) |
| `atlassian-confluence` | `ConfluenceClient`: spaces, pages, child pages, footer comments (REST API v2) |
| `atlassian-autoconfigure` | Beans from `anode.atlassian.*` |
| `jira-spring-boot-starter`, `confluence-spring-boot-starter` | Client + auto-configuration |
| `atlassian-dependencies` | BOM |

```xml
<dependency>
    <groupId>io.github.a-n-o-d-e-r</groupId>
    <artifactId>jira-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Configuration — `anode.atlassian.*`

Jira and Confluence share the site and the account. Nothing is configured until `base-url`,
`email` and `api-token` are all set; set the token from the environment (`ANODE_ATLASSIAN_API_TOKEN`), it is never logged.

| Property | Default | Meaning |
|---|---|---|
| `base-url` | — | `https://your-domain.atlassian.net` |
| `email`, `api-token` | — | Account and its [API token](https://id.atlassian.com/manage-profile/security/api-tokens) |
| `jira.enabled`, `confluence.enabled` | `true` | Switch a client off |

Each client bean can be replaced by declaring your own. Clients use the application's `RestClient.Builder` when
there is one, and do not retry: wrap calls (e.g. Resilience4j `@Retry`) where 429/5xx handling matters.

## Usage

```java
jira.search(Search.jql("project = OPS")).issues();
String description = (String) jira.getIssue("OPS-12", List.of("description"), "renderedFields").renderedFields().get("description");
jira.comment("OPS-12", "PR opened: https://...");
jira.transition("OPS-12", "Done");          // transition name or target status

String spaceId = confluence.getSpaces(List.of("DOC"), null, null).results().getFirst().id();
confluence.createPage(new NewPage(spaceId, "Runbook", null, Body.storage("<p>...</p>")), null);
```

## Adding an operation

Declare it on `JiraClient` / `ConfluenceClient` (`@GetExchange`, `@PostExchange`...) with records for its JSON.

## Build

```bash
sdk env        # JDK 25 (.sdkmanrc)
mvn verify
```

Release to Maven Central: `mvn -Pcentral deploy`.
