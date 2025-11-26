package com.ijiaodui.diff.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class LoadAppProperties {
  private final static Properties properties = new Properties();

  /**
   * 获取版本信息
   *
   * @return
   */
  public static void loadAppProperties() {

    // 使用类加载器加载资源文件
    try {
      InputStream is = LoadAppProperties.class.getClassLoader().getResourceAsStream("banner.txt");
      if (is != null) {
          byte[] bytes = is.readAllBytes();
          log.info(new String(bytes));
      }

      InputStream input = LoadAppProperties.class.getClassLoader().getResourceAsStream("app.properties");
      if (input == null) {
        log.error("failed to load app.properties");
        return;
      }

      // 加载属性文件
      properties.load(input);
      log.info("Ver: {}; Build Time: {}", properties.getProperty("app.version"), properties.getProperty("build.timestamp"));

    } catch (IOException ex) {
      ex.printStackTrace();
      log.error(ex.getMessage());
    }
  }

  // 获取属性值的方法
  public static String getProperty(String key) {
    return properties.getProperty(key);
  }
}
