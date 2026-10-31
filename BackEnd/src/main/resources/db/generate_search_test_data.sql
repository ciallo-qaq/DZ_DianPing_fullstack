-- 实时搜索联调数据：每个主要业务表新增约 200 条。
-- 使用固定 ID 和 INSERT IGNORE，可重复执行且不会重复插入。
-- 分类表 tb_shop_type 保持固定字典数据，不批量扩充。

USE hmdp;

CREATE TEMPORARY TABLE IF NOT EXISTS tmp_seed_numbers (
    n INT NOT NULL PRIMARY KEY
);
TRUNCATE TABLE tmp_seed_numbers;

INSERT INTO tmp_seed_numbers (n)
SELECT ones.n + tens.n * 10 + hundreds.n * 100 + 1
FROM (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) ones
CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
) tens
CROSS JOIN (
    SELECT 0 AS n UNION ALL SELECT 1 UNION ALL SELECT 2
) hundreds
WHERE ones.n + tens.n * 10 + hundreds.n * 100 + 1 <= 200;

START TRANSACTION;

-- 新增 200 个测试用户，ID 为 1011~1210。
INSERT IGNORE INTO tb_user
    (id, phone, password, nick_name, icon, create_time, update_time)
SELECT
    1010 + n,
    CONCAT('139', LPAD(80000000 + n, 8, '0')),
    '',
    CONCAT('搜索测试用户', LPAD(n, 3, '0')),
    '/imgs/icons/default-icon.png',
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 10:00:00'),
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 10:00:00')
FROM tmp_seed_numbers;

-- 为 10~209 号已有用户补充 200 条个人资料。
INSERT IGNORE INTO tb_user_info
    (user_id, city, introduce, fans, followee, gender, birthday, credits, level,
     create_time, update_time)
SELECT
    9 + n,
    '杭州',
    CONCAT('喜欢探店、咖啡和本地美食，这是第', n, '位体验用户。'),
    20 + n,
    10 + (n % 50),
    n % 2,
    DATE_SUB('2000-01-01', INTERVAL (20 + n % 15) YEAR),
    100 + n * 3,
    LEAST(9, n % 10),
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 10:00:00'),
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 10:00:00')
FROM tmp_seed_numbers;

-- 新增 200 家店铺，ID 为 1001~1200，名称覆盖常见搜索前缀。
INSERT IGNORE INTO tb_shop
    (id, name, type_id, images, area, address, x, y, avg_price,
     sold, comments, score, open_hours, create_time, update_time)
SELECT
    1000 + n,
    CASE n % 10
        WHEN 0 THEN CONCAT('豆腐小馆', LPAD(n, 3, '0'), '店')
        WHEN 1 THEN CONCAT('豆花甜品', LPAD(n, 3, '0'), '店')
        WHEN 2 THEN CONCAT('海底捞火锅', LPAD(n, 3, '0'), '店')
        WHEN 3 THEN CONCAT('港式茶餐厅', LPAD(n, 3, '0'), '店')
        WHEN 4 THEN CONCAT('西湖咖啡', LPAD(n, 3, '0'), '店')
        WHEN 5 THEN CONCAT('星聚会KTV', LPAD(n, 3, '0'), '店')
        WHEN 6 THEN CONCAT('轻氧健身', LPAD(n, 3, '0'), '工作室')
        WHEN 7 THEN CONCAT('精致美甲', LPAD(n, 3, '0'), '店')
        WHEN 8 THEN CONCAT('亲子乐园', LPAD(n, 3, '0'), '店')
        ELSE CONCAT('夜色酒吧', LPAD(n, 3, '0'), '店')
    END,
    CASE n % 10
        WHEN 0 THEN 1
        WHEN 1 THEN 1
        WHEN 2 THEN 1
        WHEN 3 THEN 1
        WHEN 4 THEN 8
        WHEN 5 THEN 2
        WHEN 6 THEN 4
        WHEN 7 THEN 10
        WHEN 8 THEN 7
        ELSE 8
    END,
    '/imgs/blogs/blog1.jpg',
    ELT(1 + (n % 6), '大关', '运河上街', '拱宸桥/上塘', '北部新城', '水晶城', '远洋乐堤港'),
    CONCAT('杭州市拱墅区搜索测试路', n, '号'),
    120.10 + (n % 20) * 0.001,
    30.20 + (n % 20) * 0.001,
    45 + (n % 16) * 10,
    100 + n * 17,
    20 + n * 2,
    35 + (n % 16),
    '10:00-22:00',
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 10:00:00'),
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 10:00:00')
FROM tmp_seed_numbers;

