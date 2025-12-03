package com.ijiaodui.diff.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor
@Data
public class SysConfig {
  public Map<String, Object> rest_verticle;
  public Map<String, Object> metrics;

  public Map<String, Object> minio;
}
