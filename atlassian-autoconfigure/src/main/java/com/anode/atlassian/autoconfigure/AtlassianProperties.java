package com.anode.atlassian.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Atlassian Cloud site and account; the API token comes from the environment (ANODE_ATLASSIAN_API_TOKEN).
 *
 * @param baseUrl site URL, e.g. https://your-domain.atlassian.net
 * @param email account the API token belongs to
 * @param apiToken API token of that account
 * @param jira Jira client
 * @param confluence Confluence client
 */
@ConfigurationProperties(AtlassianProperties.PREFIX)
public record AtlassianProperties(String baseUrl, String email, String apiToken, @DefaultValue Product jira, @DefaultValue Product confluence) {

    public static final String PREFIX = "anode.atlassian";

    /** @param enabled whether its client is configured */
    public record Product(@DefaultValue("true") boolean enabled) {}

    /** Never shows the token. */
    @Override
    public String toString() {
        return "AtlassianProperties[" + baseUrl + " as " + email + "]";
    }
}
