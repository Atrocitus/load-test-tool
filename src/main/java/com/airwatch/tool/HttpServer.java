package com.airwatch.tool;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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
    public static AtomicLong totalRequests = new AtomicLong(1);
    public static AtomicBoolean testInProgress = new AtomicBoolean(false);
    public static AtomicBoolean testStarted = new AtomicBoolean(false);
    public static AtomicBoolean startSinglePolicyUpdateSubmit = new AtomicBoolean(false);
    public static AtomicBoolean startSinglePolicyUpdateStarted = new AtomicBoolean(false);
    public static AtomicLong maxOpenConnections = new AtomicLong(0);

    public static String remoteMethod;

    public static JsonArray remoteHostsWithPortAndProtocol = new JsonArray();
    public static JsonArray singlePolicyUpdateHostsWithPortAndProtocol = new JsonArray();
    public static Integer singlePolicyUpdateRequestsPerSecond = 100;
    public static int rampUpTimeCounter = 0;

    public static Double rampUpTimeMultiplier = 0D;
    public static Long durationInSeconds;

    public static Double rampUpTimeInSeconds;
    public static Long singlePolicyUpdateDurationInSeconds;


    public static int pingPercentage = 0;
    public static int syncPercentage = 0;
    public static int itemOperationPercentage = 0;
    public static AtomicLong totalPingCount = new AtomicLong(0);
    public static AtomicLong totalSyncCount = new AtomicLong(0);
    public static AtomicLong totalItemOperationsCount = new AtomicLong(0);
    public static AtomicLong openPingCount = new AtomicLong(0);
    public static AtomicLong openSyncCount = new AtomicLong(0);
    public static AtomicLong openItemOperationsCount = new AtomicLong(0);

    public static int pingRequestsPerSecond = 0;
    public static int syncRequestsPerSecond = 0;
    public static int itemOperationRequestsPerSecond = 0;
    public static MultiMap headers = null;
    public static List<Device> devices = CsvReader.createDevices();
    private long testStartTime = 0;

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
            long testDuration = testStartTime > 0 ? ((System.currentTimeMillis() - testStartTime) / 1000) : 0;
            System.out.println("\n Test Duration = " + testDuration + " Seconds. Open connections to remote server = " + openConnections
                    + ". Total requests = " + totalRequests + ". Success = " + successCount
                    + ". Error = " + errorCount + ". Error types with count = " + errorsType + ". Ping {" + totalPingCount + ", " + openPingCount + "}"
                    + ". Sync {" + totalSyncCount + ", " + openSyncCount + "}"
                    + ". ItemOperations {" + totalItemOperationsCount + ", " + openItemOperationsCount + "}"
                    + ". Non 200 response " + non200Responses);
        });

        final long timerId = vertx.setPeriodic(1000, doNothing -> {
            if (testStarted.get()) {
                rampUpTimeCounter++;
                rampUpTimeMultiplier = (rampUpTimeCounter / rampUpTimeInSeconds);
            }
        });

        vertx.setPeriodic(1000, doNothing -> {
            if (testStarted.get() && rampUpTimeCounter > rampUpTimeInSeconds) {
                rampUpTimeMultiplier = 1D;
                vertx.cancelTimer(timerId);
                rampUpTimeCounter--;
            }
        });
    }

    private Router createRequestHandler() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create()).failureHandler(context -> {
            Throwable exception = ((io.vertx.ext.web.RoutingContext) context.getDelegate()).failure();
            LOGGER.error("Unable to handle request {}", context.getBodyAsString(), exception);
            testInProgress.set(false);
            startSinglePolicyUpdateSubmit.set(false);
            testStartTime = 0;
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
            require(durationInSeconds != null && durationInSeconds > 0, "Duration to run the tests should be defined in minutes using variable 'durationInSeconds'");

            rampUpTimeInSeconds = json.getDouble("rampUpTimeInSeconds", 10D);
            require(rampUpTimeInSeconds != null && rampUpTimeInSeconds > 0, "Provide 'rampUpTimeInSeconds'");

            remoteHostsWithPortAndProtocol = json.getJsonArray("remoteHostsWithPortAndProtocol");
            require(remoteHostsWithPortAndProtocol != null && remoteHostsWithPortAndProtocol.size() > 0, "Define the remote hosts as array with name 'remoteHostsWithPortAndProtocol'");
            remoteMethod = json.getString("remoteMethod", "POST");

            String testType = json.getString("testType");
            require("requestPerSecond".equalsIgnoreCase(testType) || "concurrentUsers".equalsIgnoreCase(testType), "Define 'testType' parameter value as 'requestPerSecond' or 'concurrentUsers'");

            if ("requestPerSecond".equalsIgnoreCase(testType)) {
                pingRequestsPerSecond = json.getInteger("pingRequestsPerSecond", 0);
                syncRequestsPerSecond = json.getInteger("syncRequestsPerSecond", 0);
                itemOperationRequestsPerSecond = json.getInteger("itemOperationRequestsPerSecond", 0);

                require(pingRequestsPerSecond > -1, "Define 'pingRequestsPerSecond'");
                require(syncRequestsPerSecond > -1, "Define 'syncRequestsPerSecond'");
                require(itemOperationRequestsPerSecond > -1, "Define 'itemOperationRequestsPerSecond'");

                vertx.eventBus().send("scheduleTest", LoadTestType.REQUEST_PER_SECOND.toString());
            } else {

                int maxOpenConcurrentConnections = json.getInteger("maxOpenConnections", 0);
                require(maxOpenConcurrentConnections > 0, "Define 'maxOpenConnections'");
                maxOpenConnections.set(maxOpenConcurrentConnections);

                pingPercentage = json.getInteger("pingPercentage", 0);
                syncPercentage = json.getInteger("syncPercentage", 0);
                itemOperationPercentage = json.getInteger("itemOperationPercentage", 0);

                require(pingPercentage > 0, "Define 'pingPercentage'");
                require(syncPercentage > 0, "Define 'syncPercentage'");
                require(itemOperationPercentage > 0, "Define 'itemOperationPercentage'");

                vertx.eventBus().send("scheduleTest", LoadTestType.CONCURRENT_OPEN_CONNECTIONS.toString());
            }

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
        testStartTime = System.currentTimeMillis();
        totalPingCount.set(0);
        totalSyncCount.set(0);
        totalItemOperationsCount.set(0);

        openPingCount.set(0);
        openSyncCount.set(0);
        openItemOperationsCount.set(0);

        openConnections.set(0);
        totalRequests.set(1);
        errorCount.set(0);
        successCount.set(0);

        errorsType.clear();
        non200Responses.clear();
    }

    private void startSinglePolicyUpdate(final RoutingContext context) {
        if (startSinglePolicyUpdateSubmit.compareAndSet(false, true)) {
            JsonObject json = context.getBodyAsJson();
            singlePolicyUpdateRequestsPerSecond = json.getInteger("requestsPerSecond", 100);
            singlePolicyUpdateHostsWithPortAndProtocol = json.getJsonArray("singlePolicyUpdateHostsWithPortAndProtocol");
            singlePolicyUpdateHostsWithPortAndProtocol = json.getJsonArray("singlePolicyUpdateHostsWithPortAndProtocol");
            singlePolicyUpdateDurationInSeconds = json.getLong("singlePolicyUpdateDurationInSeconds");

            vertx.setTimer(singlePolicyUpdateDurationInSeconds * 1000, doNothing -> {
                System.out.println("*************************************************************************************");
                System.out.println("Stopping Single Policy Update test to make any further request!!!");
                System.out.println("*************************************************************************************");
                startSinglePolicyUpdateSubmit.set(false);
                startSinglePolicyUpdateStarted.set(false);
            });

            startSinglePolicyUpdateStarted.set(true);
            context.response().setChunked(true).write("Single Policy Payload submit started successfully!!!").end();
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
        rampUpTimeMultiplier = 0D;
        rampUpTimeCounter = 0;
        vertx.eventBus().send("stopTests", null);
    }
}