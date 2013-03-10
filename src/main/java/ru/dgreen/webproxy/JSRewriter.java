/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dgreen.webproxy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author dgreen
 */
public class JSRewriter {

    Logger logger = LoggerFactory.getLogger(JSRewriter.class);
    UrlRewriter rewriter;
    Map<String, List<String>> associatedDomains = new HashMap<String, List<String>>();

    public JSRewriter(UrlRewriter rewriter) {
        this.rewriter = rewriter;
        loadRelatedDomains();
    }
    static Pattern jsVariable = Pattern.compile("([\"'])(http|https)(://)(.*?)(\\1)");
    static Pattern jsStringVar = Pattern.compile("([\"'])(.*?)(\\1)");

    public String rewriteJS(String js, String domain, String scheme) {
        String[] parts = domain.split("[.]");
        if (parts.length > 1) {
            domain = parts[parts.length - 2] + "." + parts[parts.length - 1];
        }

        js = Pattern.compile(domain.replace(".", "[.]"), Pattern.CASE_INSENSITIVE).matcher(js).replaceAll(rewriter.encodeUrlWithoutScheme(domain, scheme));
        final List<String> adoms = associatedDomains.get(domain);
        if (adoms != null) {
            for (String adom : adoms) {
                js = Pattern.compile(adom.replace(".", "[.]"), Pattern.CASE_INSENSITIVE).matcher(js).replaceAll(rewriter.encodeUrlWithoutScheme(adom, scheme));
            }
        }
        return js;

    }

    private void loadRelatedDomains() throws IllegalStateException {
        try {
            Properties properties = new Properties();
            properties.load(JSRewriter.class.getClassLoader().getResourceAsStream("associated.domains.properties"));
            for (Object key : properties.keySet()) {
                final String property = properties.getProperty((String) key);
                String[] doms = property.split(",");
                List<String> assoc = new ArrayList<String>();
                for (String dom : doms) {
                    dom = dom.trim();
                    if (dom.length() > 0) {
                        assoc.add(dom);
                    }
                }
                if (assoc.size() > 0) {
                    this.associatedDomains.put((String) key, assoc);
                    logger.info("[ASSOC] " + (String) key + " " + assoc);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't load properties file", e);
        }
    }
}
