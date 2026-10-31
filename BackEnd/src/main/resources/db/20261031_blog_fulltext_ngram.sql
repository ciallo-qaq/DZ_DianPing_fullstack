-- 为已有 hmdp 数据库升级博客搜索索引。
-- MySQL 8 自带 ngram parser，无需额外插件。

USE hmdp;

SET @title_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tb_blog'
      AND INDEX_NAME = 'idx_title'
);
SET @title_index_sql = IF(
    @title_index_exists = 0,
    'ALTER TABLE tb_blog ADD INDEX idx_title(title) USING BTREE',
    'SELECT 1'
);
PREPARE title_index_statement FROM @title_index_sql;
EXECUTE title_index_statement;
DEALLOCATE PREPARE title_index_statement;

SET @content_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'tb_blog'
      AND INDEX_NAME = 'ft_blog_content'
);
SET @content_index_sql = IF(
    @content_index_exists = 0,
    'ALTER TABLE tb_blog ADD FULLTEXT INDEX ft_blog_content(content) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE content_index_statement FROM @content_index_sql;
EXECUTE content_index_statement;
DEALLOCATE PREPARE content_index_statement;
