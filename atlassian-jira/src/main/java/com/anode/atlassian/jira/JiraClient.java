package com.anode.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Jira Cloud REST API v3 ({@code <site>/rest/api/3}): issues, JQL search, comments, transitions.
 * Optional parameters are skipped when {@code null}. Records carry the fields used here, unknown ones are ignored.
 */
@HttpExchange("/rest/api/3")
public interface JiraClient {

    /** {@code fields} to return (all when {@code null}); expand {@code "renderedFields"} for HTML descriptions. */
    @GetExchange("/issue/{key}")
    Issue getIssue(@PathVariable("key") String issueKey,
            @RequestParam(name = "fields", required = false) List<String> fields,
            @RequestParam(name = "expand", required = false) String expand);

    @PostExchange("/search/jql")
    SearchResults search(@RequestBody Search search);

    /** {@code comment}'s body is Atlassian Document Format, see {@link #comment(String, String)}. */
    @PostExchange("/issue/{key}/comment")
    Comment addComment(@PathVariable("key") String issueKey, @RequestBody NewComment comment);

    @GetExchange("/issue/{key}/transitions")
    Transitions getTransitions(@PathVariable("key") String issueKey);

    @PostExchange("/issue/{key}/transitions")
    void doTransition(@PathVariable("key") String issueKey, @RequestBody DoTransition transition);

    /** The authenticated account (a cheap connectivity check). */
    @GetExchange("/myself")
    User myself();

    /** Comments plain {@code text} on an issue: blank lines separate paragraphs, other line breaks are kept. */
    default Comment comment(String issueKey, String text) {
        return addComment(issueKey, new NewComment(document(text)));
    }

    /** {@code text} as Atlassian Document Format, which API v3 requires for rich-text fields. */
    private static Map<String, Object> document(String text) {
        List<Map<String, Object>> paragraphs = new ArrayList<>();
        for (String block : text.strip().split("\\R\\s*\\R")) {
            List<Map<String, Object>> content = new ArrayList<>();
            for (String line : block.split("\\R")) {
                if (!content.isEmpty()) {
                    content.add(Map.of("type", "hardBreak"));
                }
                content.add(Map.of("type", "text", "text", line));
            }
            paragraphs.add(Map.of("type", "paragraph", "content", content));
        }
        return Map.of("type", "doc", "version", 1, "content", paragraphs);
    }

    /**
     * Moves an issue through the transition named {@code nameOrStatus}, or leading to that status.
     *
     * @throws IllegalStateException when the issue has no such transition
     */
    default void transition(String issueKey, String nameOrStatus) {
        Transition available = getTransitions(issueKey).transitions().stream()
                .filter(t -> nameOrStatus.equalsIgnoreCase(t.name())
                        || (t.to() != null && nameOrStatus.equalsIgnoreCase(t.to().name())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No transition to '" + nameOrStatus + "' on " + issueKey));
        doTransition(issueKey, new DoTransition(new Id(available.id())));
    }

    /** {@code fields} and {@code renderedFields} as Jackson reads them, keyed by field id. */
    record Issue(String id, String key, String self, Map<String, Object> fields, Map<String, Object> renderedFields) {
        /** Where people read the issue. */
        public String browseUrl() {
            return self.substring(0, self.indexOf("/rest/")) + "/browse/" + key;
        }
    }

    /** Pass the previous page's {@code nextPageToken} for the following one. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Search(String jql, List<String> fields, Integer maxResults, String nextPageToken) {
        public static Search jql(String jql) {
            return new Search(jql, null, null, null);
        }
    }

    record SearchResults(List<Issue> issues, String nextPageToken, Boolean isLast) {}

    /** {@code body} is an Atlassian Document Format document. */
    record NewComment(Map<String, Object> body) {}

    record Comment(String id) {}

    record Transitions(List<Transition> transitions) {}

    record Transition(String id, String name, Status to) {}

    record Status(String name) {}

    record DoTransition(Id transition) {}

    record Id(String id) {}

    record User(String accountId, String displayName, String emailAddress) {}
}
