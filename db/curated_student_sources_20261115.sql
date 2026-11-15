-- Local curated import after the crawler stages matching versions of official pages.
-- reviewed_by remains NULL because this import is performed locally, not by a logged-in admin.
START TRANSACTION;

INSERT INTO knowledge(title,content,category,keywords,view_count,created_by,created_at)
SELECT '学生宿舍设备报修流程', CONCAT('学校宿舍管理科页面（2022-05-27）按设备类型列出报修方式。网络故障可联系网络中心 020-87110228；门禁问题可联系宿舍管理科 020-87114586 并向宿管登记；一般土建、水电、家具等问题可通过“我的e校园”APP 或后勤维修渠道报修。空调、热水、洗衣机等有各自服务电话，具体校区与设备类型请查看原文。历史联系电话可能调整，使用前以官网最新信息为准。\n\n来源：',url,'\n抓取时间：',fetched_at),
       '宿舍服务','宿舍 报修 网络 门禁 空调 热水 我的e校园',0,NULL,CURRENT_TIMESTAMP
FROM crawl_candidate WHERE id='0a4ed07f-f569-3c3b-ba19-a7614a19799e' AND fingerprint='e791e2566daffbdccc453b21f15e75d79a8b855306b3d2308b49bc71a96d8727' AND status='PENDING';
SET @knowledge_id=LAST_INSERT_ID();
UPDATE crawl_candidate SET status='APPROVED',knowledge_id=@knowledge_id,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=NULL
WHERE id='0a4ed07f-f569-3c3b-ba19-a7614a19799e' AND fingerprint='e791e2566daffbdccc453b21f15e75d79a8b855306b3d2308b49bc71a96d8727' AND status='PENDING';

INSERT INTO knowledge(title,content,category,keywords,view_count,created_by,created_at)
SELECT '心理咨询预约指引', CONCAT('学校心理中心指引（2026-04-30）：可从“华南理工大学学生在线”微信公众号的“学生事务→心理咨询与测评”入口，或学校心理健康信息化管理平台预约。使用本人学号和学校统一认证账号登录，点击“我要预约”并完成预约协议与排班选择。预约提前3天开放；首次咨询带学生证件并提前10分钟到场；不能赴约时至少提前24小时在系统取消。五山、大学城、广州国际校区均设服务点，具体办公时间、地点和电话以原文为准。服务对象为学校全日制在读大学生。\n\n来源：',url,'\n抓取时间：',fetched_at),
       '心理咨询','心理咨询 预约 学号 校区 心理中心',0,NULL,CURRENT_TIMESTAMP
FROM crawl_candidate WHERE id='af5c5a8a-9341-32e6-92d0-2ea4a51b99a7' AND fingerprint='f8d80413649250e85922f85e85cf85f40930114a25268539306080f8cbf300af' AND status='PENDING';
SET @knowledge_id=LAST_INSERT_ID();
UPDATE crawl_candidate SET status='APPROVED',knowledge_id=@knowledge_id,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=NULL
WHERE id='af5c5a8a-9341-32e6-92d0-2ea4a51b99a7' AND fingerprint='f8d80413649250e85922f85e85cf85f40930114a25268539306080f8cbf300af' AND status='PENDING';

INSERT INTO knowledge(title,content,category,keywords,view_count,created_by,created_at)
SELECT '心理咨询常见问题', CONCAT('学校心理中心常见问题页面说明：心理问题不等于精神病，咨询师不能窥视内心，心理咨询也不能保证一两次就解决困扰。咨询通常需要双方持续参与；咨询师提供分析、引导和支持，不能代替来访者作决定。页面也说明咨询师会为来访者保密。具体预约方式请参阅学校心理咨询预约指引。\n\n来源：',url,'\n抓取时间：',fetched_at),
       '心理咨询','心理咨询 常见问题 保密 流程',0,NULL,CURRENT_TIMESTAMP
FROM crawl_candidate WHERE id='3327882b-69b9-3de3-bdca-038a8bbb44bf' AND fingerprint='3ddae084e16d1051b46f4703ba10d6fb261670971c8d8e039e3da22f6be72768' AND status='PENDING';
SET @knowledge_id=LAST_INSERT_ID();
UPDATE crawl_candidate SET status='APPROVED',knowledge_id=@knowledge_id,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=NULL
WHERE id='3327882b-69b9-3de3-bdca-038a8bbb44bf' AND fingerprint='3ddae084e16d1051b46f4703ba10d6fb261670971c8d8e039e3da22f6be72768' AND status='PENDING';

INSERT INTO knowledge(title,content,category,keywords,view_count,created_by,created_at)
SELECT '2026年心理委员专题培训', CONCAT('学校2026-11-12通知：培训对象为2026级本科生、研究生心理委员，以及未参加2025年培训的其他年级心理委员。专题讲座安排在2026年11月17日至19日，团体活动安排在2026年11月至12月，具体场次和地点另行通知。内容涉及朋辈支持边界、倾听与陪伴、风险识别、团体活动和自我关怀。活动时间可能更新，请以学校最新通知为准。\n\n来源：',url,'\n抓取时间：',fetched_at),
       '校园通知','2026 心理委员 培训 讲座',0,NULL,CURRENT_TIMESTAMP
FROM crawl_candidate WHERE id='e6504f66-ef85-3e3d-9f64-97dc9ac03d6e' AND fingerprint='dc42f20fa94dc20ca1c86ff2e97dfe63adfdb2e833d90f99b16116a664d4b3fe' AND status='PENDING';
SET @knowledge_id=LAST_INSERT_ID();
UPDATE crawl_candidate SET status='APPROVED',knowledge_id=@knowledge_id,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=NULL
WHERE id='e6504f66-ef85-3e3d-9f64-97dc9ac03d6e' AND fingerprint='dc42f20fa94dc20ca1c86ff2e97dfe63adfdb2e833d90f99b16116a664d4b3fe' AND status='PENDING';

INSERT INTO knowledge(title,content,category,keywords,view_count,created_by,created_at)
SELECT '2026年何镜堂科技创新奖评选', CONCAT('学校2026-11-13通知：何镜堂科技创新奖拟奖励全日制本科生5人、硕士研究生2人、博士研究生2人；奖金分别为每人2万元、3万元、5万元。学生向所在学院（系）提交申请和证明材料，由学院审核推荐。学院报送截止时间为2026年11月19日17:00；具体申请表、评选条件和建筑学院安排见原通知。截止时间可能更新，请以学校最新公告为准。\n\n来源：',url,'\n抓取时间：',fetched_at),
       '奖学金','2026 何镜堂 科技创新奖 评选 申请 截止',0,NULL,CURRENT_TIMESTAMP
FROM crawl_candidate WHERE id='fe9e243c-c630-397c-947b-e15e439e8bee' AND fingerprint='19e772358e750c5532fab36c236280ba8870665c97bd1203f08c546aab08221a' AND status='PENDING';
SET @knowledge_id=LAST_INSERT_ID();
UPDATE crawl_candidate SET status='APPROVED',knowledge_id=@knowledge_id,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=NULL
WHERE id='fe9e243c-c630-397c-947b-e15e439e8bee' AND fingerprint='19e772358e750c5532fab36c236280ba8870665c97bd1203f08c546aab08221a' AND status='PENDING';

COMMIT;
