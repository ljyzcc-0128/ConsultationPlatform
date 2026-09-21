import { Table, Tag, Typography } from 'antd';

type DiffRow = {
  path: string;
  before?: string;
  after?: string;
  kind: 'added' | 'removed' | 'changed';
};

/** 扁平化 JSON 到 path → value */
function flatten(value: unknown, prefix = '', out: Map<string, string> = new Map()): Map<string, string> {
  if (value === null || value === undefined) {
    if (prefix) out.set(prefix, 'null');
    return out;
  }
  if (Array.isArray(value)) {
    if (value.length === 0 && prefix) out.set(prefix, '[]');
    value.forEach((v, i) => flatten(v, prefix ? `${prefix}[${i}]` : `[${i}]`, out));
    return out;
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>);
    if (entries.length === 0 && prefix) out.set(prefix, '{}');
    for (const [k, v] of entries) {
      flatten(v, prefix ? `${prefix}.${k}` : k, out);
    }
    return out;
  }
  out.set(prefix, String(value));
  return out;
}

function diffRows(before: unknown, after: unknown): DiffRow[] {
  const beforeFlat = flatten(before);
  const afterFlat = flatten(after);
  const rows: DiffRow[] = [];
  const paths = new Set([...beforeFlat.keys(), ...afterFlat.keys()]);
  for (const path of paths) {
    const b = beforeFlat.get(path);
    const a = afterFlat.get(path);
    if (b === undefined && a !== undefined) {
      rows.push({ path, after: a, kind: 'added' });
    } else if (b !== undefined && a === undefined) {
      rows.push({ path, before: b, kind: 'removed' });
    } else if (b !== a) {
      rows.push({ path, before: b, after: a, kind: 'changed' });
    }
  }
  return rows.sort((x, y) => x.path.localeCompare(y.path));
}

const KIND_META: Record<DiffRow['kind'], { label: string; color: string }> = {
  added: { label: '新增', color: 'green' },
  removed: { label: '移除', color: 'red' },
  changed: { label: '变更', color: 'orange' },
};

/**
 * 通用 JSON diff 组件（指南 6.3）：高亮变化字段，替代把 JSON 字符串直接堆在页面上。
 * before 可省略（仅有变更后快照的场景，全部显示为「新增」高亮）。
 */
export function JsonDiff({ before, after }: { before?: unknown; after: unknown }) {
  let parsedBefore = before;
  let parsedAfter = after;
  if (typeof before === 'string') {
    try {
      parsedBefore = JSON.parse(before);
    } catch {
      /* keep raw */
    }
  }
  if (typeof after === 'string') {
    try {
      parsedAfter = JSON.parse(after);
    } catch {
      /* keep raw */
    }
  }
  const rows = diffRows(parsedBefore, parsedAfter);
  if (rows.length === 0) {
    return <Typography.Text type="secondary">无字段变化</Typography.Text>;
  }
  return (
    <Table<DiffRow>
      size="small"
      rowKey="path"
      dataSource={rows}
      pagination={false}
      aria-label="json-diff"
      columns={[
        {
          title: '字段',
          dataIndex: 'path',
          width: '35%',
          render: (path: string) => <Typography.Text code>{path}</Typography.Text>,
        },
        {
          title: '变更前',
          dataIndex: 'before',
          width: '27%',
          render: (v?: string) => (v === undefined ? <Typography.Text type="secondary">—</Typography.Text> : <Typography.Text delete>{v}</Typography.Text>),
        },
        {
          title: '变更后',
          dataIndex: 'after',
          width: '27%',
          render: (v?: string) => (v === undefined ? <Typography.Text type="secondary">—</Typography.Text> : <Typography.Text strong>{v}</Typography.Text>),
        },
        {
          title: '类型',
          dataIndex: 'kind',
          width: '11%',
          render: (kind: DiffRow['kind']) => (
            <Tag color={KIND_META[kind].color}>{KIND_META[kind].label}</Tag>
          ),
        },
      ]}
    />
  );
}