-- 新增 200 篇博客，ID 为 1001~1200。
INSERT IGNORE INTO tb_blog
    (id, shop_id, user_id, title, images, content, liked, comments,
     create_time, update_time)
SELECT
    1000 + n,
    1000 + n,
    9 + n,
    CASE n % 10
        WHEN 0 THEN CONCAT('豆腐脑早餐探店第', n, '篇')
        WHEN 1 THEN CONCAT('豆腐乳配粥也太香了第', n, '篇')
        WHEN 2 THEN CONCAT('豆花甜品测评第', n, '篇')
        WHEN 3 THEN CONCAT('火锅聚餐推荐第', n, '篇')
        WHEN 4 THEN CONCAT('茶餐厅下午茶第', n, '篇')
        WHEN 5 THEN CONCAT('咖啡店安静办公第', n, '篇')
        WHEN 6 THEN CONCAT('KTV周末欢唱第', n, '篇')
        WHEN 7 THEN CONCAT('亲子乐园遛娃第', n, '篇')
        WHEN 8 THEN CONCAT('美甲款式分享第', n, '篇')
        ELSE CONCAT('健身打卡记录第', n, '篇')
    END,
    '/imgs/blogs/blog1.jpg',
    CASE n % 10
        WHEN 0 THEN CONCAT('这家店的豆腐脑入口顺滑，配上油条很适合早餐。测试记录：', n)
        WHEN 1 THEN CONCAT('豆腐乳味道浓郁，配白粥很下饭，环境安静。测试记录：', n)
        WHEN 2 THEN CONCAT('豆花口感细腻，甜咸两种口味都值得尝试。测试记录：', n)
        WHEN 3 THEN CONCAT('火锅锅底香辣，菜品新鲜，适合朋友聚餐。测试记录：', n)
        WHEN 4 THEN CONCAT('港式茶餐厅的菠萝包和奶茶都很不错。测试记录：', n)
        WHEN 5 THEN CONCAT('咖啡店环境舒适，有插座和稳定网络。测试记录：', n)
        WHEN 6 THEN CONCAT('KTV曲库更新及时，包间隔音效果不错。测试记录：', n)
        WHEN 7 THEN CONCAT('亲子乐园项目丰富，工作人员很耐心。测试记录：', n)
        WHEN 8 THEN CONCAT('美甲店款式多，颜色显白，服务细致。测试记录：', n)
        ELSE CONCAT('健身房器械齐全，晚上锻炼的人比较多。测试记录：', n)
    END,
    (n * 37) % 2000,
    (n * 11) % 300,
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 12:00:00'),
    TIMESTAMPADD(DAY, n % 120, '2026-01-01 12:00:00')
FROM tmp_seed_numbers;

-- 新增 200 条博客评论，ID 为 1001~1200。
INSERT IGNORE INTO tb_blog_comments
    (id, user_id, blog_id, parent_id, answer_id, content, liked, status,
     create_time, update_time)
SELECT
    1000 + n,
    9 + n,
    1000 + n,
    CASE WHEN n % 4 = 0 THEN 999 + n ELSE 0 END,
    0,
    ELT(1 + (n % 6),
        '看起来很好吃，已经加入周末清单。',
        '环境不错，照片拍得也很有氛围。',
        '请问人均消费大概多少？',
        '这家店我也去过，推荐招牌菜。',
        '感谢分享，信息很有帮助。',
        '下次准备和朋友一起去试试。'),
    n % 30,
    0,
    TIMESTAMPADD(DAY, n % 120, '2026-01-02 12:00:00'),
    TIMESTAMPADD(DAY, n % 120, '2026-01-02 12:00:00')
FROM tmp_seed_numbers;

-- 新增 200 条关注关系；每对用户组合唯一。
INSERT IGNORE INTO tb_follow
    (id, user_id, follow_user_id, create_time)
