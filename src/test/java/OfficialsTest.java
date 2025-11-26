import com.alibaba.nacos.api.exception.NacosException;
import com.ijiaodui.diff.algo.Mistake;
import com.ijiaodui.diff.algo.MistakeInfo;
import com.ijiaodui.diff.algo.ofiicialcheck.OfficialChecker;
import com.ijiaodui.diff.algo.ofiicialcheck.corrupt_official_check.CorruptOfficialCheck;
import com.ijiaodui.diff.algo.ofiicialcheck.corrupt_official_check.Position;
import com.ijiaodui.diff.config.SysConfigInstance;
import com.ijiaodui.diff.config.SysConfig;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.ijiaodui.diff.algo.utils.LocationSimilarityCalculator.calulateSimilarity;

@RunWith(VertxUnitRunner.class)
public class OfficialsTest {
    @Before
    public void before(TestContext testContext) throws IOException, NacosException {
        // 获取资源文件路径
        Yaml yaml = new Yaml();
        InputStream officialsConfigIn = new FileInputStream("officials.yml");
        List<Object> paraList = new ArrayList<>();
        paraList.add(yaml.loadAs(officialsConfigIn, OfficialsPara.class));

        SysConfigInstance.setPara(paraList);

        InputStream in = new FileInputStream("config.yml");
        SysConfig config = yaml.loadAs(in, SysConfig.class);
        if (config.ner != null) {
            String host = config.ner.get("host").toString();
            int port = (int) config.ner.get("port");
            String url = config.ner.get("url").toString();
            boolean ssl = (boolean) config.ner.getOrDefault("ssl", false);
            int timeout = (int) config.ner.get("timeout");
            int idleTimeout = (int) config.ner.getOrDefault("idle_timeout", 30);
            int type = (int) config.ner.get("type");

            Vertx vertx = Vertx.vertx();
            WebClientOptions wco = new WebClientOptions();
            wco.setConnectTimeout(timeout);
            wco.setIdleTimeout(idleTimeout).setIdleTimeoutUnit(TimeUnit.SECONDS);
            wco.setKeepAlive(true);
            wco.setMaxPoolSize(16);
            wco.setPoolCleanerPeriod(5000);
            WebClient webClient = WebClient.create(vertx, wco);

            JsonObject proxyPara = new JsonObject();
            proxyPara.put("host", host);
            proxyPara.put("port", port);
            proxyPara.put("url", url);
            proxyPara.put("timeout", timeout);
            proxyPara.put("ssl", ssl);

            NerProc nerProc = new NerProc(webClient, proxyPara, timeout, type);
            OfficialChecker.setNerProc(nerProc);
            CorruptOfficialCheck.setNerProc(nerProc);
        }
    }

    @After
    public void after(TestContext testContext) {
    }

    @Test
    public void test1(TestContext testContext) {
        String text = "中共杭州市委副书记，杭州市人民政府市长 姚高员," +
                "中共杭州市委常委，杭州市人民政府党组成员、常务副市长 陈瑾," +
                "中共杭州市委常委，杭州市人民政府党组成员、副市长 方毅," +
                "杭州市人民政府党组成员、副市长 丁狄刚," +
                "杭州市人民政府党组成员、副市长，市委政法委副书记，市公安局党委书记、局长、督察长 罗杰," +
                "杭州市人民政府副市长 王某某," +  // 错误：姓名不正确
                "杭州市人民政府党组成员、副市长、杭州高新区（滨江）党委书记 章登峰," +
                "杭州市公安局局长 李某某";  // 错误：职务分配不正确

        Async async = testContext.async();

        OfficialChecker checker = new OfficialChecker(Vertx.vertx());
        checker.check(text, OfficialChecker.Mode.CHECK_ORDER).onComplete(done -> {
            for (Mistake mistake : done.result()) {
                System.out.println(mistake.toString());
                for (MistakeInfo mi : mistake.getInfos()) {
                    System.out.println(mi.getDesc1());
                }
            }
            async.complete();
        });
    }

    @Test
    public void testCorruptOfficials(TestContext testContext) {
        Async async = testContext.async();

        CorruptOfficialCheck.addOfficial("张三", new Position("市委书记", "滨江", "张三严重违纪", 0));
        CorruptOfficialCheck.addOfficial("李四", new Position("主任", "滨江", "李四严重违纪", 0));
        CorruptOfficialCheck.addOfficial("王五", new Position("政协主席", "滨江", "王五严重违纪", 0));

        CorruptOfficialCheck detector = new CorruptOfficialCheck();
        String textToCheck = "近日，滨江市召开重要会议，市委书记张三发表了重要讲话。同时，省发改委也传来消息，主任李四将进行工作视察。另有报道称，程序员王五开发了一款新软件。";

        System.out.println("开始检测文本...");
        detector.check(textToCheck).onFailure(failure -> {
            System.err.println(failure.getMessage());
        }).onSuccess(results -> {
            if (results.isEmpty()) {
                System.out.println("未发现落马官员信息。");
            } else {
                System.out.println("检测结果如下：");
                for (Mistake result : results) {
                    System.out.println(result);
                }
            }
        }).onComplete(done -> {
            async.complete();
        });
    }

    @Test
    public void testLocationSimlarity(TestContext testContext) {
        System.out.println("--- 地点相似度计算测试 ---");

        // 规则 1: 最高分 (完全匹配)
        List<String> locA = Arrays.asList("中国", "北京市", "海淀区");
        List<String> locB = Arrays.asList("海淀区", "北京市", "中国"); // 顺序不同，但内容相同
        System.out.printf("情况1 (完全匹配): '%s' vs '%s' => 得分: %.2f\n", locA, locB, calulateSimilarity(locA, locB)); // 预期: 1.00

        // 规则 2: 中等得分 (部分匹配)
        List<String> locC = Arrays.asList("中国", "北京市");
        List<String> locD = Arrays.asList("北京市", "海淀区");
        System.out.printf("情况2 (部分匹配): '%s' vs '%s' => 得分: %.2f\n", locC, locD, calulateSimilarity(locC, locD)); // 预期: 1/3 ≈ 0.33

        // 规则 3: 零分 (信息缺失)
        List<String> locE = Arrays.asList("北京市");
        List<String> locF = Collections.emptyList();
        System.out.printf("情况3 (信息缺失): '%s' vs '%s' => 得分: %.2f\n", locE, locF, calulateSimilarity(locE, locF)); // 预期: 0.00

        // 规则 4: 负分惩罚 (明确冲突)
        List<String> locG = Arrays.asList("北京市");
        List<String> locH = Arrays.asList("上海市");
        System.out.printf("情况4 (明确冲突): '%s' vs '%s' => 得分: %.2f\n", locG, locH, calulateSimilarity(locG, locH)); // 预期: -0.50

        // 边界情况: 都为空
        List<String> locI = Collections.emptyList();
        List<String> locJ = Collections.emptyList();
        System.out.printf("情况5 (都为空): '%s' vs '%s' => 得分: %.2f\n", locI, locJ, calulateSimilarity(locI, locJ)); // 预期: 0.00

        // 边界情况: 一个为null
        List<String> locK = Arrays.asList("深圳市");
        List<String> locL = null;
        System.out.printf("情况6 (含null): '%s' vs '%s' => 得分: %.2f\n", locK, locL, calulateSimilarity(locK, locL)); // 预期: 0.00
    }
}
