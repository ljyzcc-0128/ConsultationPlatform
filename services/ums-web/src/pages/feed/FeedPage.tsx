import { useMemo } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Badge, Button, Card, List, Space, Tabs, Tag, Tooltip, Typography, message } from 'antd';
import {
  CheckOutlined,
  ThunderboltOutlined,
  LinkOutlined,
  MailOutlined,
  WechatOutlined,
  BellOutlined,
} from '@ant-design/icons';
import { useBatchMarkRead, useFeed, type FeedTab } from '../../hooks/useFeed';
import type { FeedItem, PushRecord } from '../../api/types';
import { CHANNEL_TEXT, PUSH_MSG_STATUS_TEXT } from '../../api/types';
import { AiTag } from '../../components/AiTag';
import { EmptyState } from '../../components/EmptyState';
import { ErrorState } from '../../components/ErrorState';
import { ListSkeleton } from '../../components/ListSkeleton';

const TABS: Array<{ key: FeedTab; label: string }> = [
  { key: 'today', label: '今日重点' },
  { key: 'major_changes', label: '重大变化' },
  { key: 'updates', label: '普通更新' },
  { key: 'pushes', label: '推送记录' },
];

const CHANNEL_ICON: Record<string, React.ReactNode> = {
  IN_APP: <BellOutlined />,
  EMAIL: <MailOutlined />,
  WECHAT: <WechatOutlined />,
};

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
    <List.Item
      style={{
        borderLeft: isMajor ? '4px solid #fa8c16' : undefined,
        paddingLeft: isMajor ? 12 : 16,
        paddingRight: 16,
        background: item.readStatus ? undefined : '#fafcff',
        borderRadius: 4,
        cursor: 'pointer',
      }}
      onClick={() => onOpen(item.articleId)}
    >
      <List.Item.Meta
        avatar={
          !item.readStatus ? (
            <Tooltip title="未读">
              <Badge color="#1677ff" status="processing" data-testid="unread-dot" />
            </Tooltip>
          ) : (
            <span style={{ display: 'inline-block', width: 8 }} />
          )
        }
        title={
          <Space size={8} wrap>
            {isMajor && (
              <Tag icon={<ThunderboltOutlined />} color="warning">
                重大变化
              </Tag>
            )}
            <Typography.Text
              strong={!item.readStatus}
              style={{ fontSize: 15 }}
            >
              {item.title}
            </Typography.Text>
          </Space>
        }
        description={
          <Space direction="vertical" size={4} style={{ width: '100%' }}>
            {item.summary && (
              <Typography.Paragraph
                type="secondary"
                ellipsis={{ rows: 2, expandable: true, symbol: '展开' }}
                style={{ marginBottom: 0 }}
              >
                {item.summary}
              </Typography.Paragraph>
            )}
            <Space size={12} wrap>
              {item.aiGeneratedFields.includes('summary') && <AiTag />}
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {item.sourceName}
              </Typography.Text>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                {formatTime(item)}
              </Typography.Text>
              {item.language === 'en' && <Tag style={{ fontSize: 11 }}>EN</Tag>}
            </Space>
          </Space>
        }
      />
    </List.Item>
  );
}

function PushRecordRow({ record }: { record: PushRecord }) {
  const statusColor =
    record.status === 'CLICKED'
      ? 'green'
      : record.status === 'DELIVERED' || record.status === 'SENT'
        ? 'blue'
        : 'red';
  return (
    <List.Item>
      <List.Item.Meta
        avatar={CHANNEL_ICON[record.channel]}
        title={record.contentTitle}
        description={
          <Space size={12}>
            <Tag>{CHANNEL_TEXT[record.channel]}</Tag>
            <Tag color={statusColor}>{PUSH_MSG_STATUS_TEXT[record.status]}</Tag>
            <Typography.Text type="secondary">{record.sentAt}</Typography.Text>
          </Space>
        }
      />
    </List.Item>
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

  return (
    <Card
      title="我的情报"
      extra={
        tab !== 'pushes' ? (
          <Space>
            <Typography.Text type="secondary">
              {unreadCount > 0 ? `${unreadCount} 条未读` : '全部已读'}
            </Typography.Text>
            <Button
              icon={<CheckOutlined />}
              onClick={markAllRead}
              loading={batchMarkRead.isPending}
            >
              全部标记已读
            </Button>
          </Space>
        ) : null
      }
    >
      <Tabs
        activeKey={tab}
        onChange={(key) => setSearchParams({ tab: key })}
        items={TABS.map((t) => ({ key: t.key, label: t.label }))}
      />
      {isLoading ? (
        <ListSkeleton rows={5} />
      ) : isError ? (
        <ErrorState error={error} onRetry={() => refetch()} />
      ) : tab === 'pushes' ? (
        <List
          dataSource={items as unknown as PushRecord[]}
          renderItem={(record) => <PushRecordRow record={record} />}
          locale={{
            emptyText: (
              <EmptyState
                description="还没有推送记录。内容达到推送频率阈值后，会按关注规则的渠道推送给您。"
                actionText="去配置关注规则"
                onAction={() => navigate('/feed/subscriptions')}
              />
            ),
          }}
        />
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
        <List
          dataSource={feedItems}
          renderItem={(item) => (
            <FeedItemRow
              item={item}
              onOpen={(id) => {
                if (!item.readStatus) batchMarkRead.mutate([id]);
                navigate(`/center/${id}`);
              }}
            />
          )}
        />
      )}
      <div style={{ marginTop: 16, textAlign: 'right' }}>
        <Link to="/feed/subscriptions">
          <Button type="link" icon={<LinkOutlined />}>
            管理我的关注（内容域 / 主题 / 企业 / 关键词 / 指标 / 事件）
          </Button>
        </Link>
      </div>
    </Card>
  );
}
