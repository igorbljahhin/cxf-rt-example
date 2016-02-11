package com.example.client;

import com.example.rt.VersionResource;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxrs.client.ClientConfiguration;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduit;
import org.apache.cxf.transport.http.asyncclient.AsyncHTTPConduitFactory;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.util.*;

@Component("servicesClient")
public class ServicesClient {
    public static final String API_URI_AUTH = "oauth/token";
    public static final String API_URI_VERSION = "api/version";
    private final static Logger logger = LoggerFactory.getLogger(ServicesClient.class);
    @Value("${services.url:http://localhost:8081/cxf-rt-server}")
    private String url;
    @Value("${services.clientId:}")
    private String clientId;
    @Value("${services.clientSecret:}")
    private String clientSecret;
    @Value("${services.preAuthorizedToken:}")
    private String preAuthorizedToken;
    @Value("${services.http.maxConnectionCount:100}")
    private int maxConnectionCount = 100;
    @Value("${services.http.maxConnectionCountPerHost:100}")
    private int maxConnectionCountPerHost = 100;
    @Value("${services.http.connectionTTL:300000}")
    private long connectionTTL = 300000;
    @Value("${services.http.soTimeout:10000}")
    private long soTimeout = 10000;
    private String token;
    private VersionResource versionResource;
    private boolean initialized;

    private static void setContext(HttpServletRequest request) {
        if (request != null) {
            try {
                final Map<String, Object> context = new HashMap<>();
                final Enumeration e = request.getHeaderNames();

                while (e.hasMoreElements()) {
                    final String name = (String) e.nextElement();
                    context.put(name, request.getHeader(name));
                }

                logger.debug("Client context is set to: {}", context.toString());

                ContextMediator.set(context);
            } catch (Throwable t) {
                logger.debug("Couldn't set context {}", t.getMessage());
            }
        }
    }

    private static List<JacksonJsonProvider> getProviders() {
        final List<JacksonJsonProvider> providers = new ArrayList<>();

        final JacksonJsonProvider jsonProvider = new JacksonJsonProvider();
        jsonProvider.configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        providers.add(jsonProvider);

        return providers;
    }

    public VersionResource getVersionResource(final HttpServletRequest request) {
        if (!initialized) {
            initProxies();
        }

        setContext(request);

        return this.versionResource;
    }

    private String getForwardingAgent() {
        final String result = getForwardingHeader(HttpHeaders.USER_AGENT);

        return (result != null && result.isEmpty()) ? "Server-to-server Agent" : result;
    }

    private String getForwardingHeader(String headerName) {
        String result = null;

        try {
            final Map<String, Object> context = ContextMediator.get();

            if (context != null && (context.containsKey(headerName) || context.containsKey(headerName.toLowerCase()))) {
                result = (String) context.get(headerName);

                if (result == null) {
                    result = (String) context.get(headerName.toLowerCase());
                }

                logger.debug("Found in context {}: {}", headerName, result);
            } else {
                logger.debug("No {} in context: {}", headerName, (context == null ? null : context.toString()));
            }
        } catch (Throwable t) {
            logger.debug("Error extracting {} header", headerName);
        }

        return result;
    }

    protected void initProxies() {
        final String formattedUrl = this.url.trim();

        initAccessToken();

        final Bus bus = BusFactory.getDefaultBus();
        bus.setProperty(AsyncHTTPConduit.USE_ASYNC, Boolean.TRUE);

        final Map<String, Object> settings = new HashMap<String, Object>() {{
            put(AsyncHTTPConduitFactory.SO_TIMEOUT, soTimeout);
            put(AsyncHTTPConduitFactory.CONNECTION_TTL, connectionTTL);
            put(AsyncHTTPConduitFactory.MAX_CONNECTIONS, maxConnectionCount);
            put(AsyncHTTPConduitFactory.MAX_PER_HOST_CONNECTIONS, maxConnectionCountPerHost);
        }};

        versionResource = initResourceProxy(formattedUrl, API_URI_VERSION, VersionResource.class, settings);

        initialized = true;
    }

