-- 备用书源自动续采搜索协议。仅登记已确认可用的授权站点搜索表单。
-- 不猜测 bookcheng8 的搜索地址；该站点首页未发现可确认的公开搜索表单。
-- 本脚本幂等，可重复执行。

SET @dushuwo_site_id := (SELECT id FROM reader_source_site WHERE allowed_host = 'm.dushuwo.net' LIMIT 1);
SET @bqg5555_site_id := (SELECT id FROM reader_source_site WHERE allowed_host = 'www.bqg5555.cc' LIMIT 1);

UPDATE reader_source_rule
SET search_url_template = 'https://m.dushuwo.net/search.php',
    selector_json = JSON_SET(
        selector_json,
        '$.search', JSON_OBJECT(
            'method', 'POST',
            'url', 'https://m.dushuwo.net/search.php',
            'params', JSON_OBJECT('s', '{title}', 'type', 'articlename')
        )
    ),
    remark = CONCAT(COALESCE(remark, ''), ' 已验证搜索：POST /search.php，参数 s。'),
    update_time = NOW()
WHERE site_id = @dushuwo_site_id
  AND status = '1'
  AND rule_name = 'dushuwo-分类推荐与连载状态规则';

UPDATE reader_source_rule
SET search_url_template = 'https://www.bqg5555.cc/search/',
    selector_json = JSON_SET(
        selector_json,
        '$.bookList.item', 'ul.txt-list.txt-list-row5 li',
        '$.bookList.title', '.s2 a',
        '$.bookList.author', '.s4',
        '$.bookList.category', '.s1',
        '$.search', JSON_OBJECT(
            'method', 'POST',
            'url', 'https://www.bqg5555.cc/search/',
            'params', JSON_OBJECT('searchkey', '{title}', 'type', 'articlename')
        )
    ),
    remark = CONCAT(COALESCE(remark, ''), ' 已验证搜索：POST /search/，参数 searchkey。'),
    update_time = NOW()
WHERE site_id = @bqg5555_site_id
  AND status = '1'
  AND rule_name = 'bqg5555-分类热门与拆页章节规则';
