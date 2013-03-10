JWebProxy
=========
JWebProxy is a java.servlet.Filter based web proxy which you can execute using any servlet container that you want. 
Tomcat or Jetty for example.

To start using proxy you should get dns record with wildcard. I have \*.proxy.highload.net.
Also you should generate ssl cert for you server if you want to proxy https pages. 
So you should write down your settings in **src/main/webapp/WEB-INF/web.xml** file and configure you servlet container to start on specified ports. I start tomcat using _authbind_ package on privileged ports 80 and 443. All should works good on ports 8080 and 8443 too. 

Proxied urls will looks like **http://www.google.com.proxy.highload.net/**. 

If you want this proxy to fully support some site you should manually debug it with browser and append lines to **associated.domains.properties** or fix logick in any **\*Rewriter** class. If you have more complex problems you should find solution in **ProxyFilter** class.