    private <T> T initResourceProxy(String url, String path, Class<T> clazz, Map<String, Object> settings) {
        final WebClient webClient = WebClient.create(url.endsWith("/") ? url + path : url + "/" + path, getProviders(), true);

        if (token != null) {
            webClient.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        T t = JAXRSClientFactory.fromClient(webClient, clazz, true);
        final ClientConfiguration config = WebClient.getConfig(t);

        final Interceptor i = new AbstractPhaseInterceptor(Phase.POST_PROTOCOL) {
            @Override
            public void handleMessage(Message message) throws Fault {
                final Map<String, List> headers = (Map<String, List>) message.get(Message.PROTOCOL_HEADERS);

                if (headers != null) {
                    try {
                        headers.put(HttpHeaders.AUTHORIZATION, Collections.singletonList("Bearer " + token));
                        headers.put(HttpHeaders.USER_AGENT, Collections.singletonList("" + getForwardingAgent()));

                        final String accept = getForwardingHeader(HttpHeaders.ACCEPT);

                        if (accept != null && accept.isEmpty()) {
                            headers.put(HttpHeaders.ACCEPT, Collections.singletonList(accept));
                        }
                    } catch (Exception ce) {
                        throw new Fault(ce);
                    }
                }
            }

            @Override
            public void handleFault(Message message) {
            }
        };

        if (config.getOutInterceptors() != null) {
            config.getOutInterceptors().add(i);
        } else {
            config.setOutInterceptors(new ArrayList<Interceptor<? extends Message>>() {{
                add(i);
            }});
        }

        final AsyncHTTPConduit conduit = (AsyncHTTPConduit) config.getConduit();
        HTTPClientPolicy policy = conduit.getClient();

        if (policy == null) {
            policy = new HTTPClientPolicy();
            conduit.setClient(policy);
        }

        policy.setAllowChunking(true);
        policy.setChunkingThreshold(16384);
        policy.setReceiveTimeout(120000);
        policy.setConnectionTimeout(10000);

        final AsyncHTTPConduitFactory factory = conduit.getAsyncHTTPConduitFactory();
        factory.update(settings);

        final AspectJProxyFactory afactory = new AspectJProxyFactory(t);
        afactory.addInterface(clazz);

        t = (T) afactory.getProxy();
        return t;
    }

    private void initAccessToken() {
        if (!preAuthorizedToken.isEmpty()) {
            token = preAuthorizedToken;
        } else if (!clientId.isEmpty()) {
            try {
                this.token = getAccessToken(clientId, clientSecret);
            } catch (Exception ex) {
                logger.error("SC:Error while get access token", ex);

                throw new RuntimeException("Can't get services client token;" + ex.getMessage());
            }
        }
    }

    private String getAccessToken(String clientId, String clientSecret) throws IOException {
        final DefaultHttpClient httpclient = new DefaultHttpClient();

        try {
            final URL serviceUrl = new URL(url);
            final HttpHost targetHost = new HttpHost(serviceUrl.getHost(), serviceUrl.getPort());
            httpclient.getCredentialsProvider().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(clientId, clientSecret));

            final AuthCache authCache = new BasicAuthCache();

            final BasicScheme basicAuth = new BasicScheme();
            authCache.put(targetHost, basicAuth);

            final BasicHttpContext localcontext = new BasicHttpContext();
            localcontext.setAttribute(ClientContext.AUTH_CACHE, authCache);

            final HttpPost httpget = new HttpPost(serviceUrl.getPath() + "/" + API_URI_AUTH);

            final List<NameValuePair> nvps = new ArrayList<>();
            nvps.add(new BasicNameValuePair("grant_type", "client_credentials"));

            httpget.setEntity(new UrlEncodedFormEntity(nvps));

            final HttpResponse response = httpclient.execute(targetHost, httpget, localcontext);
            final HttpEntity entity = response.getEntity();
            final String responseStr = EntityUtils.toString(entity);
            final Map<String, String> respJSON = (Map<String, String>) new ObjectMapper().readValue(responseStr, Map.class);

            token = respJSON.get("access_token");
        } catch (IOException e) {
            logger.error("SC: can't get token", e);
            throw e;
        } finally {
            httpclient.getConnectionManager().shutdown();
        }

        return token;
    }
}
