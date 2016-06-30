package com.airwatch.tool;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by manishk on 6/27/16.
 */
public class LoadWorker extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadWorker.class);
    private static final Map<String, HttpClient> clients = new HashMap<>();

    @Override
    public void start() {
        vertx.setPeriodic(100, doNothing -> {
            if (HttpServer.testStarted.get() && HttpServer.openConnections.get() < HttpServer.maxOpenConnections.get()) {
                for (int index = 0; index < 70; index++) {
                    sendRequestToRemote();
                }
            }
        });
        vertx.setPeriodic(200, doNothing -> {
            if (HttpServer.testStarted.get() && HttpServer.openConnections.get() < HttpServer.maxOpenConnections.get()) {
                for (int index = 0; index < 120; index++) {
                    sendRequestToRemote();
                }
            }
        });

        vertx.setPeriodic(300, doNothing -> {
            if (HttpServer.testStarted.get() && HttpServer.openConnections.get() < HttpServer.maxOpenConnections.get()) {
                for (int index = 0; index < 180; index++) {
                    sendRequestToRemote();
                }
            }
        });
    }

    private void sendRequestToRemote() {
        HttpServer.openConnections.incrementAndGet();
        HttpServer.totalRequests.incrementAndGet();
        int random = 1 + (int) (Math.random() * ((HttpServer.remotePaths.size() - 1) + 1));
        String uriPath = HttpServer.remotePaths.getString(random - 1);
        String uriPathLowercase = uriPath.toLowerCase(Locale.ENGLISH);
        final boolean ping = uriPathLowercase.contains("cmd=ping");
        final boolean sync = uriPathLowercase.contains("cmd=sync");
        if (ping) {
            HttpServer.totalPingCount.incrementAndGet();
            HttpServer.openPingCount.incrementAndGet();
        } else if (sync) {
            HttpServer.totalSyncCount.incrementAndGet();
            HttpServer.openSyncCount.incrementAndGet();
        } else {
            HttpServer.totalItemOperationsCount.incrementAndGet();
            HttpServer.openItemOperationsCount.incrementAndGet();
        }
        URI uri = URI.create(uriPath);

        HttpClientRequest clientRequest;
        String path = uri.getPath() + "?" + uri.getQuery();
        if ("OPTIONS".equalsIgnoreCase(HttpServer.remoteMethod)) {
            clientRequest = createClient(uri).options(path);
        } else {
            clientRequest = createClient(uri).post(path);
        }
        clientRequest.toObservable().subscribe(httpClientResponse -> {
            checkResponse(httpClientResponse);
            // Got response from email server.
            HttpServer.openConnections.decrementAndGet();
            decrementCommandCount(ping, sync);
        }, ex -> {
            decrementCommandCount(ping, sync);
            countError(ex);
            LOGGER.error("Error while connecting to remote host", ex);
        });
        clientRequest.setChunked(true).end();
    }

    private void checkResponse(HttpClientResponse httpClientResponse) {
        if (httpClientResponse.statusCode() != 200) {
            String statusCode = httpClientResponse.statusCode() + "";
            AtomicLong non200Response = HttpServer.non200Response.get(statusCode);
            if (non200Response == null) {
                non200Response = new AtomicLong(0);
            }
            non200Response.incrementAndGet();
            HttpServer.non200Response.put(statusCode, non200Response);
        } else {
            HttpServer.successCount.incrementAndGet();
        }
    }

    private void countError(Throwable ex) {
        String errorMessage = ex.getMessage();
        if (errorMessage == null) {
            errorMessage = ex.getLocalizedMessage();
        }
        AtomicLong errorTypeCount = HttpServer.errorsType.get(errorMessage);
        if (errorTypeCount == null) {
            errorTypeCount = new AtomicLong(0);
        }
        errorTypeCount.incrementAndGet();
        HttpServer.errorsType.put(errorMessage, errorTypeCount);

        HttpServer.errorCount.incrementAndGet();
        HttpServer.openConnections.decrementAndGet();
    }

    private HttpClient createClient(final URI uri) {
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
            options.setTrustAll(true);
            options.setVerifyHost(false);
            httpClient = vertx.createHttpClient(options);
            clients.put(hostKey, httpClient);
        }
        return httpClient;
    }

    private void decrementCommandCount(final boolean ping, final boolean sync) {
        if (ping) {
            HttpServer.openPingCount.decrementAndGet();
        } else if (sync) {
            HttpServer.openSyncCount.decrementAndGet();
        } else {
            HttpServer.openItemOperationsCount.decrementAndGet();
        }
    }
}
