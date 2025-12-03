package com.ijiaodui.diff.resources;

import com.futureinteraction.utils.FileProc;
import com.futureinteraction.utils.files.MinioWrapper;
import com.futureinteraction.utils.http.HttpUtils;
import com.ijiaodui.diff.pdfdiff.AiBiDuiPdfDiffProc;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

@Slf4j
public class DiffResource {
    MinioWrapper minioWrapper = null;

    public void register(Router mainRouter, MinioWrapper minioWrapper) {
        this.minioWrapper = minioWrapper;

        String PATH = "/diff/v1";
        String PATH_MINIO = "/diff/v1/minio";

        mainRouter.post(PATH).handler(this::diffDoc);
        mainRouter.post(PATH_MINIO).handler(this::diffMinioDoc);
    }

    private void diffDoc(RoutingContext context) {
        HttpServerResponse response = context.response();
        HttpUtils.setHttpHeader(response);

        List<FileUpload> fileUploadSet = context.fileUploads();

        if (fileUploadSet.size() != 2) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            JsonObject res = genResponse(HttpStatus.SC_BAD_REQUEST, "必须上传2个文件");
            response.end(res.encodePrettily());
            return;
        }

        long time = System.currentTimeMillis();

        String srcPdfFilePath = fileUploadSet.get(0).uploadedFileName();
        String cmpPdfFilePath = fileUploadSet.get(1).uploadedFileName();

        Callable<String> callable = () -> AiBiDuiPdfDiffProc.getPdfDiffJsonString(srcPdfFilePath, cmpPdfFilePath);
        context.vertx().executeBlocking(callable).onFailure(failure -> {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            JsonObject rt = new JsonObject();
            rt.put("code", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            rt.put("msg", failure.getMessage());
            response.end(rt.encodePrettily());
        }).onSuccess(success -> {
            HttpUtils.setHttpHeader(response);
            JsonObject rt = new JsonObject(success);
            long costTime = System.currentTimeMillis() - time;
            rt.put("cost_time", costTime);
            response.end(rt.encodePrettily());
        }).onComplete(done -> {
            context.vertx().fileSystem().delete(srcPdfFilePath).onFailure(failure -> {
                log.error(failure.getMessage());
            });

            context.vertx().fileSystem().delete(cmpPdfFilePath).onFailure(failure -> {
                log.error(failure.getMessage());
            });
        });
    }

    private void diffMinioDoc(RoutingContext context) {
        HttpServerResponse response = context.response();
        HttpUtils.setHttpHeader(response);

        JsonObject p = context.body().asJsonObject();
        JsonArray files = p.getJsonArray("files", new JsonArray());

        if (files.size() != 2) {
            response.setStatusCode(HttpStatus.SC_BAD_REQUEST);
            JsonObject res = genResponse(HttpStatus.SC_BAD_REQUEST, "必须上传2个minio文件id");
            response.end(res.encodePrettily());
            return;
        }

        long time = System.currentTimeMillis();

        StringBuilder srcPdfFilePath = new StringBuilder();
        StringBuilder cmpPdfFilePath = new StringBuilder();

        Future<byte[]> minioFileFuture1 = minioWrapper != null ? minioWrapper.download(files.getString(0)) : Future.failedFuture("minio client is null");
        Future<byte[]> minioFileFuture2 = minioWrapper != null ? minioWrapper.download(files.getString(1)) : Future.failedFuture("minio client is null");

        Future.all(minioFileFuture1, minioFileFuture2).compose(v -> {
            srcPdfFilePath.append(FileProc.getDIR()).append("/").append(UUID.randomUUID()).append(".pdf");
            cmpPdfFilePath.append(FileProc.getDIR()).append("/").append(UUID.randomUUID()).append(".pdf");

            Future<Void> future1 = context.vertx().fileSystem().writeFile(srcPdfFilePath.toString(), Buffer.buffer(minioFileFuture1.result()));
            Future<Void> future2 = context.vertx().fileSystem().writeFile(cmpPdfFilePath.toString(), Buffer.buffer(minioFileFuture1.result()));
            return Future.all(future1, future2);
        }).compose(v -> {
            Callable<String> callable = () -> AiBiDuiPdfDiffProc.getPdfDiffJsonString(srcPdfFilePath.toString(), cmpPdfFilePath.toString());
            return context.vertx().executeBlocking(callable);
        }).onFailure(failure -> {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            JsonObject rt = new JsonObject();
            rt.put("code", HttpStatus.SC_INTERNAL_SERVER_ERROR);
            rt.put("msg", failure.getMessage());
            response.end(rt.encodePrettily());
        }).onSuccess(success -> {
            HttpUtils.setHttpHeader(response);
            JsonObject rt = new JsonObject(success);
            long costTime = System.currentTimeMillis() - time;
            rt.put("cost_time", costTime);
            response.end(rt.encodePrettily());
        }).onComplete(done -> {
            if (!srcPdfFilePath.isEmpty()) {
                context.vertx().fileSystem().delete(srcPdfFilePath.toString()).onFailure(failure -> {
                    log.error(failure.getMessage());
                });
            }

            if (!cmpPdfFilePath.isEmpty()) {
                context.vertx().fileSystem().delete(cmpPdfFilePath.toString()).onFailure(failure -> {
                    log.error(failure.getMessage());
                });
            }
        });
    }

    private JsonObject genResponse(int code, String msg) {
        JsonObject data = new JsonObject();
        data.put("code", code)
                .put("msg", msg);

        return data;
    }
}