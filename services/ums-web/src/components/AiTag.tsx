import { Tag } from 'antd';
import { RobotOutlined } from '@ant-design/icons';

/**
 * CNT-003：AI 生成内容统一标记。
 * 凡 aiGeneratedFields 数组里标注的字段（如 summary / tags），展示时旁加此标签，
 * 四个模块统一使用，不得各自设计。
 */
export function AiTag({ label = 'AI生成' }: { label?: string }) {
  return (
    <Tag className="ai-generated-tag" icon={<RobotOutlined />} color="purple">
      {label}
    </Tag>
  );
}
