import { useMemo } from 'react';
import { Form, Input, Modal, Select } from 'antd';
import type { FormInstance } from 'antd';
import type { XxlJobInfo } from '../../api/types';

export const SCHEDULE_TYPE: Record<string, string> = {
  CRON: 'CRON',
  FIX_RATE: '固定速率',
  FIX_DELAY: '固定延迟',
  NONE: '无',
};

const MISFIRE_OPTIONS = [
  { value: 'DO_NOTHING', label: '忽略（不触发补偿）' },
  { value: 'FIRE_ONCE_NOW', label: '立即补偿执行一次' },
];

const ROUTE_OPTIONS = [
  { value: 'FIRST', label: '第一个' },
  { value: 'ROUND', label: '轮询' },
  { value: 'RANDOM', label: '随机' },
  { value: 'FAILOVER', label: '故障转移' },
  { value: 'BUSYOVER', label: '忙碌转移' },
];

const BLOCK_OPTIONS = [
  { value: 'SERIAL_EXECUTION', label: '单机串行' },
  { value: 'DISCARD_LATER', label: '丢弃后续调度' },
  { value: 'COVER_EARLY', label: '覆盖之前调度' },
];

export interface JobFormValues {
  jobDesc: string;
  executorHandler: string;
  scheduleType: string;
  scheduleConf: string;
  executorParam?: string;
  author?: string;
  alarmEmail?: string;
  misfireStrategy: string;
  executorRouteStrategy: string;
  executorBlockStrategy: string;
}

interface JobFormModalProps {
  open: boolean;
  editing: XxlJobInfo | null;
  form: FormInstance<JobFormValues>;
  confirmLoading: boolean;
  onOk: () => void;
  onCancel: () => void;
}

/** 任务新增/编辑弹窗 */
export function JobFormModal({ open, editing, form, confirmLoading, onOk, onCancel }: JobFormModalProps) {
  const scheduleType = Form.useWatch('scheduleType', form);
  const isCron = scheduleType !== 'FIX_RATE';

  return (
    <Modal
      title={editing ? `编辑任务 #${editing.id}` : '新增定时任务'}
      open={open}
      onOk={onOk}
      onCancel={onCancel}
      confirmLoading={confirmLoading}
      okText={editing ? '保存' : '创建'}
      cancelText="取消"
      width={560}
      destroyOnClose
    >
      <Form<JobFormValues>
        form={form}
        layout="vertical"
        requiredMark="optional"
        style={{ marginTop: 8 }}
      >
        <Form.Item
          name="jobDesc"
          label="任务描述"
          rules={[{ required: true, message: '请输入任务描述' }]}
        >
          <Input placeholder="如：信源采集调度（到期信源自动触发）" maxLength={100} showCount />
        </Form.Item>

        <Form.Item
          name="executorHandler"
          label="JobHandler"
          rules={[{ required: true, message: '请输入 JobHandler（与 @XxlJob 注解一致）' }]}
        >
          <Input placeholder="如：crawlScheduledJob" />
        </Form.Item>

        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item name="scheduleType" label="调度类型" style={{ width: 160 }} initialValue="CRON">
            <Select
              options={[
                { value: 'CRON', label: 'CRON' },
                { value: 'FIX_RATE', label: '固定速率（秒）' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="scheduleConf"
            label={isCron ? 'Cron 表达式' : '固定速率（秒）'}
            rules={[
              { required: true, message: isCron ? '请输入 Cron 表达式' : '请输入速率秒数' },
              ...(isCron
                ? []
                : [{ pattern: /^\d+$/, message: '请输入正整数秒数' }]),
            ]}
            style={{ flex: 1 }}
          >
            <Input placeholder={isCron ? '如：0 */30 * * * ?' : '如：60'} />
          </Form.Item>
        </div>

        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item name="author" label="负责人" style={{ flex: 1 }}>
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item
            name="alarmEmail"
            label="报警邮件"
            style={{ flex: 1 }}
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input placeholder="选填，失败时告警" />
          </Form.Item>
        </div>

        <Form.Item name="executorParam" label="任务参数">
          <Input.TextArea rows={2} placeholder="选填，执行时透传给 JobHandler" />
        </Form.Item>

        <div style={{ display: 'flex', gap: 12 }}>
          <Form.Item name="misfireStrategy" label="过期策略" style={{ flex: 1 }} initialValue="DO_NOTHING">
            <Select options={MISFIRE_OPTIONS} />
          </Form.Item>
          <Form.Item name="executorRouteStrategy" label="路由策略" style={{ flex: 1 }} initialValue="FIRST">
            <Select options={ROUTE_OPTIONS} />
          </Form.Item>
          <Form.Item name="executorBlockStrategy" label="阻塞策略" style={{ flex: 1 }} initialValue="SERIAL_EXECUTION">
            <Select options={BLOCK_OPTIONS} />
          </Form.Item>
        </div>
      </Form>
    </Modal>
  );
}

/** 由列表数据统计运行/停止数量 */
export function useJobStats(list: XxlJobInfo[] | undefined) {
  return useMemo(() => {
    const running = (list ?? []).filter((j) => j.triggerStatus === 1).length;
    return { total: list?.length ?? 0, running, stopped: (list?.length ?? 0) - running };
  }, [list]);
}

export { MISFIRE_OPTIONS, ROUTE_OPTIONS, BLOCK_OPTIONS };
