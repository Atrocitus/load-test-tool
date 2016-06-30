package com.airwatch.tool;

import io.vertx.core.DeploymentOptions;
import io.vertx.rxjava.core.AbstractVerticle;

public class AppStarter extends AbstractVerticle {

    @Override
    public void start() {
        vertx.deployVerticle(HttpServer.class.getCanonicalName(), new DeploymentOptions().setInstances(1));
        vertx.deployVerticle(LoadWorker.class.getCanonicalName(), new DeploymentOptions().setInstances(10).setWorker(true));
        vertx.deployVerticle(SomeFakeServer.class.getCanonicalName(), new DeploymentOptions().setInstances(5));
    }
}