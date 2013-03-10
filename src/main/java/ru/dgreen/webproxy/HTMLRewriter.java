/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dgreen.webproxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.htmlparser.jericho.Attributes;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.OutputDocument;
import net.htmlparser.jericho.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dgreen
 */
public class HTMLRewriter {

    private static final Logger logger = LoggerFactory.getLogger(HTMLRewriter.class);
    UrlRewriter rewriter;
    CSSRewriter cssRewriter;
    JSRewriter jsRewriter;

    public HTMLRewriter(UrlRewriter rewriter, CSSRewriter cssRewriter, JSRewriter jsRewriter) {
        this.rewriter = rewriter;
        this.cssRewriter = cssRewriter;
        this.jsRewriter = jsRewriter;
    }

    public InputStream rewriteHtml(InputStream streamFromServer, String encoding, String domain, String scheme) throws IOException {

        Source source = encoding == null ? new Source(streamFromServer) : new Source(new InputStreamReader(streamFromServer, encoding));
        OutputDocument outputDocument = new OutputDocument(source);
        rewriteTags(source, "a", "href", outputDocument);
        rewriteTags(source, "img", "src", outputDocument);
        rewriteTags(source, "form", "action", outputDocument);
        rewriteTags(source, "link", "href", outputDocument);
        rewriteTags(source, "input", "src", outputDocument);
        rewriteTags(source, "meta", "content", outputDocument);
        rewriteInlineJS(source, "onclick", outputDocument, domain, scheme);
        final List<Element> scripts = source.getAllElements("script");
        if (scripts != null) {
            for (Element script : scripts) {
                try {
                    final String scriptSource = script.toString();
                    outputDocument.replace(script, jsRewriter.rewriteJS(scriptSource, domain, scheme));
                } catch (Exception e) {
                    logger.debug("Rewrite failed", e);
                }
            }
        }
        final List<Element> styles = source.getAllElements("style");
        if (styles != null) {
            for (Element style : styles) {
                try {
                    outputDocument.replace(style, cssRewriter.rewriteCSS(style.toString()));
                } catch (Exception e) {
                    logger.debug("Rewrite failed", e);
                }
            }
        }
        final String output = outputDocument.toString();
        final byte[] bytes = output.getBytes();
        streamFromServer = new ByteArrayInputStream(bytes);
        return streamFromServer;
    }

    protected void rewriteTags(Source source, final String elemName, final String targetAttr, OutputDocument outputDocument) {
        final List<Element> anchors = source.getAllElements(elemName);
        if (anchors != null) {
            for (Element anchor : anchors) {
                final Attributes attributes = anchor.getAttributes();
                if (attributes != null) {
                    Map<String, String> attrs = attributes.populateMap(new HashMap<String, String>(), true);
                    final String value = attrs.get(targetAttr);
                    if (value != null) {
                        try {
                            final String localUrl = rewriter.encodeUrl(value);
                            logger.debug("[REWRITE] " + value + " to " + localUrl);
                            attrs.put(targetAttr, localUrl);
                            outputDocument.replace(attributes, attrs);
                        } catch (Exception e) {
                            logger.debug("Rewrite failed", e);
                        }
                    }


                }

            }
        }
    }
    static Pattern inlineJsVariable = Pattern.compile(".*([\"'])(http|https)(://)([^\"'\n\r]*)([\"']).*");

    private void rewriteInlineJS(Source source, final String inlineJSAttrName, OutputDocument outputDocument, String domain, String scheme) {
        final List<Element> inlines = source.getAllElements(inlineJSAttrName, inlineJsVariable);
        for (Element inline : inlines) {
            try {
                outputDocument.replace(inline, jsRewriter.rewriteJS(inline.toString(), domain, scheme));
            } catch (Exception e) {
                logger.debug("Rewrite failed", e);
            }
        }
    }
}
