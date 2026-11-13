package com.anode.atlassian.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Atlassian Cloud site and account; the API token comes from the environment (ANODE_ATLASSIAN_API_TOKEN).
 *
 * @param baseUrl site URL, e.g. https://your-domain.atlassian.net
 * @param email account the API token belongs to
 * @param apiToken API token of that account
 * @param cloudId site's cloud id, for scoped API tokens: they are only accepted through the gateway
 *     ({@code <gateway-url>/ex/jira|confluence/<cloud-id>}), unscoped ones call the site directly
 * @param gatewayUrl Atlassian API gateway
 * @param jira Jira client
 * @param confluence Confluence client
 */
@ConfigurationProperties(AtlassianProperties.PREFIX)
public record AtlassianProperties(String baseUrl, String email, String apiToken, String cloudId,
                                  @DefaultValue("https://api.atlassian.com") String gatewayUrl, @DefaultValue Product jira, @DefaultValue Product confluence) {

    public static final String PREFIX = "anode.atlassian";

    /** @param enabled whether its client is configured */
    public record Product(@DefaultValue("true") boolean enabled) {}

    /** Where {@code product} ({@code jira}, {@code confluence}) is called: the site, or the gateway with a cloud id. */
    String url(String product) {
        String url = cloudId == null || cloudId.isBlank() ? baseUrl : gatewayUrl + "/ex/" + product + "/" + cloudId.strip();
        return url.replaceAll("/+$", "");
    }

    /** Never shows the token. */
    @Override
    public String toString() {
        return "AtlassianProperties[" + baseUrl + " as " + email + "]";
    }
}
