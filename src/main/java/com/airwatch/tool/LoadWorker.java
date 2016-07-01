package com.airwatch.tool;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.Vertx;
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

import static com.airwatch.tool.HttpServer.*;

/**
 * Created by manishk on 6/27/16.
 */
public class LoadWorker extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadWorker.class);
    @Override
    public void start() {
        vertx.setPeriodic(100, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get()) {
                for (int index = 0; index < 70; index++) {
                    sendRequestToRemote();
                }
            }
        });
        vertx.setPeriodic(200, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get()) {
                for (int index = 0; index < 120; index++) {
                    sendRequestToRemote();
                }
            }
        });

        vertx.setPeriodic(300, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get()) {
                for (int index = 0; index < 180; index++) {
                    sendRequestToRemote();
                }
            }
        });
    }

    private void sendRequestToRemote() {
        openConnections.incrementAndGet();
        totalRequests.incrementAndGet();

        // We don't want to rely on random selector as it won't guarantee the equal number of requests for each command type.
        String uriPath = pingURL;
        if (totalPingCount.get() > totalSyncCount.get()) {
            uriPath = syncURL;
        } else if (totalPingCount.get() > totalItemOperationsCount.get()) {
            uriPath = itemOPerationsURL;
        }
        String uriPathLowercase = uriPath.toLowerCase(Locale.ENGLISH);
        final boolean ping = uriPathLowercase.contains("cmd=ping");
        final boolean sync = uriPathLowercase.contains("cmd=sync");
        if (ping) {
            totalPingCount.incrementAndGet();
            openPingCount.incrementAndGet();
        } else if (sync) {
            totalSyncCount.incrementAndGet();
            openSyncCount.incrementAndGet();
        } else {
            totalItemOperationsCount.incrementAndGet();
            openItemOperationsCount.incrementAndGet();
        }
        URI uri = URI.create(uriPath);

        HttpClientRequest clientRequest;
        String path = uri.getPath() + "?" + uri.getQuery();
        if ("OPTIONS".equalsIgnoreCase(remoteMethod)) {
            clientRequest = ClientUtil.createClient(uri, vertx).options(path);
        } else {
            clientRequest = ClientUtil.createClient(uri, vertx).post(path);
        }
        clientRequest.toObservable().subscribe(httpClientResponse -> {
            checkResponse(httpClientResponse);
            // Got response from email server.
            openConnections.decrementAndGet();
            decrementCommandCount(ping, sync);
        }, ex -> {
            decrementCommandCount(ping, sync);
            countError(ex);
            LOGGER.error("Error while connecting to remote host", ex);
        });
        clientRequest.headers().addAll(headers);
        clientRequest.end();
    }

    private void checkResponse(HttpClientResponse httpClientResponse) {
        if (httpClientResponse.statusCode() != 200) {
            String statusCode = httpClientResponse.statusCode() + "";
            AtomicLong non200Response = non200Responses.get(statusCode);
            if (non200Response == null) {
                non200Response = new AtomicLong(0);
            }
            non200Response.incrementAndGet();
            non200Responses.put(statusCode, non200Response);
        } else {
            successCount.incrementAndGet();
        }
    }

    private void countError(Throwable ex) {
        String errorMessage = ex.getMessage();
        if (errorMessage == null) {
            errorMessage = ex.getLocalizedMessage();
        }
        AtomicLong errorTypeCount = errorsType.get(errorMessage);
        if (errorTypeCount == null) {
            errorTypeCount = new AtomicLong(0);
        }
        errorTypeCount.incrementAndGet();
        errorsType.put(errorMessage, errorTypeCount);

        errorCount.incrementAndGet();
        openConnections.decrementAndGet();
    }

    private void decrementCommandCount(final boolean ping, final boolean sync) {
        if (ping) {
            openPingCount.decrementAndGet();
        } else if (sync) {
            openSyncCount.decrementAndGet();
        } else {
            openItemOperationsCount.decrementAndGet();
        }
    }
}
