package com.anode.atlassian.autoconfigure;

import com.anode.atlassian.confluence.ConfluenceClient;
import com.anode.atlassian.jira.JiraClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * Jira and Confluence clients from {@code anode.atlassian.*}, calling the site as the account; each bean can be
 * replaced. Built on the application's {@link RestClient.Builder} when there is one. No retries: wrap calls
 * (e.g. Resilience4j) where 429/5xx handling matters.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = AtlassianProperties.PREFIX, name = {"base-url", "email", "api-token"})
@EnableConfigurationProperties(AtlassianProperties.class)
public class AtlassianAutoConfiguration {

    static <T> T client(Class<T> type, String product, AtlassianProperties properties, ObjectProvider<RestClient.Builder> builders) {
        RestClient rest = builders.getIfAvailable(RestClient::builder).clone()
                .baseUrl(properties.url(product))
                .defaultHeaders(headers -> headers.setBasicAuth(properties.email(), properties.apiToken()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(rest)).build().createClient(type);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(JiraClient.class)
    @ConditionalOnBooleanProperty(name = AtlassianProperties.PREFIX + ".jira.enabled", matchIfMissing = true)
    static class Jira {
        @Bean
        @ConditionalOnMissingBean
        JiraClient jiraClient(AtlassianProperties properties, ObjectProvider<RestClient.Builder> builders) {
            return client(JiraClient.class, "jira", properties, builders);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ConfluenceClient.class)
    @ConditionalOnBooleanProperty(name = AtlassianProperties.PREFIX + ".confluence.enabled", matchIfMissing = true)
    static class Confluence {
        @Bean
        @ConditionalOnMissingBean
        ConfluenceClient confluenceClient(AtlassianProperties properties, ObjectProvider<RestClient.Builder> builders) {
            return client(ConfluenceClient.class, "confluence", properties, builders);
        }
    }
}
