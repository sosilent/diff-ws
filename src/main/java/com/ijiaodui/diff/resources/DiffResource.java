package com.ijiaodui.diff.resources;

import com.futureinteraction.utils.http.HttpUtils;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import java.util.List;

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

        response.setStatusCode(HttpStatus.SC_OK);
        long costTime = System.currentTimeMillis() - time;

        JsonObject result = new JsonObject();
        result.put("cost_time", costTime);

        response.end(result.encodePrettily());
    }

    private JsonObject genResponse(int code, String msg) {
        JsonObject data = new JsonObject();
        data.put("code", code)
                .put("msg", msg);

        return data;
    }
}
