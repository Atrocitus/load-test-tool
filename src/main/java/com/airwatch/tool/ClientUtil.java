package com.airwatch.tool;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpClient;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by manishk on 7/1/16.
 */
public class ClientUtil {

    private static final Map<String, HttpClient> clients = new HashMap<>();

    public static HttpClient createClient(final URI uri, final Vertx vertx) {
        String hostKey = uri.getHost() + uri.getPort();
        HttpClient httpClient = clients.get(hostKey);
        if (httpClient == null) {
            boolean secure = StringUtils.equalsIgnoreCase(uri.getScheme(), "https");
            HttpClientOptions options = new HttpClientOptions()
                    .setDefaultHost(uri.getHost())
                    .setSsl(secure)
                    .setConnectTimeout(900000)
                    .setMaxPoolSize(50000)
                    .setTryUseCompression(true);
            // If port isn't set then Vertx uses default port as 80 - which will cause issues.
            if (uri.getPort() == -1) {
                options.setDefaultPort(secure ? 443 : 80);
            } else {
                options.setDefaultPort(uri.getPort());
            }
            options.setTrustAll(true).setKeepAlive(true);
            options.setVerifyHost(false);
            httpClient = vertx.createHttpClient(options);
            clients.put(hostKey, httpClient);
        }
        return httpClient;
    }

}
