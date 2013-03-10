/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dgreen.webproxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dgreen
 */
public class ProxyFilter implements Filter {

    public static final Logger logger = LoggerFactory.getLogger(ProxyFilter.class);
    UrlRewriter rewriter;
    CSSRewriter cssRewriter;
    JSRewriter jsRewriter;
    HTMLRewriter htmlRewriter;
    private HttpClient httpClient;

    @Override
    public void init(FilterConfig fc) throws ServletException {
        String domain = fc.getInitParameter("tld");
        int port = Integer.parseInt(fc.getInitParameter("port"));
        int sslPort = Integer.parseInt(fc.getInitParameter("sslPort"));
        rewriter = new UrlRewriter(domain, port, sslPort);
        cssRewriter = new CSSRewriter(rewriter);
        jsRewriter = new JSRewriter(rewriter);
        htmlRewriter = new HTMLRewriter(rewriter, cssRewriter, jsRewriter);

        httpClient = new HttpClient(new MultiThreadedHttpConnectionManager());
        httpClient.getParams().setBooleanParameter(HttpClientParams.USE_EXPECT_CONTINUE, false);
        httpClient.getParams().setCookiePolicy(CookiePolicy.IGNORE_COOKIES);

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain fc) throws IOException, ServletException {

        final HttpServletResponse response = (HttpServletResponse) servletResponse;
        final HttpServletRequest request = (HttpServletRequest) servletRequest;

        final String requestURL = request.getRequestURL().toString();
        logger.info("[REQUEST] " + requestURL);
        if (!rewriter.isEncoded(requestURL)) {
            logger.debug("Skipping " + request.getRequestURL());
            if (request.getRequestURI().equals("/")) {
                if (request.getParameter("url") == null) {
                    fc.doFilter(servletRequest, servletResponse);
                } else {
                    response.sendRedirect(rewriter.encodeUrl(request.getParameter("url")));
                }
            } else {
                response.sendRedirect("/");
            }
        } else {
            try {

                proxy(request, response);

            } catch (Exception e) {
                logger.error("[ERROR] " + requestURL, e);
                response.sendError(500);
            }

        }


    }

    @Override
    public void destroy() {
    }

    private void copyHeaders(HttpServletRequest request, HttpMethod method) {
        Enumeration headers = request.getHeaderNames();
        while (headers.hasMoreElements()) {
            String name = (String) headers.nextElement();
            Enumeration value = request.getHeaders(name);
            while (value.hasMoreElements()) {
                String headerValue = (String) value.nextElement();
                if (name.equalsIgnoreCase("host")) {
                    continue;
                }
                if (name.equalsIgnoreCase("Cookie")) {
                    logger.debug("[GET] [COOKIE] " + headerValue);
                }
                if (rewriter.isEncoded(headerValue)) {
                    logger.debug("[REWRITE] [HEADER] " + name + ": " + "'" + headerValue + "'");
                    method.addRequestHeader(name, rewriter.decodeUrlWithouScheme(headerValue, request.getScheme()));
                } else {
                    method.addRequestHeader(name, headerValue);
                }
            }
        }
    }
    static Pattern cookieDomain = Pattern.compile("(domain=)([^;\\s]+);?", Pattern.CASE_INSENSITIVE);

    private void copyHeader(HttpMethod method, final HttpServletResponse response) {
        Header[] headers = method.getResponseHeaders();

        for (int i = 0; i < headers.length; i++) {
            Header header = headers[i];
            String name = header.getName();
            boolean ignore = name.equalsIgnoreCase("content-length") || name.equalsIgnoreCase("connection") || name.equalsIgnoreCase("transfer-encoding");
            if (!ignore) {
                if (name.equalsIgnoreCase("location")) {
                    final String value = rewriter.encodeUrl(header.getValue());
                    logger.info("[REDIRECT] " + header.getValue() + " rewritten " + value);
                    response.addHeader(name, value);
                } else if (name.equalsIgnoreCase("set-cookie")) {
                    final String value = URLDecoder.decode(header.getValue());

                    Matcher matcher = cookieDomain.matcher(value);
                    StringBuffer buffer = new StringBuffer();
                    while (matcher.find()) {
                        matcher.appendReplacement(buffer, matcher.group(1) + rewriter.encodeCookieDomain(matcher.group(2)) + ";");
                    }
                    matcher.appendTail(buffer);
                    logger.debug("[SET] [COOKIE] " + value + " rewritten " + buffer.toString());
                    response.addHeader(name, buffer.toString());
                } else {
                    response.addHeader(name, header.getValue());
                }
            }

        }
    }

