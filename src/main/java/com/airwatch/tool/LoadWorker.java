package com.airwatch.tool;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.airwatch.tool.HttpServer.*;

/**
 * Created by manishk on 6/27/16.
 */
public class LoadWorker extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadWorker.class);

    private List<Long> timers = new ArrayList<>();

    @Override
    public void start() {
        vertx.eventBus().consumer("scheduleTest", event -> {
            LoadTestType testType = LoadTestType.valueOf(event.body().toString());
            if (testType == LoadTestType.REQUEST_PER_SECOND) {
                scheduleRequestsPerSecondTimer();
            } else {
                scheduleUsersBaseTimer();
            }
        });

        vertx.eventBus().consumer("stopTests", event -> {
            timers.forEach(id -> {
                vertx.cancelTimer(id);
            });
        });
    }

    private void scheduleUsersBaseTimer() {
        timers.add(vertx.setPeriodic(100, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get() * rampUpTimeMultiplier) {
                for (int index = 0; index < 70 * rampUpTimeMultiplier; index++) {
                    if (openConnections.get() < maxOpenConnections.get()) {
                        sendPingWithPercentage();
                        sendSyncWithPercentage();
                        sendItemOperationsWithPercentage();
                    }
                }
            }
        }));

        timers.add(vertx.setPeriodic(200, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get() * rampUpTimeMultiplier) {
                for (int index = 0; index < 120 * rampUpTimeMultiplier; index++) {
                    if (openConnections.get() < maxOpenConnections.get()) {
                        sendPingWithPercentage();
                        sendSyncWithPercentage();
                        sendItemOperationsWithPercentage();
                    }
                }
            }
        }));

        timers.add(vertx.setPeriodic(300, doNothing -> {
            if (testStarted.get() && openConnections.get() < maxOpenConnections.get() * rampUpTimeMultiplier) {
                for (int index = 0; index < 180 * rampUpTimeMultiplier; index++) {
                    if (openConnections.get() < maxOpenConnections.get()) {
                        sendPingWithPercentage();
                        sendSyncWithPercentage();
                        sendItemOperationsWithPercentage();
                    }
                }
            }
        }));
    }

    private void scheduleRequestsPerSecondTimer() {

        timers.add(vertx.setPeriodic(1000, doNothing -> {
            for (int index = 0; index < pingRequestsPerSecond * rampUpTimeMultiplier; index++) {
                sendPing();
            }
        }));

        timers.add(vertx.setPeriodic(1000, doNothing -> {
            for (int index = 0; index < syncRequestsPerSecond * rampUpTimeMultiplier; index++) {
                sendSync();
            }
        }));

        timers.add(vertx.setPeriodic(1000, doNothing -> {
            for (int index = 0; index < itemOperationRequestsPerSecond * rampUpTimeMultiplier; index++) {
                sendItemOperations();
            }
        }));
    }

    private void sendPingWithPercentage() {
        long percentage = totalPingCount.get() * 100 / totalRequests.get();
        if (percentage < pingPercentage) {
            sendPing();
        }
    }

    private void sendSyncWithPercentage() {
        long percentage = totalSyncCount.get() * 100 / totalRequests.get();
        if (percentage < syncPercentage) {
            sendSync();
        }
    }

    private void sendItemOperationsWithPercentage() {
        long percentage = totalItemOperationsCount.get() * 100 / totalRequests.get();
        if (percentage < itemOperationPercentage) {
            sendItemOperations();
        }
    }

    private void sendPing() {
        totalPingCount.incrementAndGet();
        openPingCount.incrementAndGet();
        sendRequestToRemote(CommandType.PING);
    }

    private void sendSync() {
        totalSyncCount.incrementAndGet();
        openSyncCount.incrementAndGet();
        sendRequestToRemote(CommandType.SYNC);
    }

    private void sendItemOperations() {
        totalItemOperationsCount.incrementAndGet();
        openItemOperationsCount.incrementAndGet();
        sendRequestToRemote(CommandType.ITEM_OPERATIONS);
    }

    private void sendRequestToRemote(final CommandType commandType) {
        openConnections.incrementAndGet();
        totalRequests.incrementAndGet();

        int random = (int) (Math.random() * ((devices.size() - 1) + 1));
        Device device = devices.get(random);
        StringBuilder builder = new StringBuilder().append("/Microsoft-Server-ActiveSync?")
                .append(commandType.getCommand()).append("DeviceId=").append(device.getEasDeviceId())
                .append("&DeviceType=").append(device.getEasDeviceType());
        try {
            builder.append("&User=").append(URLEncoder.encode(device.getUserId(), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String remoteHost = remoteHostsWithPortAndProtocol.getString((int) (Math.random() * ((remoteHostsWithPortAndProtocol.size() - 1) + 1)));
        HttpClientRequest clientRequest;
        clientRequest = ClientUtil.createClient(remoteHost, vertx).post(builder.toString());
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
}
