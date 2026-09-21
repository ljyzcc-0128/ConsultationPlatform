/**
 * MSW mock 数据：仅保留搜索兜底静态新闻列表。
 * Phase 6.2/6.3 接口（供应链看板、采集任务）已全部切真实后端，相关 mock 数据已移除。
 * 供应链/采集任务类型定义已迁移至 src/api/types.ts。
 */

// ---------- 搜索兜底新闻 ----------

interface RawNewsItem {
  articleId: string;
  title: string;
  summary: string | null;
  sourceName: string;
  publishDate: string;
  publishTime: string | null;
  language: string;
  category: string[];
}

/** 后端不可用时的静态兜底数据（搜索 mock 共用） */
export const FALLBACK_NEWS: RawNewsItem[] = [
  {
    articleId: 'mock-0001',
    title: '国家能源局公示第六批能源领域首台（套）重大技术装备清单，12 项新型储能技术装备入围',
    summary:
      '国家能源局公示第六批首台（套）清单，90 项装备中 12 项涉及新型储能；\n包括直流耦合光储一体化、千万千瓦级沙戈荒风光火储协同控制、兆瓦级超高温热泵储能等；\n对储能设备商意味着示范应用通道打开，建议跟踪依托工程招标动态。',
    sourceName: '中国储能网',
    publishDate: '2026-09-18',
    publishTime: '2026-09-18T08:00:00',
    language: 'zh-CN',
    category: ['BESS', 'Policy', 'Technology'],
  },
  {
    articleId: 'mock-0002',
    title: '碳酸锂现货价格连续第三周上行，储能电芯成本压力显现',
    summary:
      '电池级碳酸锂现货均价环比上涨 2.3%，连续三周上行；\n材料成本传导下磷酸铁锂电芯报价小幅上调，储能系统集成商毛利承压；\n建议关注 Q4 排产与长协谈判窗口。',
    sourceName: 'SMM 上海有色网',
    publishDate: '2026-09-18',
    publishTime: '2026-09-18T06:30:00',
    language: 'zh-CN',
    category: ['Supply Chain', 'Market Analysis'],
  },
  {
    articleId: 'mock-0003',
    title: 'Hithium unveils 20,000-cycle sodium-ion cell and 4MWh BESS',
    summary:
      '海辰储能发布 N785Ah 钠离子电芯与 4MWh BESS 系统，宣称 20000 次循环、30 年寿命；\n钠电与锂电产线兼容度高，可快速规模量产；\n对锂电储能厂商形成成本与寿命双维度竞争压力。',
    sourceName: 'Energy-Storage.news',
    publishDate: '2026-09-17',
    publishTime: '2026-09-17T13:25:53',
    language: 'en',
    category: ['BESS', 'Technology'],
  },
  {
    articleId: 'mock-0004',
    title: '澳大利亚新南威尔士州 3GWh 电池储能项目全部投运',
    summary:
      'NSW 第二轮容量招标的电池储能与需求响应项目全部投运；\n合计约 1GW/3GWh，投资超 18 亿澳元；\n澳大利亚 firming tender 机制对国内储能出海企业具有参考价值。',
    sourceName: 'Energy-Storage.news',
    publishDate: '2026-09-18',
    publishTime: '2026-09-18T00:25:42',
    language: 'en',
    category: ['Renewable Energy', 'Market Analysis'],
  },
  {
    articleId: 'mock-0005',
    title: '两部门印发重要工业品低价无序竞争成本核算通知',
    summary:
      '国家发改委、市场监管总局联合印发成本核算通知，明确低价无序竞争认定中的成本核算规则；\n行业协会可在指导下测算行业平均成本；\n储能电芯环节的价格竞争或纳入重点监测。',
    sourceName: '中国储能网',
    publishDate: '2026-09-11',
    publishTime: '2026-09-11T09:00:00',
    language: 'zh-CN',
    category: ['Policy', 'Regulation', 'Finance'],
  },
  {
    articleId: 'mock-0006',
    title: '广东省新型储能电站建设运行管理办法征求意见',
    summary:
      '广东就新型储能电站管理办法公开征求意见，覆盖备案、并网、安全、退役全流程；\n明确储能电站消防安全与并网性能要求；\n华南区域储能项目开发合规成本需重新评估。',
    sourceName: '广东水力与新能源勘测设计协会',
    publishDate: '2026-09-16',
    publishTime: '2026-09-16T10:00:00',
    language: 'zh-CN',
    category: ['Policy', 'Safety'],
  },
];
