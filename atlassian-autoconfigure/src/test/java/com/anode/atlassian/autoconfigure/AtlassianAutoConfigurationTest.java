package com.anode.atlassian.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.anode.atlassian.confluence.ConfluenceClient;
import com.anode.atlassian.jira.JiraClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AtlassianAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AtlassianAutoConfiguration.class));

    private static final String[] SITE = {"anode.atlassian.base-url=https://acme.atlassian.net/",
            "anode.atlassian.email=bot@acme.org", "anode.atlassian.api-token=t"};

    @Test
    void clientsOnlyWithSiteAccountAndToken() {
        runner.withPropertyValues("anode.atlassian.base-url=https://acme.atlassian.net", "anode.atlassian.email=bot@acme.org")
                .run(context -> assertThat(context).doesNotHaveBean(JiraClient.class).doesNotHaveBean(ConfluenceClient.class));
        runner.withPropertyValues(SITE)
                .run(context -> assertThat(context).hasSingleBean(JiraClient.class).hasSingleBean(ConfluenceClient.class));
    }

    @Test
    void eachProductCanBeSwitchedOff() {
        runner.withPropertyValues(SITE).withPropertyValues("anode.atlassian.jira.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(JiraClient.class).hasSingleBean(ConfluenceClient.class));
        runner.withPropertyValues(SITE).withPropertyValues("anode.atlassian.confluence.enabled=false")
                .run(context -> assertThat(context).hasSingleBean(JiraClient.class).doesNotHaveBean(ConfluenceClient.class));
    }

    @Test
    void callsTheSiteAsTheAccountThroughTheApplicationsBuilder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://acme.atlassian.net/rest/api/3/myself"))
                .andExpect(header("Authorization", "Basic Ym90QGFjbWUub3JnOnQ=")) // bot@acme.org:t
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"accountId\":\"a1\"}", MediaType.APPLICATION_JSON));

        runner.withPropertyValues(SITE).withBean(RestClient.Builder.class, () -> builder)
                .run(context -> assertThat(context.getBean(JiraClient.class).myself().accountId()).isEqualTo("a1"));
        server.verify();
    }
}
