package com.airwatch.tool;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.airwatch.tool.HttpServer.*;

/**
 * Created by manishk on 7/1/16.
 */
public class SendSinglePolicyUpdate extends AbstractVerticle {

    public static final ConcurrentHashMap<String, AtomicLong> non200Responses = new ConcurrentHashMap();
    public static final ConcurrentHashMap<String, AtomicLong> errorsMessages = new ConcurrentHashMap();
    public static AtomicLong totalSinglePolicyRequests = new AtomicLong(0);
    public static AtomicLong errorCount = new AtomicLong(0);
    public static AtomicLong successCount = new AtomicLong(0);
    public static AtomicLong singlePolicyUpdateOpenConnections = new AtomicLong(0);

    @Override
    public void start() {

        vertx.setPeriodic(1000, doNothing -> {
            if (HttpServer.startSinglePolicyUpdateStarted.get()) {
                for (int index = 0; index < singlePolicyUpdateRequestsPerSecond; index++) {
                    if (totalSinglePolicyRequests.get() < totalSinglePolicyUpdateRequestsToBeMade) {
                        sendRequestToRemote();
                    }
                }
            }
        });

        vertx.setPeriodic(5000, doNothing -> {
            if (HttpServer.startSinglePolicyUpdateStarted.get()) {
                System.out.println("\nSingle Policy Update to remote server = " + singlePolicyUpdateOpenConnections
                        + ". Total requests = " + totalSinglePolicyRequests + ". Success = " + successCount
                        + ". Requests/second = " + singlePolicyUpdateRequestsPerSecond
                        + ". Error = " + errorCount + ". Non 200 response " + non200Responses);
            }
        });
    }

    private void sendRequestToRemote() {
        singlePolicyUpdateOpenConnections.incrementAndGet();
        totalSinglePolicyRequests.incrementAndGet();
        String path = "/segconsole/management.ashx?updatedevicepolicy";

        String remoteHost = singlePolicyUpdateHostsWithPortAndProtocol.getString((int) (Math.random() * ((singlePolicyUpdateHostsWithPortAndProtocol.size() - 1) + 1)));

        HttpClientRequest clientRequest = ClientUtil.createClient(remoteHost, vertx).post(path);
        clientRequest.toObservable().subscribe(httpClientResponse -> {
            checkResponse(httpClientResponse, remoteHost);
            singlePolicyUpdateOpenConnections.decrementAndGet();
        }, ex -> {
            ex.printStackTrace();
            countError(ex);
        });
        String payload = "{\"AllowSync\":null,\"AwDeviceId\":552,\"BrowserUriScheme\":\"awb:\\/\\/\",\"BrowserUriSecureScheme\":\"awbs:\\/\\/\",\"CreatedDateTime\":\"\\/Date(-62135578800000)\\/\",\"DaysSinceLastActivity\":0,\"DeviceIdentifier\":\"BE6330CAE8BC441EBCF65555B9E31114\",\"DevicePolicyLoadEndTime\":\"\\/Date(-62135578800000)\\/\",\"DevicePolicyLoadStartTime\":\"\\/Date(-62135578800000)\\/\",\"DeviceType\":2,\"EasDeviceIdentifier\":\"BE6330CAE8BC441EBCF65555B9E31114\",\"EasProfileInstall\":false,\"FullDeviceIdentifier\":\"BE6330CAE8BC441EBCF65555B9E31114\",\"IsCompromised\":false,\"IsDataProtected\":true,\"IsEnrolled\":true,\"IsManaged\":true,\"IsModelCompliant\":true,\"IsNotMDMCompliant\":false,\"IsOsCompliant\":true,\"MemConfigId\":27,\"MemDeviceId\":7653,\"MobileEmailDiagnosticsEnabled\":false,\"RemoveDevice\":false}";
        JsonObject json = new JsonObject(payload);
        json.put("EasDeviceIdentifier", UUID.randomUUID().toString()); // 10.44.74.88
        clientRequest.setChunked(true).write(json.toString()).end();
    }

    private void countError(Throwable ex) {
        errorCount.incrementAndGet();
        singlePolicyUpdateOpenConnections.decrementAndGet();

        String errorMessage = ex.getMessage();
        if (errorMessage == null) {
            errorMessage = ex.getLocalizedMessage();
        }
        AtomicLong errorTypeCount = errorsMessages.get(errorMessage);
        if (errorTypeCount == null) {
            errorTypeCount = new AtomicLong(0);
        }
        errorTypeCount.incrementAndGet();
        errorsMessages.put(errorMessage, errorTypeCount);
    }

    private void checkResponse(HttpClientResponse httpClientResponse, String remoteHost) {
        if (httpClientResponse.statusCode() != 200) {
            String statusCodeKey = httpClientResponse.statusCode() + " :: " + remoteHost;
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
}
