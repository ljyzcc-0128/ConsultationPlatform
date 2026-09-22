import { useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { message } from 'antd';
import { useBatchMarkRead, useFeed, type FeedTab } from '../../hooks/useFeed';
import type { FeedItem, PushRecord } from '../../api/types';
import { CHANNEL_TEXT, PUSH_MSG_STATUS_TEXT } from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';

const TABS: Array<{ key: FeedTab; label: string }> = [
  { key: 'today', label: '今日重点' },
  { key: 'major_changes', label: '重大变化' },
  { key: 'updates', label: '普通更新' },
  { key: 'pushes', label: '推送记录' },
];

function formatTime(item: FeedItem): string {
  return item.publishTime ? item.publishTime.replace('T', ' ') : item.publishDate;
}

/** MY-001 列表项：标题+摘要+来源+时间+已读圆点；重大变化视觉区分（MY-002） */
function FeedItemRow({
  item,
  onOpen,
}: {
  item: FeedItem;
  onOpen: (id: string) => void;
}) {
  const isMajor = item.changeType === 'MAJOR';
  return (
    <div className="feed-item">
      <div
        className="feed-card"
        onClick={() => onOpen(item.articleId)}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            onOpen(item.articleId);
          }
        }}
        style={isMajor ? { borderLeft: '3px solid rgba(250,140,22,0.8)' } : undefined}
      >
        <div className="feed-title">{item.title}</div>
        {item.summary && <div className="feed-summary">{item.summary}</div>}
        <div className="feed-meta">
          <span className="src">{item.sourceName}</span>
          <span className="dot" />
          <span>{formatTime(item)}</span>
          {item.language === 'en' && <span className="tag en">EN</span>}
          {!item.readStatus && <span className="tag new">新</span>}
        </div>
        <span className="expand-link" aria-hidden="true">
          展开全文 →
        </span>
      </div>
    </div>
  );
}

function PushRecordRow({ record }: { record: PushRecord }) {
  const statusClass =
    record.status === 'CLICKED'
      ? 'en'
      : record.status === 'DELIVERED' || record.status === 'SENT'
        ? 'en'
        : 'new';
  return (
    <div className="feed-item">
      <div className="feed-card">
        <div className="feed-title">{record.contentTitle}</div>
        <div className="feed-meta">
          <span className="src">{CHANNEL_TEXT[record.channel]}</span>
          <span className="dot" />
          <span>{record.sentAt}</span>
          <span className={`tag ${statusClass}`}>{PUSH_MSG_STATUS_TEXT[record.status]}</span>
        </div>
      </div>
    </div>
  );
}

export function FeedPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const tab = (searchParams.get('tab') as FeedTab) || 'today';
  const { data, isLoading, isError, error, refetch } = useFeed(tab);
  const batchMarkRead = useBatchMarkRead();

  const items = useMemo(() => data ?? [], [data]);
  const feedItems = items as FeedItem[];
  const unreadCount = feedItems.filter((i) => !i.readStatus).length;

  const markAllRead = () => {
    const unreadIds = feedItems.filter((i) => !i.readStatus).map((i) => i.articleId);
    if (unreadIds.length === 0) {
      message.info('没有未读内容');
      return;
    }
    batchMarkRead.mutate(unreadIds, {
      onSuccess: () => message.success('已全部标记为已读'),
    });
  };

  const handleOpen = (item: FeedItem) => (id: string) => {
    if (!item.readStatus) batchMarkRead.mutate([id]);
    navigate(`/center/${id}`);
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>我的情报</h1>
          <div className="sub">
            {tab === 'pushes'
              ? '查看历史推送记录'
              : `今日为你筛选出 ${feedItems.length} 条高相关内容`}
          </div>
        </div>
        {tab !== 'pushes' && (
          <div className="head-actions">
            <span className="unread-pill">
              {unreadCount > 0 ? `${unreadCount} 条未读` : '全部已读'}
            </span>
            <button
              className="btn-ghost"
              type="button"
              onClick={markAllRead}
              disabled={batchMarkRead.isPending}
            >
              ✓ 全部标记已读
            </button>
          </div>
        )}
      </div>

      <div className="pill-tabs">
        {TABS.map((t) => (
          <button
            key={t.key}
            className={`pill-tab${tab === t.key ? ' active' : ''}`}
            type="button"
            onClick={() => setSearchParams({ tab: t.key })}
          >
            {t.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <ListSkeleton rows={5} />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : tab === 'pushes' ? (
        (items as unknown as PushRecord[]).length === 0 ? (
          <EmptyState
            description="还没有推送记录。内容达到推送频率阈值后，会按关注规则的渠道推送给您。"
            actionText="去配置关注规则"
            onAction={() => navigate('/feed/subscriptions')}
          />
        ) : (
          <div className="timeline">
            {(items as unknown as PushRecord[]).map((record) => (
              <PushRecordRow key={record.id} record={record} />
            ))}
          </div>
        )
      ) : feedItems.length === 0 ? (
        <EmptyState
          description={
            tab === 'today'
              ? '今天还没有符合条件的重点内容。内容上线后，这里会按您关注的内容域自动聚合。'
              : '该视图下暂时没有内容。可以先去关注管理页设置关注规则，扩大信息覆盖范围。'
          }
          actionText="去关注管理页设置关注规则"
          onAction={() => navigate('/feed/subscriptions')}
        />
      ) : (
        <div className="timeline">
          {feedItems.map((item) => (
            <FeedItemRow
              key={item.articleId}
              item={item}
              onOpen={handleOpen(item)}
            />
          ))}
        </div>
      )}

      <div className="footer-action">
        <Link to="/feed/subscriptions">
          🔗 管理我的关注（内容域 / 主题 / 企业 / 关键词 / 指标 / 事件）
        </Link>
      </div>
    </>
  );
}
