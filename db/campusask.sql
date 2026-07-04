/*
 Navicat Premium Data Transfer

 Source Server         : localhost_3306
 Source Server Type    : MySQL
 Source Server Version : 80046 (8.0.46)
 Source Host           : localhost:3306
 Source Schema         : campusask

 Target Server Type    : MySQL
 Target Server Version : 80046 (8.0.46)
 File Encoding         : 65001

 Date: 29/05/2026 12:00:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for contribution
-- ----------------------------
DROP TABLE IF EXISTS `contribution`;
CREATE TABLE `contribution`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NULL DEFAULT NULL,
  `question` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'pending',
  `reviewed_by` bigint NULL DEFAULT NULL,
  `review_reason` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `review_time` datetime NULL DEFAULT NULL,
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of contribution
-- ----------------------------

-- ----------------------------
-- Table structure for hot_keyword
-- ----------------------------
DROP TABLE IF EXISTS `hot_keyword`;
CREATE TABLE `hot_keyword`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `keyword` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `search_count` int NULL DEFAULT 1,
  `updated_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `keyword`(`keyword` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of hot_keyword
-- ----------------------------

-- ----------------------------
-- Table structure for sensitive_word
-- ----------------------------
DROP TABLE IF EXISTS `sensitive_word`;
CREATE TABLE `sensitive_word` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `word` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of sensitive_word
-- ----------------------------
INSERT INTO `sensitive_word` (`word`) VALUES
('政治'),
('色情'),
('暴力'),
('赌博'),
('违规'),
('违禁'),
('敏感词'),
('涉政'),
('涉黄'),
('谣言');

-- ----------------------------
-- Table structure for knowledge
-- ----------------------------
DROP TABLE IF EXISTS `knowledge`;
CREATE TABLE `knowledge`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `keywords` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `view_count` int NULL DEFAULT 0,
  `created_by` bigint NULL DEFAULT NULL,
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_keywords` (`keywords`) USING BTREE,
  KEY `idx_title` (`title`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of knowledge
-- ----------------------------
INSERT INTO `knowledge` VALUES (1, '食堂开放时间', '学生食堂开放时间为早上7:00-9:00，中午11:00-13:00，晚上17:00-19:00', '生活服务', '食堂,开放,时间', 0, 1, '2026-05-27 21:14:10');
INSERT INTO `knowledge` VALUES (2, '图书馆借书规则', '图书馆开放时间为7:00-23:00，每人最多借阅5本书，借阅期限为30天', '学习资源', '图书馆,借书,规则', 0, 1, '2026-05-27 21:16:25');
INSERT INTO `knowledge` VALUES (3, '校车时刻表', '校车每天7:00、12:00、18:00从正门发车，终点为地铁站，全程约20分钟', '交通出行', '校车,时刻表,发车', 0, 1, '2026-05-27 21:17:15');
INSERT INTO `knowledge` VALUES (4, '在哪里打印', '校内打印点在图书馆一楼和教学楼B栋101，支持A4黑白和彩色打印，黑白0.1元/页', '生活服务', '打印,复印,图书馆', 0, 1, '2026-05-27 21:17:54');

-- ----------------------------
-- Table structure for question_log
-- ----------------------------
DROP TABLE IF EXISTS `question_log`;
CREATE TABLE `question_log`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) NULL DEFAULT NULL COMMENT '用户学号/工号',
  `question` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'knowledge',
  `processed` tinyint(1) NULL DEFAULT 0,
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of question_log
-- ----------------------------

-- ----------------------------
-- Table structure for forum_post（茶话会帖子）
-- ----------------------------
DROP TABLE IF EXISTS `forum_post`;
CREATE TABLE `forum_post` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发帖用户学号/工号',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '帖子标题',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '帖子内容',
  `images` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '图片URL列表，逗号分隔',
  `view_count` int DEFAULT 0 COMMENT '浏览量',
  `like_count` int DEFAULT 0 COMMENT '点赞数',
  `favorite_count` int DEFAULT 0 COMMENT '收藏数',
  `comment_count` int DEFAULT 0 COMMENT '评论数冗余',
  `tags` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_created_at` (`created_at`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for forum_comment（茶话会评论）
-- ----------------------------
DROP TABLE IF EXISTS `forum_comment`;
CREATE TABLE `forum_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '关联帖子ID',
  `user_id` varchar(50) NOT NULL COMMENT '评论用户学号/工号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `parent_id` bigint DEFAULT NULL COMMENT '父评论ID，NULL表示顶级评论',
  `reply_to_user_id` varchar(50) DEFAULT NULL COMMENT '被回复用户学号/工号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_post_id` (`post_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_parent_id` (`parent_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for forum_like（茶话会点赞记录）
-- ----------------------------
DROP TABLE IF EXISTS `forum_like`;
CREATE TABLE `forum_like` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `post_id` bigint NOT NULL COMMENT '帖子ID',
  `user_id` varchar(50) NOT NULL COMMENT '用户学号/工号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'user',
  `created_at` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;

ALTER TABLE user ADD COLUMN nickname VARCHAR(20) DEFAULT NULL COMMENT '用户昵称';
ALTER TABLE user ADD COLUMN avatar_url VARCHAR(255) DEFAULT NULL COMMENT '头像链接';
ALTER TABLE user ADD COLUMN motto VARCHAR(255) DEFAULT NULL COMMENT '个人名言';

-- 评论回复功能：添加 parent_id 和 reply_to_user_id
-- ALTER TABLE forum_comment ADD COLUMN  parent_id bigint DEFAULT NULL COMMENT '父评论ID，NULL表示顶级评论';
-- ALTER TABLE forum_comment ADD COLUMN  reply_to_user_id bigint DEFAULT NULL COMMENT '被回复用户ID';

-- 反馈表
CREATE TABLE IF NOT EXISTS feedback (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        question VARCHAR(500),
                                        answer TEXT,
                                        source VARCHAR(50),
                                        helpful TINYINT(1) DEFAULT 1,
                                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
-- V4: 知识库分类体系（17 类）
-- 学业：课程与选课、考试与成绩、图书馆、奖学金与资助、毕业与实习
-- 生活：食堂餐饮、宿舍住宿、交通出行、校园卡证、校园服务
-- 行政：医疗健康、网络与IT、行政规章、社团活动
-- 综合：招生与就业、学校概况、其他

UPDATE knowledge SET category = '课程与选课' WHERE category IN ('学术学习', '课程相关', '考试相关', '课程', '选课');
UPDATE knowledge SET category = '图书馆' WHERE category IN ('学习资源', '论文相关', '学习指导', '论文');
UPDATE knowledge SET category = '食堂餐饮' WHERE category IN ('校园生活', '食堂相关', '食堂', '餐饮');
UPDATE knowledge SET category = '宿舍住宿' WHERE category IN ('宿舍相关', '宿舍', '住宿服务');
UPDATE knowledge SET category = '校园服务' WHERE category IN ('生活服务', '综合咨询', '后勤相关', '打印', '快递', '后勤');
UPDATE knowledge SET category = '网络与IT' WHERE category IN ('网络服务', '技术支持', '网络相关', '系统相关', '设备相关', '网络', '系统', '设备', '技术');
UPDATE knowledge SET category = '行政规章' WHERE category IN ('规章制度', '学籍相关', '奖惩相关', '日常管理', '学籍', '奖惩', '管理');
UPDATE knowledge SET category = '社团活动' WHERE category IN ('活动相关', '活动', '校园活动');
UPDATE knowledge SET category = '招生与就业' WHERE category IN ('就业指导', '实习相关', '招聘相关', '职业规划', '实习', '招聘', '就业');
UPDATE knowledge SET category = '医疗健康' WHERE category IN ('医疗相关', '医疗', '校医');
UPDATE knowledge SET category = '奖学金与资助' WHERE category IN ('奖学金', '助学金');
UPDATE knowledge SET category = '交通出行' WHERE category IN ('校车');
UPDATE knowledge SET category = '其他' WHERE category IN ('其他服务');

-- ----------------------------
-- Table structure for knowledge_gap（知识缺口：AI/联网搜索回答的问题，待管理员审核入库）
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_gap`;
CREATE TABLE `knowledge_gap` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `question` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户原始问题',
  `answer` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'AI回答内容',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源：ai_search/ai_web/fallback',
  `search_count` int DEFAULT 1 COMMENT '同一问题被问次数',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT 'pending' COMMENT '状态：pending/approved/ignored',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核时指定的分类',
  `keywords` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核时指定的关键词',
  `reviewed_by` bigint DEFAULT NULL COMMENT '审核人ID',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_question` (`question`(200)) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;