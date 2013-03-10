/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dgreen.webproxy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dgreen
 */
public class CSSRewriter {

    private static final Logger logger = LoggerFactory.getLogger(CSSRewriter.class);
    UrlRewriter rewriter;

    public CSSRewriter(UrlRewriter rewriter) {
        this.rewriter = rewriter;
    }
    static Pattern cssUrl = Pattern.compile("(url\\([\"']*)(http|https)(://)(.*?)([\"']*\\))");

    public String rewriteCSS(String css) {
        StringBuffer buffer = new StringBuffer();
        final Matcher matcher = cssUrl.matcher(css);
        while (matcher.find()) {
            String url = matcher.group(2) + matcher.group(3) + matcher.group(4);
            String encoded = url;
            try {
                encoded = rewriter.encodeUrl(url);
            } catch (Exception e) {
                logger.debug("Can't rewrite " + url, e);
            }
            logger.debug("[REWRITE] [CSS] " + url + " to " + encoded);
            matcher.appendReplacement(buffer, matcher.group(1) + encoded.replace("\\", "\\\\") + matcher.group(5));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