    private void proxy(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        final String requestURL = request.getRequestURL().toString();

        final String rewritten = rewriter.decodeUrl(requestURL);

        logger.debug("Proxing " + requestURL + " to " + rewritten);
        final String targetDomain = new URI(rewritten, true).getHost();
        HttpMethod method = null;
        if (request.getMethod().equalsIgnoreCase("GET")) {
            method = new GetMethod(rewritten) {
                {
                    if (request.getQueryString() != null) {
                        setQueryString(UrlRewriter.fixEncoding(request.getQueryString()));
                    }
                }
            };
        } else if (request.getMethod().equalsIgnoreCase("POST")) {
            method = new PostMethod(rewritten) {
                {
                    setRequestEntity(new InputStreamRequestEntity(request.getInputStream(), request.getContentLength(), request.getContentType()));
                    if (request.getQueryString() != null) {
                        setQueryString(UrlRewriter.fixEncoding(request.getQueryString()));
                    }
                }
            };

        } else {
            response.sendError(405);
        }
        method.setFollowRedirects(false);
        copyHeaders(request, method);
        int result = httpClient.executeMethod(method);
        log(method);
        response.setStatus(result);
        copyHeader(method, response);
        final String contentType = response.getContentType();
        InputStream download;
        OutputStream upload = response.getOutputStream();
        if (contentType != null && isRewritable(contentType)) {
            byte[] body = method.getResponseBody();
            if (body == null) {
                upload.close();
                return;
            }
            download = new ByteArrayInputStream(body);
            final String encoding = response.getHeader("Content-Encoding");
            final boolean gzip = encoding != null && encoding.equalsIgnoreCase("gzip");
            if (gzip) {
                download = new GZIPInputStream(download);
            }
            download = rewrite(targetDomain, request.getScheme(), contentType, download);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            final OutputStream gzos = gzip ? new GZIPOutputStream(bos) : bos;
            proxy(download, gzos);
            response.setContentLength(bos.size());
            download = new ByteArrayInputStream(bos.toByteArray());
        } else {
            download = method.getResponseBodyAsStream();
            int contentLength = -1;
            final Header contentLengthHeader = method.getResponseHeader("Content-Length");
            if (contentLengthHeader != null) {
                contentLength = Integer.parseInt(contentLengthHeader.getValue());
            }
            final Header transferEncoding = method.getResponseHeader("Transfer-Encoding");
            if (transferEncoding != null) {
                response.addHeader("Transfer-Encoding", transferEncoding.getValue());
            }
            if (contentLength > -1) {
                response.setContentLength(contentLength);
            }
        }

        proxy(download, upload);

    }

    private InputStream rewrite(final String domain, String scheme, final String contentType, InputStream streamFromServer) throws IOException {
        if (contentType != null) {
            String encoding = "ISO-8859-1";
            if (contentType.contains("charset=")) {
                String enc = contentType.split("charset=")[1].replaceAll("[^A-Za-z-0-9]", "");
                if (Charset.isSupported(enc)) {
                    encoding = enc;
                }
            }
            if (contentType.contains("html")) {
                logger.debug("[REWRITE] rewriting html");
                streamFromServer = htmlRewriter.rewriteHtml(streamFromServer, encoding, domain, scheme);
            } else if (contentType.contains("css")) {
                logger.debug("[REWRITE] rewriting css");
                streamFromServer = new ByteArrayInputStream(
                        cssRewriter.rewriteCSS(IOUtils.toString(streamFromServer, encoding)).getBytes());
            } else if (contentType.contains("javascript")) {
                logger.debug("[REWRITE] rewriting js");
                streamFromServer = new ByteArrayInputStream(
                        jsRewriter.rewriteJS(IOUtils.toString(streamFromServer, encoding), domain, scheme).getBytes());
            }
        }
        return streamFromServer;
    }

    private void proxy(InputStream streamFromServer, OutputStream responseStream) throws IOException {
        int bytesProxied = 0;
        try {
            responseStream.flush();
            if (streamFromServer != null) {

                byte[] buffer = new byte[65536];
                int read = streamFromServer.read(buffer);
                while (read > 0) {
                    responseStream.write(buffer, 0, read);
                    bytesProxied += read;
                    read = streamFromServer.read(buffer);
                }
                streamFromServer.close();
            }
            responseStream.flush();
            responseStream.close();
        } catch (Exception e) {
            logger.error("[PROXY] [ERROR] bytes proxied " + bytesProxied, e);
            if (streamFromServer != null) {
                try {
                    streamFromServer.close();
                } catch (Exception ignored) {
                }
            }
            try {
                responseStream.flush();
                responseStream.close();
            } catch (Exception ignored) {
            }

        }
    }

    private void log(HttpMethod method) throws URIException {
        final Header contentType = method.getResponseHeader("Content-Type");
        if ((method.getStatusCode() != 200 && method.getStatusCode() != 304) || (contentType != null && contentType.getValue() != null && contentType.getValue().contains("html"))) {
            logger.info(method.getStatusCode() + " " + method.getURI() + "\n" + Arrays.toString(method.getRequestHeaders()) + "\n" + Arrays.toString(method.getResponseHeaders()) + "\n");
        }
    }

    private boolean isRewritable(final String contentType) {
        return contentType.contains("html") || contentType.contains("css") || contentType.contains("javascript");
    }
}
