package org.dromara.reader.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.reader.domain.ReaderSourceDiscoveryBlacklist;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/** 发现流程共用的 URL、内网地址和黑名单校验。 */
public final class ReaderSourceDiscoveryGuard {

    private ReaderSourceDiscoveryGuard() {
    }

    public static URI validatePublicHttpUri(String rawUrl, String field) {
        if (StringUtils.isBlank(rawUrl)) {
            throw new ServiceException(field + "不能为空");
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme) || StringUtils.isBlank(uri.getHost())
                || uri.getUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443)
                || uri.getFragment() != null) {
                throw new ServiceException(field + "仅允许不携带凭据的 HTTP/HTTPS 公网地址");
            }
            rejectPrivateAddress(uri.getHost(), field);
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(field + "格式不正确");
        }
    }

    public static String normalizeHost(String host) {
        return host == null ? null : host.trim().toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
    }

    public static String normalizeCandidateUrl(URI uri) {
        String path = StringUtils.isBlank(uri.getPath()) ? "/" : uri.getPath();
        String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
        return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + normalizeHost(uri.getHost()) + path + query;
    }

    public static ReaderSourceDiscoveryBlacklist findBlacklist(URI uri,
                                                                List<ReaderSourceDiscoveryBlacklist> rules) {
        String host = normalizeHost(uri.getHost());
        String url = normalizeCandidateUrl(uri);
        return rules.stream().filter(rule -> "1".equals(rule.getStatus()) && matches(rule, host, url)).findFirst().orElse(null);
    }

    public static boolean matches(ReaderSourceDiscoveryBlacklist rule, String host, String normalizedUrl) {
        String value = rule.getMatcherValue() == null ? "" : rule.getMatcherValue().trim().toLowerCase(Locale.ROOT);
        return switch (rule.getMatcherType()) {
            case "HOST" -> host.equals(value);
            case "SUFFIX" -> host.equals(value) || host.endsWith("." + value);
            case "URL" -> normalizedUrl.equals(value);
            default -> false;
        };
    }

    public static void validateMatcher(String matcherType, String matcherValue) {
        if (!List.of("HOST", "SUFFIX", "URL").contains(matcherType)) {
            throw new ServiceException("黑名单匹配类型只能是 HOST、SUFFIX 或 URL");
        }
        if (StringUtils.isBlank(matcherValue) || matcherValue.length() > 1000) {
            throw new ServiceException("黑名单匹配值不能为空且不能超过 1000 个字符");
        }
        if ("HOST".equals(matcherType) || "SUFFIX".equals(matcherType)) {
            String value = matcherValue.trim().toLowerCase(Locale.ROOT).replaceFirst("^\\*\\.", "");
            if (!value.matches("[a-z0-9](?:[a-z0-9.-]{0,253}[a-z0-9])?")) {
                throw new ServiceException("黑名单主机格式不正确");
            }
        }
    }

    private static void rejectPrivateAddress(String host, String field) {
        String normalized = normalizeHost(host);
        if ("localhost".equals(normalized) || normalized.endsWith(".localhost") || normalized.endsWith(".local")) {
            throw new ServiceException(field + "不允许访问本地主机");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || "169.254.169.254".equals(address.getHostAddress())) {
                    throw new ServiceException(field + "不允许访问内网、保留地址或云元数据地址");
                }
            }
        } catch (UnknownHostException ex) {
            throw new ServiceException(field + "主机无法解析");
        }
    }
}
