package com.airwatch.tool;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static io.vertx.core.impl.Arguments.require;

/**
 * Created by manishk on 6/24/16.
 */
public class HttpServer extends AbstractVerticle {

    public static final ConcurrentHashMap<String, AtomicLong> errorsType = new ConcurrentHashMap();
    public static final ConcurrentHashMap<String, AtomicLong> non200Responses = new ConcurrentHashMap();
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
    public static AtomicLong openConnections = new AtomicLong(0);
    public static AtomicLong errorCount = new AtomicLong(0);
    public static AtomicLong successCount = new AtomicLong(0);
    public static AtomicLong totalRequests = new AtomicLong(0);
    public static AtomicBoolean testInProgress = new AtomicBoolean(false);
    public static AtomicBoolean testStarted = new AtomicBoolean(false);
    public static AtomicBoolean startSinglePolicyUpdateSubmit = new AtomicBoolean(false);
    public static AtomicBoolean startSinglePolicyUpdateStarted = new AtomicBoolean(false);
    public static AtomicLong maxOpenConnections = new AtomicLong(0);

    public static AtomicLong singlePolicyUpdateMaxConnections = new AtomicLong(0);

    public static String remoteMethod;
    public static String remoteHost;
    public static String pingURL = "";
    public static String syncURL = "";
    public static String itemOPerationsURL = "";
    public static Long durationInSeconds;
    public static AtomicLong totalPingCount = new AtomicLong(0);
    public static AtomicLong totalSyncCount = new AtomicLong(0);
    public static AtomicLong totalItemOperationsCount = new AtomicLong(0);
    public static AtomicLong openPingCount = new AtomicLong(0);
    public static AtomicLong openSyncCount = new AtomicLong(0);
    public static AtomicLong openItemOperationsCount = new AtomicLong(0);
    public static MultiMap headers = null;

    @Override
    public void start() {
        headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Host", "10.44.72.188:443");
        headers.add("X-MS-PolicyKey", "3973534767");
        headers.add("Authorization", "bWFuaXNoOm1hbmlzaA==");
        headers.add("Content-Type", "application/vnd.ms-sync.wbxml");
        headers.add("Accept", "*/*");
        headers.add("Content-Length", "0");
        headers.add("Accept-Language", "en-us");
        headers.add("Accept-Encoding", "gzip, deflate");
        headers.add("MS-ASProtocolVersion", "14.1");
        headers.add("User-Agent", "Apple-iPod5C1/1304.15");

        System.setProperty("logback.configurationFile", "logback.xml");
        int port = Integer.parseInt(System.getProperty("server.port", "8080"));
        io.vertx.rxjava.core.http.HttpServer server = vertx.createHttpServer(
                new HttpServerOptions()
                        .setCompressionSupported(true)
        );
        Router router = createRequestHandler();
        server.requestHandler(router::accept).listen(port);

        vertx.setPeriodic(5000, doNothing -> {
            System.out.println("Open connections to remote server = " + openConnections
                    + ". Total requests = " + totalRequests + ". Success = " + successCount
                    + ". Error = " + errorCount + ". Error types with count = " + errorsType + ". Ping {" + totalPingCount + ", " + openPingCount + "}"
                    + ". Sync {" + totalSyncCount + ", " + openSyncCount + "}"
                    + ". ItemOperations {" + totalItemOperationsCount + ", " + openItemOperationsCount + "}"
                    + ". Non 200 response " + non200Responses);
        });
    }

    private Router createRequestHandler() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create()).failureHandler(context -> {
            Throwable exception = ((io.vertx.ext.web.RoutingContext) context.getDelegate()).failure();
            LOGGER.error("Unable to handle request {}", context.getBodyAsString(), exception);
            context.response().setChunked(true).write("Error : " + exception.getMessage()).end();
        });
        router.post("/load/start").handler(context -> startLoadTest(context));
        router.post("/load/stop").handler(context -> stopLoadTest(context));
        router.get("/load/stop").handler(context -> stopLoadTest(context));
        router.post("/load/startsinglepolicyload").handler(context -> startSinglePolicyUpdate(context));
        return router;
    }

    private void startLoadTest(final RoutingContext context) {
        if (testInProgress.compareAndSet(false, true)) {
            resetMetrics();
            JsonObject json = context.getBodyAsJson();
            require(json != null, "No request body");
            durationInSeconds = json.getLong("durationInSeconds");
            Integer concurrentRequests = json.getInteger("concurrentRequests");
            require(durationInSeconds != null && durationInSeconds > 0, "Duration to run the tests should be defined in minutes using variable 'durationInSeconds'");
            require(concurrentRequests != null && concurrentRequests > 0, "Concurrent requests size should be defined as number using variable 'concurrentRequests'");
            maxOpenConnections.set(concurrentRequests);
            remoteHost = json.getString("remoteHost");
            remoteMethod = json.getString("remoteMethod");

            pingURL = remoteHost + "/Microsoft-Server-ActiveSync?Cmd=Ping&User=KenyaUser118729&DeviceId=ABC&DeviceType=2";
            syncURL = remoteHost + "/Microsoft-Server-ActiveSync?Cmd=Sync&User=KenyaUser118729&DeviceId=ABC&DeviceType=2";
            itemOPerationsURL = remoteHost + "/Microsoft-Server-ActiveSync?Cmd=ItemOperations&User=KenyaUser118729&DeviceId=ABC&DeviceType=2";

            testStarted.set(true);
            vertx.setTimer(durationInSeconds * 1000, doNothing -> {
                System.out.println("*************************************************************************************");
                System.out.println("Stopping the test to make any further request!!!");
                System.out.println("*************************************************************************************");
                shutdownTheTest();
                System.out.println("\n\n\n" + errorsType + "\n\n\n");
            });
            context.response().setChunked(true).write("Load test triggered successfully!!!").end();
        } else {
            context.response().setChunked(true).write("Load test already running!!!").setStatusCode(400).end();
        }
    }

    private void resetMetrics() {
        totalPingCount.set(0);
        totalSyncCount.set(0);
        totalItemOperationsCount.set(0);

        openPingCount.set(0);
        openSyncCount.set(0);
        openItemOperationsCount.set(0);

        openConnections.set(0);
        totalRequests.set(0);
        errorCount.set(0);
        successCount.set(0);

        errorsType.clear();
        non200Responses.clear();
    }

    private void startSinglePolicyUpdate(final RoutingContext context) {
        if (!testStarted.get()) {
            context.response().setChunked(true).write("First start the load test.").end();
        } else if (startSinglePolicyUpdateSubmit.compareAndSet(false, true)) {
            context.response().setChunked(true).write("Single Policy Payload submit started successfully!!!").end();
            JsonObject json = context.getBodyAsJson();
            singlePolicyUpdateMaxConnections.set(json.getInteger("singlePolicyUpdateMaxConnections"));
            startSinglePolicyUpdateStarted.set(true);
        } else {
            context.response().setChunked(true).write("Single Policy Payload submit is already running!!!").end();
        }
    }

    private void stopLoadTest(final RoutingContext context) {
        context.response().setChunked(true).write("Load test stopped successfully!!!").end();
        shutdownTheTest();
    }

    private void shutdownTheTest() {
        testInProgress.set(false);
        testStarted.set(false);
        startSinglePolicyUpdateSubmit.set(false);
        startSinglePolicyUpdateStarted.set(false);
    }
}