package com.airwatch.tool;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

import static com.airwatch.tool.HttpServer.*;

/**
 * Created by manishk on 6/27/16.
 */
public class LoadWorker extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadWorker.class);
    private static String pingURL = "/Microsoft-Server-ActiveSync?Cmd=Ping&";
    private static String syncURL = "/Microsoft-Server-ActiveSync?Cmd=Sync&";
    private static String itemOPerationsURL = "/Microsoft-Server-ActiveSync?Cmd=ItemOperations&";

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
        int random = (int) (Math.random() * ((1000 - 1) + 1));
        Device device = devices.get(random);
        StringBuilder builder = new StringBuilder(uriPath).append("DeviceId=").append(device.getEasDeviceId())
                .append("&User=").append(device.getUserId()).append("&DeviceType=").append(device.getEasDeviceType());
        uriPath = builder.toString();
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
        String remoteHost = remoteHostsWithPortAndProtocol.getString((int) (Math.random() * ((remoteHostsWithPortAndProtocol.size() - 1) + 1)));

        HttpClientRequest clientRequest;
        if ("OPTIONS".equalsIgnoreCase(remoteMethod)) {
            clientRequest = ClientUtil.createClient(remoteHost, vertx).options(uriPath);
        } else {
            clientRequest = ClientUtil.createClient(remoteHost, vertx).post(uriPath);
        }
        clientRequest.toObservable().subscribe(httpClientResponse -> {
            RxSupport.observeBody(httpClientResponse).subscribe(buffer -> {
                // Read the full response otherwise it will cause "Cannot assign requested address" error.
            }, ex -> {

            }, () -> {
                checkResponse(httpClientResponse, remoteHost, ping, sync);
                // Got response from email server.
                openConnections.decrementAndGet();
                decrementCommandCount(ping, sync);
            });
        }, ex -> {
            decrementCommandCount(ping, sync);
            countError(ex);
            LOGGER.error("Error while connecting to remote host", ex);
        });
        clientRequest.headers().addAll(headers);
        clientRequest.end();
    }

    private void checkResponse(HttpClientResponse httpClientResponse, String remoteHost, boolean ping, boolean sync) {
        String requestType = ping ? "PING" : sync ? "SYNC" : "ItemOperation";
        if (httpClientResponse.statusCode() != 200) {
            String statusCodeKey = httpClientResponse.statusCode() + " :: " + remoteHost + " :: " + requestType;
            AtomicLong non200Response = non200Responses.get(statusCodeKey);
            if (non200Response == null) {
                non200Response = new AtomicLong(0);
            }
            non200Response.incrementAndGet();
            non200Responses.put(statusCodeKey, non200Response);
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
