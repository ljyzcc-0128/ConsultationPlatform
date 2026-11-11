-- ============================================================
-- V9 新信源批次一/二：14 个储能资讯信源接入（cp_source 幂等种子）
-- 依据：2026-11-10 信源勘察结论（详见 services/ums-crawler-py/selectors/*.yaml 头部注释）
-- 口径：与 V1 种子对齐（region/language/category 参照既有种子）；
--       频率均 1/day；第一批新闻/政策类 P1，第二批低频监测型 P2
--       （监测型每日抓取列表页，Java 侧按规范化 URL 去重实现"有更新才入库"）
-- 幂等：INSERT ... ON DUPLICATE KEY UPDATE，重复执行不产生副作用
-- ============================================================

INSERT INTO `cp_source`
  (`source_id`, `source_name`, `region`, `source_type`, `entry_url`, `crawl_enabled`, `crawl_frequency`, `priority`, `language`, `category`, `crawl_status`, `remark`) VALUES
  -- ===== 第一批：新闻/政策，每日增量（P1）=====
  ('INT-04', 'ESS News', '全球', '储能行业媒体',
   'https://www.ess-news.com/category/markets/policy/', 1, '1/day', 'P1', 'en', '["BESS", "Policy"]', 'NotRun',
   'WordPress/pvmagazine 主题；robots Crawl-delay=10 已按 10s 限流；文章 /YYYY/MM/DD/slug/ 结构已联调（2026-11-10）'),
  ('INT-05', 'FERC', '海外/美国为主', '监管机构/政策发布',
   'https://www.ferc.gov/news-events/news/news-releases-headlines', 0, '1/day', 'P1', 'en', '["Policy", "Power Market"]', 'NotRun',
   '官网 WAF 拦截自动化请求（HTTP 403），须先修复访问（白名单/官方接口），不得对抗性绕过；选择器按 Drupal 标准结构预置，恢复后联调（2026-11-10）'),
  ('INT-06', 'ESG Today', '全球', 'ESG/储能行业媒体',
   'https://www.esgtoday.com/category/esg-news/', 1, '1/day', 'P1', 'en', '["BESS", "Policy"]', 'NotRun',
   'WordPress 架构静态可读，含储能/能源转型报道；robots 对 UMSBot 合规（2026-11-10）'),
  ('INT-07', 'DOE', '海外/美国为主', '政府机构/政策发布',
   'https://www.energy.gov/newsroom', 1, '1/day', 'P1', 'en', '["BESS", "Policy"]', 'NotRun',
   'Drupal 架构，文章 /articles/<slug>；正文 div.field--name-body，静态联调通过（2026-11-10）'),
  ('INT-08', 'Energy Trend', '全球/亚洲为主', '行业媒体/市场研究',
   'https://www.energytrend.com/news/', 1, '1/day', 'P1', 'en', '["BESS", "Power Market"]', 'NotRun',
   'TrendForce 旗下；多栏目 /news/ + /research/；robots 禁 /page/ 分页故单页抓取（2026-11-10）'),
  ('CN-03', '国际储能网', '中国', '行业媒体/政策聚合',
   'https://mchuneng.in-en.com/', 1, '1/day', 'P1', 'zh-CN', '["BESS", "Policy"]', 'NotRun',
   '移动端入口；栏目页在 chuneng.in-en.com（policy/ees/ees-cailiao/xiangmu）；标准 UA 超时改用浏览器 UA，站点偶发断连有重试（2026-11-10）'),
  ('CN-04', '时代储能网', '中国', '行业媒体',
   'https://eraes.com.cn/sy', 1, '1/day', 'P1', 'zh-CN', '["BESS", "Policy"]', 'NotRun',
   '全站 Vue SPA 需 Playwright（js_render）；栏目为动态路由，首页聚合各栏目最新内容单入口抓取；渲染等待约 10s（2026-11-10）'),
  ('CN-05', '数字储能网', '中国', '行业媒体',
   'https://www.desn.com.cn/index.html', 1, '1/day', 'P1', 'zh-CN', '["BESS", "Policy"]', 'NotRun',
   'SSL 证书过期（verify_ssl=false）；勘察时 https 返回 503 升级维护页、http 302 至 escn.com.cn（CN-01 已覆盖）；恢复后须复检选择器（2026-11-10）'),
  -- ===== 第二批：低频变更监测型，日抓列表 + Java 侧 URL 去重"有更新才入库"（P2）=====
  ('INT-09', '欧盟法规监测', '海外/欧洲为主', '政策聚合/官方监测',
   'https://european-union.europa.eu/news-and-events_en', 1, '1/day', 'P2', 'en', '["Policy", "BESS"]', 'NotRun',
   '监测型去重；条目跨域（ec.europa.eu/commission.europa.eu）已 normalize；presscorner 详情为 Angular SPA 需 Playwright（2026-11-10）'),
  ('CN-06', '阳光电源', '中国', '企业官网/厂商资讯',
   'https://www.sungrowpower.com/en/news-media-news-list', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；中文站 /zh 不可达(404)，唯一可用入口为英文站故 language=en；Next.js SPA 需 Playwright（2026-11-10）'),
  ('CN-07', '海博思创', '中国', '企业官网/厂商资讯',
   'https://www.hyperstrong.com/news/company-news', 1, '1/day', 'P2', 'zh-CN', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；三栏目 company-news/exhibition-events/industry-insights；列表与详情均 JS 渲染 js_render（2026-11-10）'),
  ('CN-08', 'CATL', '中国', '企业官网/厂商资讯',
   'https://www.catl.com/news/', 1, '1/day', 'P2', 'zh-CN', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；静态列表 /news/NNNNN.html；robots.txt 为 200 空 404 页按允许处理（2026-11-10）'),
  ('CN-09', 'CNAS', '中国', '认证机构/标准动态',
   'https://www.cnas.org.cn/', 1, '1/day', 'P2', 'zh-CN', '["Policy", "BESS"]', 'NotRun',
   '监测型去重；通知公告/认可公告栏目列表 JS 装载，采用首页静态聚合块抓取 /zxzx/*/art/ 文章（2026-11-10）'),
  ('CN-10', 'SAMR标准平台', '中国', '标准平台/官方监测',
   'https://std.samr.gov.cn/gb/gbOpinion', 1, '1/day', 'P2', 'zh-CN', '["Policy", "BESS"]', 'NotRun',
   '监测型去重；国家标准列表为纯 JS 应用（gbQueryPage XHR），js_render 渲染表格行；详情 gbDetailed?id 链接（2026-11-10）')
ON DUPLICATE KEY UPDATE
  `source_name` = VALUES(`source_name`),
  `region` = VALUES(`region`),
  `source_type` = VALUES(`source_type`),
  `entry_url` = VALUES(`entry_url`),
  `crawl_enabled` = VALUES(`crawl_enabled`),
  `crawl_frequency` = VALUES(`crawl_frequency`),
  `priority` = VALUES(`priority`),
  `language` = VALUES(`language`),
  `category` = VALUES(`category`),
  `remark` = VALUES(`remark`);
