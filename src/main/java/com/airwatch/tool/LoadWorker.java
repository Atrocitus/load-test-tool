package com.airwatch.tool;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get() * rampUpTimeMultiplier) {
                for (int index = 0; index < 70 * rampUpTimeMultiplier; index++) {
                    if (openConnections.get() < maxOpenConnections.get()) {
                        sendPing();
                        sendSync();
                        sendItemOperations();
                    }
                }
            }
        });

        vertx.setPeriodic(200, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get() * rampUpTimeMultiplier) {
                for (int index = 0; index < 120 * rampUpTimeMultiplier; index++) {
                    if (openConnections.get() < maxOpenConnections.get()) {
                        sendPing();
                        sendSync();
                        sendItemOperations();
                    }
                }
            }
        });

        vertx.setPeriodic(300, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get() * rampUpTimeMultiplier) {
                for (int index = 0; index < 180 * rampUpTimeMultiplier; index++) {
                    if (openConnections.get() < maxOpenConnections.get()) {
                        sendPing();
                        sendSync();
                        sendItemOperations();
                    }
                }
            }
        });
    }

    private void sendPing() {
        long percentage = totalPingCount.get() * 100 / totalRequests.get();
        if (percentage < pingPercentage) {
            totalPingCount.incrementAndGet();
            openPingCount.incrementAndGet();
            sendRequestToRemote(CommandType.PING);
        }
    }

    private void sendSync() {
        long percentage = totalSyncCount.get() * 100 / totalRequests.get();
        if (percentage < syncPercentage) {
            totalSyncCount.incrementAndGet();
            openSyncCount.incrementAndGet();
            sendRequestToRemote(CommandType.SYNC);
        }
    }

    private void sendItemOperations() {
        long percentage = totalItemOperationsCount.get() * 100 / totalRequests.get();
        if (percentage < itemOperationPercentage) {
            totalItemOperationsCount.incrementAndGet();
            openItemOperationsCount.incrementAndGet();
            sendRequestToRemote(CommandType.ITEM_OPERATIONS);
        }
    }

    private void sendRequestToRemote(final CommandType commandType) {
        openConnections.incrementAndGet();
        totalRequests.incrementAndGet();

        int random = (int) (Math.random() * ((devices.size() - 1) + 1));
        Device device = devices.get(random);
        StringBuilder builder = new StringBuilder(commandType.serverUriAndCommand).append("DeviceId=").append(device.getEasDeviceId())
                .append("&User=").append(device.getUserId()).append("&DeviceType=").append(device.getEasDeviceType());
        String uriPath = builder.toString();
        String remoteHost = remoteHostsWithPortAndProtocol.getString((int) (Math.random() * ((remoteHostsWithPortAndProtocol.size() - 1) + 1)));
        HttpClientRequest clientRequest;
        clientRequest = ClientUtil.createClient(remoteHost, vertx).post(uriPath);
        clientRequest.toObservable().subscribe(httpClientResponse -> {
            RxSupport.observeBody(httpClientResponse).subscribe(buffer -> {
                // Read the full response otherwise it will cause "Cannot assign requested address" error.
            }, ex -> {

            }, () -> {
                checkResponse(httpClientResponse, remoteHost, commandType);
                // Got response from email server.
                openConnections.decrementAndGet();
                decrementCommandCount(commandType);
            });
        }, ex -> {
            decrementCommandCount(commandType);
            countError(ex);
            LOGGER.error("Error while connecting to remote host", ex);
        });
        clientRequest.headers().addAll(headers);
        clientRequest.end();
    }

    private void checkResponse(HttpClientResponse httpClientResponse, String remoteHost, CommandType commandType) {
        if (httpClientResponse.statusCode() != 200) {
            String statusCodeKey = httpClientResponse.statusCode() + " :: " + remoteHost + " :: " + commandType;
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

    private void decrementCommandCount(final CommandType commandType) {
        switch (commandType) {
            case PING:
                openPingCount.decrementAndGet();
                break;
            case SYNC:
                openSyncCount.decrementAndGet();
                break;
            default:
                openItemOperationsCount.decrementAndGet();

        }
    }

    private enum CommandType {

        SYNC("/Microsoft-Server-ActiveSync?Cmd=Sync&"),
        PING("/Microsoft-Server-ActiveSync?Cmd=Ping&"),
        ITEM_OPERATIONS("/Microsoft-Server-ActiveSync?Cmd=ItemOperations&");

        private String serverUriAndCommand;

        CommandType(String commandUrlParam) {
            this.serverUriAndCommand = commandUrlParam;
        }
    }
}
