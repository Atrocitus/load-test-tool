package com.airwatch.tool;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

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

        vertx.setPeriodic(500, doNothing -> {
            if (HttpServer.startSinglePolicyUpdateStarted.get() && singlePolicyUpdateOpenConnections.get() < HttpServer.singlePolicyUpdateMaxConnections.get()) {
                for (int index = 0; index < 30; index++) {
                    sendRequestToRemote();
                }
            }
        });
        vertx.setPeriodic(800, doNothing -> {
            if (HttpServer.startSinglePolicyUpdateStarted.get() && singlePolicyUpdateOpenConnections.get() < HttpServer.singlePolicyUpdateMaxConnections.get()) {
                for (int index = 0; index < 50; index++) {
                    sendRequestToRemote();
                }
            }
        });

        vertx.setPeriodic(5000, doNothing -> {
            System.out.println("Open connections to remote server = " + singlePolicyUpdateOpenConnections
                    + ". Total requests = " + totalSinglePolicyRequests + ". Success = " + successCount
                    + ". Error = " + errorCount + ". Non 200 response " + non200Responses);
        });

    }

    private void sendRequestToRemote() {
        singlePolicyUpdateOpenConnections.incrementAndGet();
        totalSinglePolicyRequests.incrementAndGet();
        String path = "/segconsole/management.ashx?updatedevicepolicy";
        URI uri = URI.create(HttpServer.remoteHost + path);
        HttpClientRequest clientRequest = ClientUtil.createClient(uri, vertx).post(path);
        clientRequest.toObservable().subscribe(httpClientResponse -> {
            checkResponse(httpClientResponse);
            singlePolicyUpdateOpenConnections.decrementAndGet();
        }, ex -> {
            countError(ex);
        });
        clientRequest.setChunked(true).write("{\"AllowSync\":null,\"AwDeviceId\":552,\"BrowserUriScheme\":\"awb:\\/\\/\",\"BrowserUriSecureScheme\":\"awbs:\\/\\/\",\"CreatedDateTime\":\"\\/Date(-62135578800000)\\/\",\"DaysSinceLastActivity\":0,\"DeviceIdentifier\":\"BE6330CAE8BC441EBCF65555B9E31114\",\"DevicePolicyLoadEndTime\":\"\\/Date(-62135578800000)\\/\",\"DevicePolicyLoadStartTime\":\"\\/Date(-62135578800000)\\/\",\"DeviceType\":2,\"EasDeviceIdentifier\":\"BE6330CAE8BC441EBCF65555B9E31114\",\"EasProfileInstall\":false,\"FullDeviceIdentifier\":\"BE6330CAE8BC441EBCF65555B9E31114\",\"IsCompromised\":false,\"IsDataProtected\":true,\"IsEnrolled\":true,\"IsManaged\":true,\"IsModelCompliant\":true,\"IsNotMDMCompliant\":false,\"IsOsCompliant\":true,\"MemConfigId\":27,\"MemDeviceId\":7653,\"MobileEmailDiagnosticsEnabled\":false,\"RemoveDevice\":false}").end();
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
}
