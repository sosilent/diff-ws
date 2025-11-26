package com.ijiaodui.diff.config;

import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.futureinteraction.utils.NacosClient;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.*;
import java.util.concurrent.Executor;

@Slf4j
public class SysConfigInstance {
    @Setter
    private static String NAMESPACE_PREFIX = "com.ijiaodui.diff.config.";
    @Getter
    private final static SysConfigInstance instance = new SysConfigInstance();
    private NacosClient nacosClient;

    @NoArgsConstructor
    @Data
    public static class NacosConfigPara {
        public String host;
        public int port;
        public String namespace;

        public List<Map<String, String>> configs;
    }

    @Data
    static class ClientPara {
        //配置文件路径
        String path;
        String dataId;
        String group;
        //文件类型
        String type;
        String dataClass;
    }

    private final Map<String, ClientPara> clientParas = new HashMap<>();

    public List<Object> init(NacosConfigPara para) throws NacosException, IOException {
        nacosClient = new NacosClient(para.host, para.port, para.namespace);

        for (Map<String, String> config : para.configs) {
            String dataId = config.get("data_id");
            String group = config.get("group");
            String type = config.getOrDefault("type", "yaml");
            String filename = config.get("filename");
            String dataClass = config.get("data_class");

            ClientPara p = new ClientPara();
            p.path = filename;
            p.dataId = dataId;
            p.group = group;
            p.type = type;
            p.dataClass = dataClass;

            clientParas.put(dataId, p);

            try {
                Class<?> clazz = Class.forName(NAMESPACE_PREFIX + dataClass);
                nacosClient.addListener(dataId, group, new Listener() {
                    @Override
                    public Executor getExecutor() {
                        return null;
                    }

                    @Override
                    public void receiveConfigInfo(String configData) {
                        Yaml yaml = new Yaml();
                        InputStream in = new ByteArrayInputStream(configData.getBytes());

                        Object data = yaml.loadAs(in, clazz);
                        log.debug("{} data updated", clazz);
                        List<Object> objectList = new ArrayList<>();
                        objectList.add(data);

                        setPara(objectList);
                    }
                });
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }

        return syncConfig();
    }

    /**
     * 1. nacos 配置有效，则同步覆盖本地文件
     * 2. nacos 配置无效，则本地配置上传至nacos
     *
     * @throws NacosException
     * @throws IOException
     */
    private List<Object> syncConfig() throws NacosException, IOException {
        Yaml yaml = new Yaml();

        List<Object> objectList = new ArrayList<>();
        for (String dataId : clientParas.keySet()) {
            ClientPara clientPara = clientParas.get(dataId);

            String content = nacosClient.getConfig(dataId, clientPara.group);
            File file = new File(clientPara.path);

            if (content != null) {
                if (!file.exists() || file.canWrite()) {
                    FileOutputStream fo = new FileOutputStream(clientPara.path);
                    fo.write(content.getBytes());
                    fo.flush();
                    fo.close();
                } else
                    log.error("failed to write {}", clientPara.path);

                try {
                    Class<?> clazz = Class.forName(NAMESPACE_PREFIX + clientPara.dataClass);
                    Object data = yaml.loadAs(content, clazz);
                    objectList.add(data);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            } else if (file.canRead()) {
                InputStream cin = new FileInputStream(clientPara.path);
                byte[] data = cin.readAllBytes();

                boolean published = nacosClient.publishConfig(dataId, clientPara.group, new String(data), clientPara.type);
                log.info("published config: {}", published);
                cin.close();

                try {
                    Class<?> clazz = Class.forName(NAMESPACE_PREFIX + clientPara.dataClass);
                    Object d = yaml.loadAs(new String(data), clazz);
                    objectList.add(d);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }

        return objectList;
    }

    public static void setPara(List<Object> paras) {
        for (Object para : paras) {

        }
    }
}
