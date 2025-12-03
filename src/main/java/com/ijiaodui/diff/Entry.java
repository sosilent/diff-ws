package com.ijiaodui.diff;

import com.futureinteraction.utils.FileProc;
import com.futureinteraction.utils.cluster.VerticleMonitor;
import com.futureinteraction.utils.metrics.MetricsFactory;
import com.futureinteraction.utils.metrics.MetricsInit;
import com.hazelcast.core.HazelcastInstance;
import com.ijiaodui.diff.config.*;
import com.ijiaodui.diff.utils.LoadAppProperties;
import com.ijiaodui.diff.verticles.RestVerticle;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBusOptions;
import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.micrometer.MicrometerMetricsOptions;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.tuple.Pair;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Entry {
    private static Vertx VERTX;

    public static void main(String[] args) {
        //read version
        LoadAppProperties.loadAppProperties();

        String softdogEnabled = LoadAppProperties.getProperty("softdog");
        log.trace("softdog enabled: {}", softdogEnabled);

        //sys config init
        Options options = new Options();

        Option urlInput = new Option("c", "config", true, "config file path");
        urlInput.setRequired(true);
        options.addOption(urlInput);

        Option nacosInput = new Option("n", "nacos", true, "nacos config path");
        nacosInput.setRequired(false);
        options.addOption(nacosInput);

        CommandLineParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            String configPath = cmd.getOptionValue("c");

            Yaml yaml = new Yaml();

            List<Object> paraList;

            if (cmd.hasOption("n")) {
                String nacosYamlPath = cmd.getOptionValue("n");
                InputStream fin = new FileInputStream(nacosYamlPath);
                SysConfigInstance.NacosConfigPara nacosConfigPara = yaml.loadAs(fin, SysConfigInstance.NacosConfigPara.class);
                fin.close();

                paraList = SysConfigInstance.getInstance().init(nacosConfigPara);
            }
            else {
                paraList = new ArrayList<>();
            }

            InputStream in = new FileInputStream(configPath);
            SysConfig config = yaml.loadAs(in, SysConfig.class);

            run(config, paraList);
        } catch (Exception e) {
            log.error("系统无法启动: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static void run(SysConfig config, List<Object> paraList) throws Exception {
        int core = Runtime.getRuntime().availableProcessors();
        int ratio = 8;
        log.info("worker pool size: {}", core * ratio);

        int workerPoolSize = core * ratio;
        log.info("worker pool size: {}", workerPoolSize);

        FileSystemOptions fileSystemOptions = new FileSystemOptions();
        fileSystemOptions
                .setFileCachingEnabled(true);

        EventBusOptions eventBusOptions = new EventBusOptions();
        String localhost = InetAddress.getLocalHost().getHostAddress();

        log.info("localhost {}", localhost);
        eventBusOptions.setHost(localhost);

        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.setEventBusOptions(eventBusOptions)
                .setWorkerPoolSize(workerPoolSize)
                .setFileSystemOptions(fileSystemOptions);

        MicrometerMetricsOptions mo = config.metrics != null ? MetricsInit.initMetricsOptions((String) config.metrics.get("db_url"), (String) config.metrics.get("database")) : null;
        if (mo != null) {
            mo.setJvmMetricsEnabled(true);
            vertxOptions.setMetricsOptions(mo);
        }

        VertxBuilder vertxBuilder = Vertx.builder();
        vertxBuilder.with(vertxOptions);

        Promise<Pair<Vertx, HazelcastClusterManager>> vertxPromise = Promise.promise();
        vertxPromise.complete(Pair.of(vertxBuilder.build(), null));

        vertxPromise.future().onComplete(done -> {
            if (done.failed()) {
                log.error("failed to init clustered vertx");
            } else {
                VERTX = done.result().getKey();

                try {
                    FileProc.setDIR((String) config.rest_verticle.getOrDefault("./tmp", "./tmp"));
                }
                catch (Exception e) {
                    log.error(e.getMessage());
                }


                //设置参数
                SysConfigInstance.setPara(paraList);

                // 获取Hazelcast实例
                HazelcastInstance hazelcastInstance = done.result().getRight() != null ? done.result().getRight().getHazelcastInstance() : null;
                VerticleMonitor.getInstance().initMap(hazelcastInstance);

                MetricsFactory.setMeterRegistry(MetricsInit.getRegistry());

                JsonObject minio = null;
                Map<String, Object> minioConfig = config.minio;
                if (minioConfig != null) {
                    minio = new JsonObject();
                    minio.put("url", minioConfig.get("url"))
                            .put("access_key", minioConfig.get("access_key"))
                            .put("secret_key", minioConfig.get("secret_key"))
                            .put("bucket", minioConfig.get("bucket"));
                }

                JsonObject restVerticlePara = new JsonObject();
                restVerticlePara
                        .put("port", config.rest_verticle.getOrDefault("port", 8080))
                        .put("max_time", config.rest_verticle.getOrDefault("max_time", 300))
                        .put("max_body", config.rest_verticle.getOrDefault("max_body", -1))
                        .put("uploads", config.rest_verticle.getOrDefault("uploads", null))
                        .put("minio", minio);

                if (config.rest_verticle.containsKey("host"))
                    restVerticlePara.put("host", config.rest_verticle.get("host"));

                Future<Void> restVerticleFuture = deployVerticle(VERTX, RestVerticle.class,
                        (int) config.rest_verticle.getOrDefault("instance", 8),
                        (int) config.rest_verticle.getOrDefault("worker_pool_size", workerPoolSize),
                        restVerticlePara);

                restVerticleFuture.onFailure(failure -> {
                    log.error("failed to deploy virticles: {}", failure.getMessage());
                }).onSuccess(success -> {
                    log.info("succeeded to deploy virticles");
                });
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("to shutdown ...");
                CountDownLatch latch = new CountDownLatch(VerticleMonitor.getInstance().getVerticles().size());

                if (VERTX != null) {
                    for (String verticleId : VerticleMonitor.getInstance().getVerticles().keySet()) {
                        VERTX.undeploy(verticleId).onFailure(failure -> {
                            System.out.println(failure.getMessage());
                            log.error("failed to undeploy verticle: " + verticleId);
                        }).onSuccess(success -> {
                            System.out.println("verticle stopped: " + verticleId);
                            log.info("succeeded to undeploy verticle" + verticleId);

                            VerticleMonitor.getInstance().undeployVerticle(verticleId);
                        }).onComplete(done -> {
                            latch.countDown();
                        });
                    }
                }

                try {
                    latch.await(5, TimeUnit.SECONDS);
                    System.out.println("shut down ...");
                } catch (Exception ignored) {
                }
            }
        });
    }

    /**
     * 部署verticle
     * @param vertx
     * @param verticleClass verticle
     * @param instance 实例数
     * @param workPoolSize 工作池大小
     * @param para 参数
     * @return 部署未来结果
     */
    private static Future<Void> deployVerticle(Vertx vertx, Class<? extends Verticle> verticleClass, int instance, int workPoolSize, JsonObject para) {
        Promise<Void> deployPromise = Promise.promise();
        DeploymentOptions deploymentOptions = new DeploymentOptions();
        deploymentOptions.setInstances(instance);
        if (workPoolSize > 0) {
            deploymentOptions.setWorkerPoolSize(workPoolSize);
        }

        if (para != null && !para.isEmpty())
            deploymentOptions.setConfig(para);

        vertx.deployVerticle(verticleClass, deploymentOptions).onFailure(failure -> {
            deployPromise.fail(failure.getMessage());
            log.error("failed to deploy "+ verticleClass.getName() +": " + failure.getMessage());
        }).onSuccess(success -> {
            log.info("succeeded to deploy " + verticleClass.getName() + ": " + success);
            VerticleMonitor.getInstance().deployVerticle(VERTX, success, verticleClass.getName());
            deployPromise.complete();
        });
        return deployPromise.future();
    }
}
