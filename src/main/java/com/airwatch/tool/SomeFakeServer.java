package com.airwatch.tool;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;

/**
 * Created by manishk on 6/27/16.
 */
public class SomeFakeServer extends AbstractVerticle {

    public void start() {
        io.vertx.rxjava.core.http.HttpServer server = vertx.createHttpServer(
                new HttpServerOptions()
                        .setCompressionSupported(true)
        );
        Router router = createRequestHandler();
        server.requestHandler(router::accept).listen(8082);

    }

    private Router createRequestHandler() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create()).failureHandler(context -> {
            Throwable exception = ((io.vertx.ext.web.RoutingContext) context.getDelegate()).failure();
            context.response().setChunked(true).write("Error : " + exception.getMessage()).end();
        });
        router.post("/Microsoft-Server-ActiveSync").handler(context -> sayHello(context));
        router.get("/Microsoft-Server-ActiveSync").handler(context -> sayHello(context));
        return router;
    }

    private void sayHello(final RoutingContext context) {
        if ("Ping".equalsIgnoreCase(context.request().params().get("Cmd"))) {
            int random = 1 + (int)(Math.random() * ((55 - 1) + 1));
            final long timerId = vertx.setTimer(random * 1000, doNothing -> {
                context.response().end();
            });
        } else {
            context.response().end();
        }
    }
}
