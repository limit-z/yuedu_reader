package org.dromara.reader.service.impl;

import org.dromara.reader.domain.ReaderSourceDiscoveryBlacklist;
import org.dromara.reader.support.ReaderAllEnvTest;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ReaderAllEnvTest
class ReaderSourceDiscoveryGuardTest {

    @Test
    void suffixRuleShouldMatchOnlyTheHostAndItsSubdomains() {
        ReaderSourceDiscoveryBlacklist rule = rule("SUFFIX", "example.com");

        assertTrue(ReaderSourceDiscoveryGuard.matches(rule, "example.com", "https://example.com/"));
        assertTrue(ReaderSourceDiscoveryGuard.matches(rule, "sub.example.com", "https://sub.example.com/"));
        assertFalse(ReaderSourceDiscoveryGuard.matches(rule, "badexample.com", "https://badexample.com/"));
    }

    @Test
    void exactUrlRuleShouldNotBlockAnotherPathOrHost() {
        ReaderSourceDiscoveryBlacklist rule = rule("URL", "https://example.com/official");

        assertTrue(ReaderSourceDiscoveryGuard.matches(rule, "example.com", "https://example.com/official"));
        assertFalse(ReaderSourceDiscoveryGuard.matches(rule, "example.com", "https://example.com/official/next"));
        assertFalse(ReaderSourceDiscoveryGuard.matches(rule, "other.example.com", "https://other.example.com/official"));
    }

    @Test
    void privateAddressShouldBeRejectedBeforeAnyRequest() {
        org.junit.jupiter.api.Assertions.assertThrows(org.dromara.common.core.exception.ServiceException.class,
            () -> ReaderSourceDiscoveryGuard.validatePublicHttpUri("http://127.0.0.1/feed.txt", "发现源地址"));
    }

    @Test
    void candidateUrlShouldBeNormalizedWithoutFragment() {
        URI uri = URI.create("HTTPS://Example.com/path?q=1");

        String normalized = ReaderSourceDiscoveryGuard.normalizeCandidateUrl(uri);

        assertNotNull(normalized);
        assertTrue(normalized.startsWith("https://example.com/path"));
        assertFalse(normalized.contains("#"));
    }

    private ReaderSourceDiscoveryBlacklist rule(String type, String value) {
        ReaderSourceDiscoveryBlacklist rule = new ReaderSourceDiscoveryBlacklist();
        rule.setMatcherType(type);
        rule.setMatcherValue(value);
        rule.setStatus("1");
        return rule;
    }
}
