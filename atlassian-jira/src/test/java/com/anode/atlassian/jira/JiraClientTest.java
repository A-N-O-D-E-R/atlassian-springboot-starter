package com.anode.atlassian.jira;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.anode.atlassian.jira.JiraClient.BulkCreated;
import com.anode.atlassian.jira.JiraClient.BulkIssues;
import com.anode.atlassian.jira.JiraClient.IssueFields;
import com.anode.atlassian.jira.JiraClient.IssueLink;
import com.anode.atlassian.jira.JiraClient.Search;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

class JiraClientTest {

    private static final String API = "https://acme.atlassian.net/rest/api/3";

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://acme.atlassian.net");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final JiraClient jira = HttpServiceProxyFactory.builderFor(RestClientAdapter.create(builder.build()))
            .build().createClient(JiraClient.class);

    @Test
    void readsAnIssueWithItsRenderedDescription() {
        server.expect(requestTo(API + "/issue/ALE-42?fields=summary&fields=description&expand=renderedFields"))
                .andRespond(withSuccess("{\"id\":\"1\",\"key\":\"ALE-42\",\"self\":\"" + API + "/issue/1\","
                        + "\"fields\":{\"summary\":\"Fix it\"},\"renderedFields\":{\"description\":\"<p>Broken</p>\"},"
                        + "\"unknownField\":1}", MediaType.APPLICATION_JSON));

        var issue = jira.getIssue("ALE-42", List.of("summary", "description"), "renderedFields");

        assertThat(issue.key()).isEqualTo("ALE-42");
        assertThat(issue.fields()).containsEntry("summary", "Fix it");
        assertThat(issue.renderedFields()).containsEntry("description", "<p>Broken</p>");
        assertThat(issue.browseUrl()).isEqualTo("https://acme.atlassian.net/browse/ALE-42");
    }

    @Test
    void searchesWithJql() {
        server.expect(requestTo(API + "/search/jql"))
                .andExpect(content().json("{\"jql\":\"labels = factory\",\"maxResults\":10}", true))
                .andRespond(withSuccess("{\"issues\":[{\"id\":\"1\",\"key\":\"ALE-1\"}],\"isLast\":true}",
                        MediaType.APPLICATION_JSON));

        var result = jira.search(new Search("labels = factory", null, 10, null));

        assertThat(result.issues()).extracting("key").containsExactly("ALE-1");
        assertThat(result.isLast()).isTrue();
    }

    @Test
    void commentsInAtlassianDocumentFormat() {
        server.expect(requestTo(API + "/issue/ALE-42/comment"))
                .andExpect(content().json("{\"body\":{\"type\":\"doc\",\"version\":1,\"content\":["
                        + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"PR opened.\"},"
                        + "{\"type\":\"hardBreak\"},{\"type\":\"text\",\"text\":\"Review please\"}]},"
                        + "{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"See #12\"}]}]}}", true))
                .andRespond(withSuccess("{\"id\":\"c1\"}", MediaType.APPLICATION_JSON));

        assertThat(jira.comment("ALE-42", "PR opened.\nReview please\n\nSee #12\n").id()).isEqualTo("c1");
    }

    @Test
    void transitionsByTheStatusItLeadsTo() {
        server.expect(method(HttpMethod.GET)).andExpect(requestTo(API + "/issue/ALE-42/transitions"))
                .andRespond(withSuccess("{\"transitions\":[{\"id\":\"11\",\"name\":\"Start\",\"to\":{\"name\":\"In Progress\"}},"
                        + "{\"id\":\"31\",\"name\":\"Finish\",\"to\":{\"name\":\"Done\"}}]}", MediaType.APPLICATION_JSON));
        server.expect(method(HttpMethod.POST)).andExpect(requestTo(API + "/issue/ALE-42/transitions"))
                .andExpect(jsonPath("$.transition.id").value("31"))
                .andRespond(withNoContent());

        jira.transition("ALE-42", "done");

        server.verify();
    }

    @Test
    void anUnknownTransitionIsRefused() {
        server.expect(requestTo(API + "/issue/ALE-42/transitions"))
                .andRespond(withSuccess("{\"transitions\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> jira.transition("ALE-42", "Done"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Done");
    }

    @Test
    void listsTheIssueTypesAProjectCanCreate() {
        server.expect(requestTo(API + "/issue/createmeta/PD/issuetypes?startAt=0&maxResults=50"))
                .andRespond(withSuccess("{\"issueTypes\":[{\"id\":\"10000\",\"name\":\"Epic\",\"hierarchyLevel\":1}],"
                        + "\"total\":1,\"isLast\":true}", MediaType.APPLICATION_JSON));

        var types = jira.getCreateIssueTypes("PD", 0, 50);

        assertThat(types.issueTypes()).singleElement().satisfies(t -> assertThat(t.hierarchyLevel()).isEqualTo(1));
        assertThat(types.isLast()).isTrue();
    }

    @Test
    void aFailedBulkCreateKeepsItsErrorsInTheBody() {
        server.expect(requestTo(API + "/issue/bulk"))
                .andExpect(content().json("{\"issueUpdates\":[{\"fields\":{\"summary\":\"S\"}}]}", true))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("{\"issues\":[],\"errors\":"
                        + "[{\"failedElementNumber\":0,\"elementErrors\":{\"errors\":{\"summary\":\"too long\"}}}]}"));

        var failure = org.assertj.core.api.Assertions.catchThrowableOfType(HttpClientErrorException.class,
                () -> jira.createIssues(new BulkIssues(List.of(new IssueFields(Map.of("summary", "S"))))));

        var body = failure.getResponseBodyAs(BulkCreated.class);
        assertThat(body.errors()).singleElement().satisfies(e -> {
            assertThat(e.failedElementNumber()).isZero();
            assertThat(e.elementErrors().errors()).containsEntry("summary", "too long");
        });
    }

    @Test
    void theBlockingIssueIsTheInwardOne() {
        server.expect(requestTo(API + "/issueLink"))
                .andExpect(content().json("{\"type\":{\"name\":\"Blocks\"},\"inwardIssue\":{\"key\":\"PD-1\"},"
                        + "\"outwardIssue\":{\"key\":\"PD-2\"}}", true))
                .andRespond(withSuccess());

        jira.linkIssues(IssueLink.blocks("PD-1", "PD-2"));

        server.verify();
    }
}
