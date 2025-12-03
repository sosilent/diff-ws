package com.ijiaodui.diff.verticles;

import com.futureinteraction.utils.cluster.VerticleMonitor;
import com.futureinteraction.utils.files.MinioWrapper;
import com.futureinteraction.utils.http.HttpUtils;
import com.hcifuture.softdog.SoftDogHandler;
import com.ijiaodui.diff.resources.DiffResource;
import com.ijiaodui.diff.utils.RequestLogHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.StaticHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RestVerticle extends AbstractVerticle {
    private HttpServer server;

    private List<MessageConsumer<JsonObject>> consumerList = new ArrayList<>();

    /**
     * This method constructs the router factory, mounts services and handlers and starts the http server with built router
     *
     * @return
     */
    private Future<Void> startHttpServer() {
        // Generate the router
        Router router = Router.router(vertx);

        log.info("to start http server ...");

        JsonObject config = config();
        if (!config.containsKey("port")) {
            return Future.failedFuture("missing para");
        }
        int port = config.getInteger("port");
        int maxTime = config.getInteger("max_time");
        int maxBody = config.getInteger("max_body", -1);

        BodyHandler bodyHandler = BodyHandler.create().setBodyLimit(maxBody);

        String uploads = config.getString("uploads", null);
        if (uploads != null)
            bodyHandler.setUploadsDirectory(uploads);

        router.route().handler(bodyHandler);
        router.route().handler(StaticHandler.create());
        router.route().handler(new SoftDogHandler());
        router.route().handler(RequestLogHandler.create(LoggerFormat.SHORT));

        HttpServerOptions serverOptions = new HttpServerOptions();
        serverOptions
                .setPort(port)
                .setIdleTimeoutUnit(TimeUnit.SECONDS)
                .setIdleTimeout(maxTime)
                .setCompressionSupported(true);

        JsonObject minioConfig = config().getJsonObject("minio");
        server = vertx.createHttpServer(serverOptions).requestHandler(router);
        Promise<Void> promise = Promise.promise();
        server.listen().onSuccess(success -> {
            MinioWrapper minioInstance = new MinioWrapper(vertx);

            if (minioConfig != null) {
                minioInstance.initClient(minioConfig.getString("url"),
                        minioConfig.getString("access_key"),
                        minioConfig.getString("secret_key"),
                        minioConfig.getString("bucket"));
            }

            regRestApi(minioInstance, router);
            log.info("start http server at port {}, idleTimeout: {}s", port, maxTime);
            promise.complete();
        }).onFailure(promise::fail);

        return promise.future();
    }

    private void enableCorsSupport(Router router) {
        CorsHandler corsHandler = CorsHandler.create();
        corsHandler.allowedMethod(HttpMethod.GET);
        corsHandler.allowedMethod(HttpMethod.POST);
        corsHandler.allowedMethod(HttpMethod.PUT);
        corsHandler.allowedMethod(HttpMethod.DELETE);
        corsHandler.allowedMethod(HttpMethod.OPTIONS);
        corsHandler.allowedHeader(HttpHeaders.AUTHORIZATION);
        corsHandler.allowedHeader(HttpHeaders.CONTENT_TYPE);
        corsHandler.allowedHeader("Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Origin");
        corsHandler.allowedHeader("Access-Control-Allow-Headers");
        corsHandler.allowedHeader("Access-Control-Allow-Method");
        corsHandler.allowedHeader("Access-Control-Allow-Credentials");
        corsHandler.allowedHeader("Access-Control-Expose-Headers");;

        corsHandler.allowCredentials(true);

        router.route().handler(corsHandler);
    }

    private void regRestApi(MinioWrapper minioWrapper, Router mainRouter) {
        enableCorsSupport(mainRouter);
        new DiffResource().register(mainRouter, minioWrapper);

        HttpUtils.dumpRestApi(mainRouter, "/", log);
    }

    @Override
    public void start(Promise<Void> promise) {
        startHttpServer().onComplete(promise);
    }

    /**
     * This method closes the http server and unregister all services loaded to Event Bus
     */
    @Override
    public void stop() {
        VerticleMonitor.getInstance().undeployVerticle(deploymentID());
        server.close();
        System.out.println("stop rest server verticle");
    }

    private void syncData(Message<JsonObject> msg) {
        log.debug("received sync data msg and to build trie ...");
    }
}
