-- ============================================================
-- V10 新信源批次三：10 个海外低频监测信源接入（cp_source 幂等种子）
-- 依据：2026-11-12 信源勘察结论（详见 services/ums-crawler-py/selectors/int_1*.yaml 头部注释）
-- 口径：与 V9 批次二对齐（监测型 P2，日抓列表页 + Java 侧规范化 URL 去重"有更新才入库"）
-- 说明：M#7 ESN Premium（同站 premium 栏目）因 Cloudflare 挑战未接入，待条件成熟
-- 幂等：INSERT ... ON DUPLICATE KEY UPDATE，重复执行不产生副作用
-- ============================================================

INSERT INTO `cp_source`
  (`source_id`, `source_name`, `region`, `source_type`, `entry_url`, `crawl_enabled`, `crawl_frequency`, `priority`, `language`, `category`, `crawl_status`, `remark`) VALUES
  ('INT-10', 'Ember', '全球', '能源转型数据/研究机构',
   'https://ember-energy.org/latest-insights/', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；Cloudflare 挑战拦截 httpx，js_render 渲染；洞察/数据集发布列表（2026-11-12）'),
  ('INT-11', 'IRENA', '全球', '国际组织/数据出版物',
   'https://www.irena.org/Data', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；Azure WAF + SPA 拦截 httpx，js_render 渲染；能源转型展望/统计数据页（2026-11-12）'),
  ('INT-12', 'IEA', '全球', '国际组织/数据出版物',
   'https://www.iea.org/news', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；article 列表 SSR 可读，news 单入口（/reports 为按钮云无日期不兼容未混抓）；分页 ?page=N；UA UMSBot 可用（2026-11-12）'),
  ('INT-13', 'SolarPower Europe', '全球/欧洲为主', '行业协会/报告出版物',
   'https://www.solarpowereurope.org/news', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；Nuxt SPA，/insights* 均 404 唯一可用入口为 /news；js_render 9s，div.publication 列表，分页参数站点忽略仅单页；press-release 详情部分无日期元素（2026-11-12）'),
  ('INT-14', 'Nord Pool', '海外/欧洲为主', '电力交易所/市场数据',
   'https://www.nordpoolgroup.com/en/', 1, '1/day', 'P2', 'en', '["Power Market", "BESS"]', 'NotRun',
   '监测型去重；Episerver+Alpine.js，新闻列表 XHR 注入，js_render 12s 抓首页 Latest news 区块（li.message）；白皮书非列表页；站点有新闻稿 RSS 可后续切 XML（2026-11-12）'),
  ('INT-15', 'AEMO', '海外/澳大利亚', '电力市场运营/报告出版物',
   'https://www.aemo.com.au/library/major-publications', 0, '1/day', 'P2', 'en', '["Power Market", "BESS"]', 'NotRun',
   '监测型去重；Cloudflare 挑战拦截 httpx（INT-10 同款，连 Playwright 无头也无法过指纹校验，需参考 Wayback 存档方案）；探测未完成 yaml 未产出，联调后启用（2026-11-12）'),
  ('INT-16', 'ACERA', '海外/智利', '行业协会/报告出版物',
   'https://www.acera.cl/noticias/', 1, '1/day', 'P2', 'es', '["Power Market", "BESS"]', 'NotRun',
   '监测型去重；智利新能源协会，WordPress SSR /noticias/（/centro-de-informacion 为导航聚合页）；西语列表日期 dateparser 不识别，详情页 ISO time 兜底（2026-11-12）'),
  ('INT-17', 'Energy Storage Europe', '海外/欧洲为主', '行业协会/报告出版物',
   'https://energystorageeurope.eu/publications/activity-reports/', 1, '1/day', 'P2', 'en', '["BESS", "Policy"]', 'NotRun',
   '监测型去重；静态站，activity-reports/policy-papers/reports-and-studies/current-news 多栏目（2026-11-12）'),
  ('INT-18', 'Baringa', '全球', '咨询机构/洞察出版物',
   'https://www.baringa.com/en/insights/', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；Vue SPA 列表客户端渲染 js_render 9s（div.listing__results>div.card×12/页，无 URL 分页）；日期 meta article:published_time；正文 div.rich-text__inner（2026-11-12）'),
  ('INT-19', 'Renewables Now', '全球', '行业媒体',
   'https://renewablesnow.com/news/energy-storage/', 1, '1/day', 'P2', 'en', '["BESS", "Power Market"]', 'NotRun',
   '监测型去重；P#4/M#11 双方信源；2026-11-12 复测 403 已解除；屏 UMSBot 需 Chrome UA；储能板块 24 条/页；页内有响应式双份渲染，页内重复由 Java 端 original_url 去重兜底；详情页订阅墙不绕过（2026-11-12）')
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