SELECT
    1000 + n,
    9 + n,
    209 + n,
    TIMESTAMPADD(DAY, n % 120, '2026-01-03 12:00:00')
FROM tmp_seed_numbers;

-- 新增 200 张秒杀优惠券，ID 为 1001~1200。
INSERT IGNORE INTO tb_voucher
    (id, shop_id, title, sub_title, rules, pay_value, actual_value, type,
     status, create_time, update_time)
SELECT
    1000 + n,
    1000 + n,
    CASE n % 5
        WHEN 0 THEN CONCAT('豆腐店50元代金券#', n)
        WHEN 1 THEN CONCAT('火锅100元代金券#', n)
        WHEN 2 THEN CONCAT('咖啡30元代金券#', n)
        WHEN 3 THEN CONCAT('亲子乐园双人票#', n)
        ELSE CONCAT('KTV欢唱3小时券#', n)
    END,
    CONCAT('搜索联调优惠券第', n, '张'),
    '到店核销，不兑现，不找零',
    2000 + (n % 10) * 500,
    5000 + (n % 6) * 1000,
    1,
    1,
    TIMESTAMPADD(DAY, n % 120, '2026-01-04 12:00:00'),
    TIMESTAMPADD(DAY, n % 120, '2026-01-04 12:00:00')
FROM tmp_seed_numbers;

-- 与 200 张秒杀券一一对应。
INSERT IGNORE INTO tb_seckill_voucher
    (voucher_id, stock, create_time, begin_time, end_time, update_time)
SELECT
    1000 + n,
    100 + n * 5,
    TIMESTAMPADD(DAY, n % 120, '2026-01-04 12:00:00'),
    '2026-01-01 00:00:00',
    '2027-12-31 23:59:59',
    TIMESTAMPADD(DAY, n % 120, '2026-01-04 12:00:00')
FROM tmp_seed_numbers;

-- 新增 200 条优惠券订单；用户与券的组合唯一。
INSERT IGNORE INTO tb_voucher_order
    (id, user_id, voucher_id, pay_type, status, create_time, pay_time,
     use_time, refund_time, update_time)
SELECT
    202610310000000 + n,
    9 + n,
    1000 + n,
    1 + (n % 3),
    1 + (n % 6),
    TIMESTAMPADD(DAY, n % 120, '2026-01-05 12:00:00'),
    CASE WHEN n % 6 = 0 THEN NULL
         ELSE TIMESTAMPADD(DAY, n % 120, '2026-01-05 12:05:00') END,
    CASE WHEN n % 6 = 2 THEN TIMESTAMPADD(DAY, n % 120, '2026-01-06 18:00:00')
         ELSE NULL END,
    CASE WHEN n % 6 = 5 THEN TIMESTAMPADD(DAY, n % 120, '2026-01-07 09:00:00')
         ELSE NULL END,
    TIMESTAMPADD(DAY, n % 120, '2026-01-05 12:00:00')
FROM tmp_seed_numbers;

-- 新增 200 条签到记录。
INSERT IGNORE INTO tb_sign
    (id, user_id, year, month, date, is_backup)
SELECT
    1000 + n,
    9 + n,
    YEAR(TIMESTAMPADD(DAY, n % 60, '2026-09-01 00:00:00')),
    MONTH(TIMESTAMPADD(DAY, n % 60, '2026-09-01 00:00:00')),
    DATE(TIMESTAMPADD(DAY, n % 60, '2026-09-01 00:00:00')),
    n % 5
FROM tmp_seed_numbers;

COMMIT;

-- 显式大 ID 插入不会自动推进部分表的 AUTO_INCREMENT，需同步到测试数据之后。
ALTER TABLE tb_user AUTO_INCREMENT = 1211;
ALTER TABLE tb_shop AUTO_INCREMENT = 1201;
ALTER TABLE tb_blog AUTO_INCREMENT = 1201;
ALTER TABLE tb_blog_comments AUTO_INCREMENT = 1201;
ALTER TABLE tb_follow AUTO_INCREMENT = 1201;
ALTER TABLE tb_voucher AUTO_INCREMENT = 1201;
ALTER TABLE tb_sign AUTO_INCREMENT = 1201;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_numbers;
