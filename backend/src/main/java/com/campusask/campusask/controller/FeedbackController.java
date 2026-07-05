package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.mapper.FeedbackMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/admin/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    /** V4: 反馈列表（分页 + 可选筛选） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String filter) {  // all / helpful / unhelpful
        int offset = (page - 1) * size;
        Boolean helpfulFilter = null;
        if ("helpful".equals(filter)) helpfulFilter = true;
        else if ("unhelpful".equals(filter)) helpfulFilter = false;

        List<Map<String, Object>> list = feedbackMapper.listWithAnswer(offset, size, helpfulFilter);
        int total = helpfulFilter == null ? feedbackMapper.countAll()
                : (helpfulFilter ? feedbackMapper.countHelpful() : feedbackMapper.countUnhelpful());

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        return Result.success(result);
    }

    /** 反馈统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("helpful", feedbackMapper.countHelpful());
        data.put("unhelpful", feedbackMapper.countUnhelpful());
        data.put("total", feedbackMapper.countAll());
        return Result.success(data);
    }

    /** V4: 近7日反馈趋势 */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        return Result.success(feedbackMapper.dailyTrend(7));
    }

    /** 按问题分组的反馈摘要 */
    @GetMapping("/grouped")
    public Result<List<Map<String, Object>>> grouped() {
        return Result.success(feedbackMapper.groupByQuestion());
    }

    /** 按来源统计 */
    @GetMapping("/by-source")
    public Result<List<Map<String, Object>>> bySource() {
        return Result.success(feedbackMapper.groupBySource());
    }

    /** V4: 智能分析报告（纯数据驱动，不消耗 token） */
    @GetMapping("/summary")
    public Result<String> summary() {
        List<Map<String, Object>> grouped = feedbackMapper.groupByQuestion();
        List<Map<String, Object>> bySource = feedbackMapper.groupBySource();
        int totalHelpful = feedbackMapper.countHelpful();
        int totalUnhelpful = feedbackMapper.countUnhelpful();
        int totalAll = totalHelpful + totalUnhelpful;
        int rating = totalAll > 0 ? Math.round(totalHelpful * 100f / totalAll) : 0;

        if (grouped.isEmpty()) return Result.success("📭 还没有用户提交反馈，等数据积累后这里会生成详细的分析报告。");

        StringBuilder r = new StringBuilder();

        // === 1. 整体健康度 ===
        r.append("📊 一、整体反馈健康度\n");
        r.append("共收到 ").append(totalAll).append(" 条用户反馈，");
        if (rating >= 85) r.append("好评率 ").append(rating).append("%，用户满意度很高！同学们对 CampusAsk 的回答质量相当认可。\n");
        else if (rating >= 70) r.append("好评率 ").append(rating).append("%，整体表现不错，还有一些优化空间。\n");
        else if (rating >= 50) r.append("好评率 ").append(rating).append("%，差评较多，建议重点关注被踩的问题。\n");
        else r.append("好评率仅 ").append(rating).append("%，需要尽快排查问题，优化回答质量。\n");

        // 2. 点赞最多的问题
        r.append("\n👍 二、用户最满意的问题\n");
        int praised = 0;
        for (Map<String, Object> g : grouped) {
            int h = ((Number) g.get("helpful")).intValue();
            if (h > 0 && praised < 5) {
                r.append("  • ").append(g.get("question")).append("（").append(h).append(" 赞）\n");
                praised++;
            }
        }
        if (praised == 0) r.append("  暂无用户点赞的问题\n");

        // 3. 差评最多的问题
        r.append("\n👎 三、需要优化的问题\n");
        int criticized = 0;
        for (Map<String, Object> g : grouped) {
            int uh = ((Number) g.get("unhelpful")).intValue();
            if (uh > 0 && criticized < 5) {
                r.append("  • ").append(g.get("question")).append("（").append(uh).append(" 踩");
                if (uh >= 3) r.append(" ⚠️ 已自动加入知识缺口");
                r.append("）\n");
                criticized++;
            }
        }
        if (criticized == 0) r.append("  🎉 没有差评问题！太棒了！\n");

        // 4. 来源分析
        r.append("\n🔍 四、按来源分析\n");
        if (bySource.isEmpty()) {
            r.append("  暂无来源数据\n");
        } else {
            for (Map<String, Object> src : bySource) {
                String name = src.get("source").toString();
                int h = src.get("helpful") != null ? ((Number) src.get("helpful")).intValue() : 0;
                int uh = src.get("unhelpful") != null ? ((Number) src.get("unhelpful")).intValue() : 0;
                int t = h + uh;
                if (t == 0) continue;
                int pct = Math.round(h * 100f / t);
                String label = name.contains("knowledge") ? "本地知识库" :
                               name.contains("ai_knowledge") ? "AI检索数据库" :
                               name.contains("ai_search") ? "AI联网搜索" :
                               name.contains("ai_web") ? "AI自主回答" :
                               name.contains("fallback") ? "降级兜底" : name;
                r.append("  • ").append(label).append("：").append(t).append(" 条反馈，好评率 ").append(pct).append("%");
                if (pct < 70) r.append(" ⚠️ 偏低");
                r.append("\n");
            }
        }

        // 5. 改进建议
        r.append("\n💡 五、改进建议\n");
        if (rating >= 85 && criticized == 0) {
            r.append("  系统表现很优秀！保持当前质量，定期更新知识库即可。\n");
        } else {
            if (criticized > 0) {
                r.append("  📋 有 ").append(criticized).append(" 个问题收到差评，建议优先查看这些问题的回答内容是否需要修正。\n");
            }
            if (rating < 70) {
                r.append("  🎯 整体好评率偏低，建议：\n");
                r.append("    1) 补充高频差评问题的知识库内容\n");
                r.append("    2) 检查 AI 回答的准确性\n");
                r.append("    3) 优化联网搜索的摘要质量\n");
            }
            // 来源建议
            boolean aiSourceLow = false;
            for (Map<String, Object> src : bySource) {
                String name = src.get("source").toString();
                int h = src.get("helpful") != null ? ((Number) src.get("helpful")).intValue() : 0;
                int uh = src.get("unhelpful") != null ? ((Number) src.get("unhelpful")).intValue() : 0;
                if (h + uh >= 3 && h * 100f / (h + uh) < 60 && (name.contains("ai_search") || name.contains("ai_web"))) {
                    aiSourceLow = true;
                }
            }
            if (aiSourceLow) r.append("  🌐 AI 联网搜索的回答好评率偏低，建议对这类回答加强审核后再入库。\n");
        }

        return Result.success(r.toString());
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
