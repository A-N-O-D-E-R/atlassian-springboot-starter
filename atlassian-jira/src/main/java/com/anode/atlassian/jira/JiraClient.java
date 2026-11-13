package com.anode.atlassian.jira;

import com.fasterxml.jackson.annotation.JsonAlias;
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
import org.springframework.web.service.annotation.PutExchange;

/**
 * Jira Cloud REST API v3 ({@code <site>/rest/api/3}): issues, JQL search, comments, transitions, links.
 * Optional parameters are skipped when {@code null}. Records carry the fields used here, unknown ones are ignored.
 */
@HttpExchange("/rest/api/3")
public interface JiraClient {

    /** {@code fields} to return (all when {@code null}); expand {@code "renderedFields"} for HTML descriptions. */
    @GetExchange("/issue/{key}")
    Issue getIssue(@PathVariable("key") String issueKey,
            @RequestParam(name = "fields", required = false) List<String> fields,
            @RequestParam(name = "expand", required = false) String expand);

    /** The issue types {@code projectKey} can create, a page at a time ({@code isLast} on the last one). */
    @GetExchange("/issue/createmeta/{project}/issuetypes")
    IssueTypes getCreateIssueTypes(@PathVariable("project") String projectKey,
            @RequestParam(name = "startAt", required = false) Integer startAt,
            @RequestParam(name = "maxResults", required = false) Integer maxResults);

    @PostExchange("/issue")
    CreatedIssue createIssue(@RequestBody IssueFields issue);

    /**
     * Up to 50 issues. A 201 can still carry failures in {@code errors}; a 400 means all failed, with the same
     * body ({@code HttpClientErrorException.getResponseBodyAs(BulkCreated.class)}).
     */
    @PostExchange("/issue/bulk")
    BulkCreated createIssues(@RequestBody BulkIssues issues);

    /** Sets {@code issue}'s fields, leaving the others. */
    @PutExchange("/issue/{key}")
    void editIssue(@PathVariable("key") String issueKey, @RequestBody IssueFields issue);

    /** See {@link IssueLink#blocks}. */
    @PostExchange("/issueLink")
    void linkIssues(@RequestBody IssueLink link);

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
        /** Where people read the issue (when called on the site, not through the scoped-token gateway). */
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

    /** {@code fields} keyed by field id, e.g. {@code summary}, {@code issuetype}, {@code project}, {@code parent}. */
    record IssueFields(Map<String, Object> fields) {}

    record CreatedIssue(String id, String key, String self) {}

    record IssueTypes(@JsonAlias("values") List<IssueType> issueTypes, Integer total, Boolean isLast) {}

    /** {@code hierarchyLevel}: 1 epic, 0 standard (story, task...), -1 sub-task. */
    record IssueType(String id, String name, Integer hierarchyLevel) {}

    record BulkIssues(List<IssueFields> issueUpdates) {}

    /** {@code issues} are the created ones, in order; {@code errors} index the request's {@code issueUpdates}. */
    record BulkCreated(List<CreatedIssue> issues, List<BulkError> errors) {}

    record BulkError(Integer failedElementNumber, ElementErrors elementErrors) {}

    /** {@code errors} by field, {@code errorMessages} for the whole issue. */
    record ElementErrors(Map<String, String> errors, List<String> errorMessages) {}

    record IssueLink(Name type, Key inwardIssue, Key outwardIssue) {
        /**
         * {@code blocking} blocks {@code blocked}. On Jira Cloud it is {@code inwardIssue} that "blocks"
         * {@code outwardIssue}, the reverse of what the names suggest.
         */
        public static IssueLink blocks(String blocking, String blocked) {
            return new IssueLink(new Name("Blocks"), new Key(blocking), new Key(blocked));
        }
    }

    record Name(String name) {}

    record Key(String key) {}

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
