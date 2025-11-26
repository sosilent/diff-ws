package com.ijiaodui.diff.config;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@NoArgsConstructor
@Data
public class SysConfig {
  public Map<String, Object> rest_verticle;
  public Map<String, Object> metrics;

  public Map<String, Object> llm;
  public Map<String, Object> ner;
  public List<String> location_dict;

  public Map<String, Object> db_verticle;

  //官员检查
  public Map<String, Object> official_check;
  //部门排序检查啊
  public Map<String, Object> department_order_check;
}
