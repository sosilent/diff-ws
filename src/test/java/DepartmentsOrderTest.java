import com.alibaba.nacos.api.exception.NacosException;
import com.ijiaodui.diff.algo.Mistake;
import com.ijiaodui.diff.algo.DepartmentOrderChecker;
import com.ijiaodui.diff.config.SysConfigInstance;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RunWith(VertxUnitRunner.class)
public class DepartmentsOrderTest {

  @Before
  public void before(TestContext testContext) throws IOException, NacosException {
    // 获取资源文件路径
    Yaml yaml = new Yaml();
    InputStream fin = new FileInputStream("nacos.yml");
    SysConfigInstance.NacosConfigPara nacosConfigPara = yaml.loadAs(fin, SysConfigInstance.NacosConfigPara.class);
    fin.close();

    List<Object> paraList = SysConfigInstance.getInstance().init(nacosConfigPara);
    SysConfigInstance.setPara(paraList);
  }

  @After
  public void after(TestContext testContext) {
  }


  @Test
  public void test(TestContext context) {
    // 测试用例
    String testText = "根据省委办公厅(省档案局)的要求，省政协办公厅需要配合省委宣传部(省新闻办、省文明办...，同时请省纪委、省监委监督工作，省委政法委提供保障。";

    String[] paras = testText.split("，|。");

    for (String para : paras) {
      List<Mistake> results = DepartmentOrderChecker.check(para);
      if (results.isEmpty()) {
        System.out.println("部门顺序正确");
      } else {
        for (Mistake mistake : results)
          System.err.println(JsonObject.mapFrom(mistake).encodePrettily());
      }
    }
  }
}
