package com.ijiaodui.diff.resources;

import com.futureinteraction.utils.http.HttpUtils;
import com.ijiaodui.diff.pdfdiff.AiBiDuiPdfDiffProc;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import java.util.List;
import java.util.concurrent.Callable;

@Slf4j
public class DiffResource {

    public void register(Router mainRouter) {
        String PATH = "/diff/v1";

        mainRouter.post(PATH).handler(this::diffDoc);
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

    private JsonObject genResponse(int code, String msg) {
        JsonObject data = new JsonObject();
        data.put("code", code)
                .put("msg", msg);

        return data;
    }
}