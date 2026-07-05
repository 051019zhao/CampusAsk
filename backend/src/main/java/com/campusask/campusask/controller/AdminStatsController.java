package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.mapper.FeedbackMapper;
import com.campusask.campusask.mapper.KnowledgeGapMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.mapper.QuestionLogMapper;
import com.campusask.campusask.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 管理员统计面板接口
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private QuestionLogMapper questionLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private KnowledgeGapMapper knowledgeGapMapper;

    @Autowired
    private FeedbackMapper feedbackMapper;

    /**
     * V4: 统计面板概览（6 张卡片）
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        String today = LocalDate.now().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("todayCount", questionLogMapper.countByDate(today));
        data.put("userCount", userMapper.countAll());
        data.put("knowledgeCount", knowledgeMapper.countAll());
        data.put("gapPending", knowledgeGapMapper.countPending());        // 待审核缺口
        data.put("totalQueries", questionLogMapper.countAll());           // 累计查询
        data.put("feedbackRatio", feedbackMapper.countHelpful() > 0      // 好评率
                ? Math.round(feedbackMapper.countHelpful() * 100.0 / Math.max(feedbackMapper.countAll(), 1)) : 0);
        data.put("date", today);
        return Result.success(data);
    }

    /** V4: 用户列表（管理员管理用户） */
    @GetMapping("/users")
    public Result<List<Map<String, Object>>> userList() {
        java.util.List<com.campusask.campusask.entity.User> users = userMapper.findAll();
        java.util.List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.campusask.campusask.entity.User u : users) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("role", u.getRole());
            m.put("avatarUrl", u.getAvatarUrl());
            m.put("motto", u.getMotto());
            m.put("createdAt", u.getCreateAt() != null ? u.getCreateAt().toString() : null);
            result.add(m);
        }
        return Result.success(result);
    }

    /** V4: 管理员重置用户密码 */
    @PutMapping("/users/{username}/reset-pwd")
    public Result<String> resetUserPwd(@PathVariable String username, @RequestBody Map<String, String> body) {
        String newPwd = body.getOrDefault("password", "123456");
        userMapper.updatePassword(username, newPwd);
        return Result.success("密码已重置为 " + newPwd);
    }

    /** V4: 删除用户 */
    @DeleteMapping("/users/{username}")
    public Result<String> deleteUser(@PathVariable String username) {
        userMapper.deleteByUsername(username);
        return Result.success("用户已删除");
    }

    /** V4: 近期活跃动态（最新 10 条问答） */
    @GetMapping("/recent-activity")
    public Result<List<Map<String, Object>>> recentActivity() {
        List<Map<String, Object>> raw = questionLogMapper.findRecentQuestionsWithSource(10);
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("question", row.get("question"));
            item.put("source", row.get("source"));
            Object timeObj = row.get("created_at");
            if (timeObj != null) {
                try {
                    if (timeObj instanceof LocalDateTime) {
                        item.put("time", ((LocalDateTime) timeObj).format(fmt));
                    } else {
                        item.put("time", timeObj.toString());
                    }
                } catch (Exception e) {
                    item.put("time", timeObj.toString());
                }
            }
            result.add(item);
        }
        return Result.success(result);
    }

    /** V4: 全时段高频提问 Top 10 */
    @GetMapping("/top-questions")
    public Result<List<Map<String, Object>>> topQuestions() {
        return Result.success(questionLogMapper.findTopQuestions(10));
    }

    /**
     * 今日查询量
     * GET /api/admin/stats/daily
     */
    @GetMapping("/daily")
    public Result<Map<String, Object>> daily() {
        String today = LocalDate.now().toString();
        int count = questionLogMapper.countByDate(today);
        Map<String, Object> data = new HashMap<>();
        data.put("date", today);
        data.put("count", count);
        return Result.success(data);
    }

    /**
     * 近7日趋势
     * GET /api/admin/stats/trend
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(6).toString();
        List<Map<String, Object>> raw = questionLogMapper.countByDateRange(startDate, endDate);

        // 补全缺失日期为 0
        Map<String, Integer> map = new LinkedHashMap<>();
        LocalDate cursor = LocalDate.now().minusDays(6);
        while (!cursor.isAfter(LocalDate.now())) {
            map.put(cursor.toString(), 0);
            cursor = cursor.plusDays(1);
        }
        for (Map<String, Object> row : raw) {
            String d = row.get("date").toString();
            if (map.containsKey(d)) {
                map.put(d, ((Number) row.get("count")).intValue());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        map.forEach((k, v) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", k);
            item.put("count", v);
            result.add(item);
        });
        return Result.success(result);
    }

    /**
     * 来源占比
     * GET /api/admin/stats/source
     */
    @GetMapping("/source")
    public Result<List<Map<String, Object>>> source() {
        return Result.success(questionLogMapper.countBySource());
    }

    /**
     * 知识库分类统计
     * GET /api/admin/stats/knowledge-categories
     */
    @GetMapping("/knowledge-categories")
    public Result<List<Map<String, Object>>> knowledgeCategories() {
        return Result.success(knowledgeMapper.countByCategory());
    }

    /**
     * 今日提问话题分布（基于关键词匹配归类）
     * GET /api/admin/stats/topics
     */
    @GetMapping("/topics")
    public Result<List<Map<String, Object>>> topics() {
        String today = LocalDate.now().toString();
        List<String> questions = questionLogMapper.findQuestionsByDate(today);
        Map<String, Integer> topicCount = new LinkedHashMap<>();
        topicCount.put("餐饮", 0);
        topicCount.put("宿舍", 0);
        topicCount.put("学习", 0);
        topicCount.put("交通", 0);
        topicCount.put("校园服务", 0);
        topicCount.put("其他", 0);

        for (String q : questions) {
            if (q == null) { topicCount.merge("其他", 1, Integer::sum); continue; }
            String ql = q.toLowerCase();
            if (ql.contains("食堂") || ql.contains("饭") || ql.contains("吃") || ql.contains("餐饮") || ql.contains("餐厅")) topicCount.merge("餐饮", 1, Integer::sum);
            else if (ql.contains("宿舍") || ql.contains("寝室") || ql.contains("住宿") || ql.contains("洗澡") || ql.contains("热水") || ql.contains("门禁")) topicCount.merge("宿舍", 1, Integer::sum);
            else if (ql.contains("图书馆") || ql.contains("课程") || ql.contains("选课") || ql.contains("考试") || ql.contains("成绩") || ql.contains("自习") || ql.contains("学习")) topicCount.merge("学习", 1, Integer::sum);
            else if (ql.contains("校车") || ql.contains("交通") || ql.contains("出行")) topicCount.merge("交通", 1, Integer::sum);
            else if (ql.contains("学费") || ql.contains("补贴") || ql.contains("打印") || ql.contains("快递") || ql.contains("一卡通") || ql.contains("社团") || ql.contains("医务室") || ql.contains("校园")) topicCount.merge("校园服务", 1, Integer::sum);
            else topicCount.merge("其他", 1, Integer::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        topicCount.forEach((k, v) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", k);
            item.put("value", v);
            result.add(item);
        });
        return Result.success(result);
    }

    /**
     * V4: AI 每日统计报告（全面数据驱动，不消耗 API token）
     */
    @GetMapping("/report")
    public Result<String> report() {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        // 基础数据
        int todayCount = questionLogMapper.countByDate(today);
        int yesterdayCount = questionLogMapper.countByDate(yesterday);
        int userCount = userMapper.countAll();
        int knowledgeCount = knowledgeMapper.countAll();
        int totalQueries = questionLogMapper.countAll();
        int gapPending = knowledgeGapMapper.countPending();

        // 反馈数据
        int helpfulCount = feedbackMapper.countHelpful();
        int unhelpfulCount = feedbackMapper.countUnhelpful();
        int feedbackTotal = feedbackMapper.countAll();
        int feedbackPct = feedbackTotal > 0 ? Math.round(helpfulCount * 100f / feedbackTotal) : 0;

        // 来源分布
        List<Map<String, Object>> sourceData = questionLogMapper.countBySource();
        long aiTotal = 0, localTotal = 0, rejectedTotal = 0;
        for (Map<String, Object> s : sourceData) {
            String name = s.get("name").toString();
            long val = ((Number) s.get("value")).longValue();
            if (name.contains("ai")) aiTotal += val;
            else if ("rejected".equals(name) || "fallback".equals(name)) rejectedTotal += val;
            else localTotal += val;
        }

        // 今日高频问题
        List<String> topQs = questionLogMapper.findQuestionsByDateLimit(today, 8);

        // 趋势对比
        double trend = yesterdayCount > 0 ? (todayCount - yesterdayCount) * 100.0 / yesterdayCount : 0;
        String trendStr = trend > 10 ? "📈 较昨日增长 " + Math.round(trend) + "%" :
                          trend < -10 ? "📉 较昨日下降 " + Math.round(Math.abs(trend)) + "%" :
                          "➡️ 较昨日基本持平";

        StringBuilder r = new StringBuilder();

        // === 人性化开场白 ===
        r.append("👋 早上好！今天是 ").append(today).append("，来看看 CampusAsk 今天表现如何吧～\n\n");

        // === 1. 整体运营概览（自然语言描述）===
        r.append("📌 今天共有 ").append(userCount).append(" 位用户访问了 CampusAsk，");
        if (todayCount > 0) {
            r.append("大家一共提了 ").append(todayCount).append(" 个问题。\n");
            r.append(trend > 5 ? "相比昨天，咨询量有所上升，看来大家越来越依赖校园百事通了呢！\n" :
                      trend < -5 ? "相比昨天咨询量略有下降，可能是周末大家宅在宿舍了 🏠\n" :
                      "咨询量与昨天基本持平，系统运行平稳。\n");
        } else {
            r.append("今天暂时还没有人提问，可能同学们都在上课吧 📖\n");
        }

        r.append("目前知识库里收录了 ").append(knowledgeCount).append(" 条校园知识，");
        if (knowledgeCount < 15) r.append("内容还不太够，建议找时间补充一下常用问题哦。\n");
        else r.append("覆盖面还不错，能满足大部分同学的日常咨询。\n");

        // === 2. 大家关心什么（话题分析）===
        r.append("\n🔥 近期大家最关心的话题是：\n");
        if (topQs.isEmpty()) {
            r.append("  暂时没有数据可分析，等有用户提问后这里会更丰富。\n");
        } else {
            // 话题归类
            int food = 0, lib = 0, dorm = 0, transport = 0, study = 0, other = 0;
            StringBuilder foodEx = new StringBuilder(), libEx = new StringBuilder(),
                    dormEx = new StringBuilder(), transportEx = new StringBuilder(), studyEx = new StringBuilder();
            for (String q : topQs) {
                String ql = q.toLowerCase();
                if (ql.contains("食堂") || ql.contains("饭") || ql.contains("吃")) { food++; if (foodEx.length() < 60) foodEx.append("「").append(q).append("」"); }
                else if (ql.contains("图书馆") || ql.contains("借书") || ql.contains("自习")) { lib++; if (libEx.length() < 60) libEx.append("「").append(q).append("」"); }
                else if (ql.contains("宿舍") || ql.contains("门禁") || ql.contains("寝室")) { dorm++; if (dormEx.length() < 60) dormEx.append("「").append(q).append("」"); }
                else if (ql.contains("校车") || ql.contains("交通") || ql.contains("出行")) { transport++; if (transportEx.length() < 60) transportEx.append("「").append(q).append("」"); }
                else if (ql.contains("选课") || ql.contains("考试") || ql.contains("课程") || ql.contains("成绩")) { study++; if (studyEx.length() < 60) studyEx.append("「").append(q).append("」"); }
                else other++;
            }

            if (food > 0) r.append("  🍽️ 餐饮相关：").append(food).append(" 次，").append(foodEx).append("\n");
            if (lib > 0) r.append("  📚 图书馆相关：").append(lib).append(" 次，").append(libEx).append("\n");
            if (dorm > 0) r.append("  🏠 宿舍相关：").append(dorm).append(" 次，").append(dormEx).append("\n");
            if (transport > 0) r.append("  🚌 交通出行：").append(transport).append(" 次，").append(transportEx).append("\n");
            if (study > 0) r.append("  📖 学业相关：").append(study).append(" 次，").append(studyEx).append("\n");
            if (other > 0) r.append("  💬 其他话题：").append(other).append(" 次\n");

            // 话题总结
            r.append("\n  💡 看来大家最关心的是 ");
            if (food >= lib && food >= dorm && food >= study) r.append("食堂餐饮");
            else if (lib >= food && lib >= dorm && lib >= study) r.append("图书馆");
            else if (dorm >= food && dorm >= lib && dorm >= study) r.append("宿舍住宿");
            else if (study >= food && study >= lib && study >= dorm) r.append("学业课程");
            else r.append("校园生活");
            r.append("方面的信息，可以考虑完善这方面的知识内容。\n");
        }

        // === 3. 问答质量分析 ===
        r.append("\n📊 问答质量方面，");
        if (todayCount > 0) {
            int localPct = Math.round(localTotal * 100f / Math.max(todayCount, 1));
            r.append("今天有 ").append(localPct).append("% 的问题直接从知识库获得了答案，");
            if (localPct >= 60) r.append("本地命中率很不错！👍\n");
            else if (localPct >= 30) r.append("中规中矩，还有提升空间。\n");
            else r.append("命中率偏低，很多问题需要靠 AI 兜底回答。\n");

            if (aiTotal > 0) r.append("AI 智能助手帮忙处理了 ").append(aiTotal).append(" 个知识库之外的问题，充当了很好的补充。\n");
        }

        // 好评率
        r.append("用户好评率 ").append(feedbackPct).append("%");
        if (feedbackPct >= 80) r.append("，同学们对回答质量很满意！😊\n");
        else if (feedbackPct >= 60) r.append("，整体不错，但还有改进空间。\n");
        else if (feedbackTotal > 0) r.append("，有些问题可能需要优化答案质量。😟\n");
        else r.append("（还没有人提交反馈呢）\n");

        // === 4. 待办提醒 ===
        r.append("\n📋 待办提醒：\n");
        if (gapPending > 0) {
            r.append("  🔔 有 ").append(gapPending).append(" 条 AI 回答等待你审核入库，");
            if (gapPending >= 5) r.append("攒了不少了，抽空处理一下吧！⏰\n");
            else r.append("抽空去「知识缺口」页面看一眼～\n");
        } else {
            r.append("  ✅ 没有待审核的知识缺口，干得漂亮！\n");
        }
        if (unhelpfulCount >= 3) r.append("  🔔 有 ").append(unhelpfulCount).append(" 条差评反馈，其中≥3次的已自动标记为待优化。\n");
        if (gapPending == 0 && unhelpfulCount < 3) r.append("  ✅ 一切正常，没有什么特别需要处理的。\n");

        // === 5. 小贴士 ===
        r.append("\n🌟 今日小贴士：\n");
        if (knowledgeCount < 10) r.append("  知识库条目还不到 10 条，建议从「知识缺口」页面把高频 AI 回答审核入库，或者用 Excel 批量导入。\n");
        else if (gapPending > 3) r.append("  知识缺口攒得有点多了，审核通过后能大幅提升本地命中率哦～\n");
        else if (feedbackPct < 70 && feedbackTotal > 5) r.append("  好评率有待提高，建议查看「用户反馈」页面，看看哪些问题的差评比较多。\n");
        else if (localTotal >= aiTotal) r.append("  知识库覆盖率不错！保持更新，让更多问题能秒回 🚀\n");
        else r.append("  AI 帮你分担了不少工作，记得把高频 AI 回答沉淀到知识库，让回答越来越快 ⚡\n");

        return Result.success(r.toString());
    }
}
