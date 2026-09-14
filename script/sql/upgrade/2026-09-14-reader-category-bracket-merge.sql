-- 合并来源站点产生的中括号分类重复数据。
-- 统一使用不带外层中括号的分类名称，并保留原有无括号分类记录。
-- 仅修改分类字段和分类字典，不修改作品、章节及采集状态。

START TRANSACTION;

UPDATE reader_work
SET category_name = CASE category_name
  WHEN '[仙侠修真]' THEN '仙侠修真'
  WHEN '[玄幻魔法]' THEN '玄幻魔法'
  WHEN '[言情小说]' THEN '言情小说'
  WHEN '[都市小说]' THEN '都市小说'
  ELSE category_name
END
WHERE category_name IN ('[仙侠修真]', '[玄幻魔法]', '[言情小说]', '[都市小说]');

UPDATE reader_source_task
SET category_name = CASE category_name
  WHEN '[仙侠修真]' THEN '仙侠修真'
  WHEN '[玄幻魔法]' THEN '玄幻魔法'
  WHEN '[言情小说]' THEN '言情小说'
  WHEN '[都市小说]' THEN '都市小说'
  ELSE category_name
END
WHERE category_name IN ('[仙侠修真]', '[玄幻魔法]', '[言情小说]', '[都市小说]');

UPDATE reader_source_task_book
SET category_name = CASE category_name
  WHEN '[仙侠修真]' THEN '仙侠修真'
  WHEN '[玄幻魔法]' THEN '玄幻魔法'
  WHEN '[言情小说]' THEN '言情小说'
  WHEN '[都市小说]' THEN '都市小说'
  ELSE category_name
END
WHERE category_name IN ('[仙侠修真]', '[玄幻魔法]', '[言情小说]', '[都市小说]');

UPDATE reader_import_task
SET category_name = CASE category_name
  WHEN '[仙侠修真]' THEN '仙侠修真'
  WHEN '[玄幻魔法]' THEN '玄幻魔法'
  WHEN '[言情小说]' THEN '言情小说'
  WHEN '[都市小说]' THEN '都市小说'
  ELSE category_name
END
WHERE category_name IN ('[仙侠修真]', '[玄幻魔法]', '[言情小说]', '[都市小说]');

-- 只删除存在对应无括号主记录的重复字典项，避免误删其他分类。
DELETE duplicate_category
FROM reader_work_category duplicate_category
JOIN reader_work_category canonical_category
  ON canonical_category.category_name = CASE duplicate_category.category_name
    WHEN '[仙侠修真]' THEN '仙侠修真'
    WHEN '[玄幻魔法]' THEN '玄幻魔法'
    WHEN '[言情小说]' THEN '言情小说'
    WHEN '[都市小说]' THEN '都市小说'
  END
WHERE duplicate_category.category_name IN ('[仙侠修真]', '[玄幻魔法]', '[言情小说]', '[都市小说]')
  AND duplicate_category.id <> canonical_category.id;

UPDATE reader_work_category
SET normalized_name = LOWER(REPLACE(category_name, ' ', ' '))
WHERE category_name IN ('仙侠修真', '玄幻魔法', '言情小说', '都市小说');

COMMIT;
