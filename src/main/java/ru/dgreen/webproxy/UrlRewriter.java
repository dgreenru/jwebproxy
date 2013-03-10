/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dgreen.webproxy;

import java.net.URLDecoder;
import java.net.URLEncoder;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dgreen
 */
public class UrlRewriter {

    private static final Logger logger = LoggerFactory.getLogger(UrlRewriter.class);
    final String tld;
    final int port;
    final int sslPort;

    public UrlRewriter(String tld, int port, int sslPort) {
        this.tld = tld;
        this.port = port;
        this.sslPort = sslPort;
    }

    public String encodeCookieDomain(String domain) {
        boolean dotted = domain.startsWith(".");
        final String host32 = encodeUrlWithoutScheme(dotted ? domain.substring(1) : domain, "http").replaceFirst(":[0-9]+$", "");
        return dotted ? "." + host32 : host32;
    }

    public String encodeUrlWithoutScheme(String link, String scheme) {
        boolean http = link.startsWith("http");
        String encoded = encodeUrl(http ? link : scheme + "://" + link);
        if (!http) {
            encoded = encoded.substring(scheme.length() + 3);
        }
        return encoded;
    }

    public String decodeUrlWithouScheme(String link, String scheme) {
        boolean http = link.startsWith("http");
        String decoded = decodeUrl(http ? link : scheme + "://" + link);
        if (!http) {
            decoded = decoded.substring(scheme.length() + 3);
        }
        return decoded;
    }

    public String encodeUrl(String link) {
        try {
            URI uri = new URI(fixEncoding(link), true);
            if (uri.isRelativeURI()) {
                return link;
            } else {
                return encodeUri(uri, link);
            }
        } catch (Exception ex) {
            logger.debug("Can't rewrite " + link + " with " + tld, ex);
            return link;
        }
    }

    public boolean isEncoded(String link) {
        return link.contains(tld);
    }

    public String decodeUrl(String link) {
        try {
            URI uri = new URI(fixEncoding(link), true);
            if (uri.isRelativeURI()) {
                return link;
            } else {
                return decodeUri(uri, link);
            }
        } catch (Exception ex) {
            logger.debug("Can't rewrite " + link + " with tld " + tld, ex);
            return link;
        }
    }

    public static String fixEncoding(String link) {
        return link == null ? null : link.replace("|", "%7C");
    }

    private String decodeUri(URI uri, String link) throws URIException {
        final String host = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        if (host.contains(tld)) {
            String target = host.substring(0, host.indexOf(tld));
            return link.replaceFirst(host, target);
        } else {
            return link;
        }
    }

    private String encodeUri(URI uri, String link) throws URIException {
        String host = uri.getHost();
        if (!host.contains(".") || host.endsWith(".")) {
            return link;
        }
        String replacement = host + tld;
        String qs = uri.getQuery();
        if (qs != null) {
            String qsrw = rewriteQueryString(uri);
            if (!qs.equals(qsrw)) {
                link = link.replace(qs, qsrw);
            }
        }
        rewriteQueryString(uri);
        if (uri.getScheme().equalsIgnoreCase("https") && sslPort != 443) {
            replacement = replacement + ":" + sslPort;
        } else if (uri.getScheme().equalsIgnoreCase("http") && port != 80) {
            replacement = replacement + ":" + port;
        }
        return link.replaceFirst(uri.getHost(), replacement);
    }

    public String rewriteQueryString(URI uri) throws URIException {
        if (uri.getQuery() != null) {
            StringBuilder rebuilded = new StringBuilder();
            for (String param : uri.getQuery().split("&")) {
                String pair[] = param.split("=");
                if (rebuilded.length() > 0) {
                    rebuilded.append("&");
                }
                rebuilded.append(pair[0]);
                String value = "";
                if (pair.length > 1) {
                    value = URLDecoder.decode(pair[1]);
                    value = URLEncoder.encode(encodeUrl(value));
                }
                rebuilded.append("=");
                rebuilded.append(value);
            }
            return rebuilded.toString();
        }
        return uri.getQuery();
    }
}