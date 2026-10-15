import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Button, Input } from 'antd';
import { CloseOutlined, SendOutlined } from '@ant-design/icons';
import { api, getErrorMessage } from '../api/client';
import type { QaAnswer } from '../api/types';

/** 面板尺寸（定位计算用，需与 CSS 保持一致） */
const PANEL_WIDTH = 340;
const PANEL_HEIGHT = 460;
const ROBOT_SIZE = 56;
const EDGE = 8;

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  /** assistant 专用：原始回答（含模式与引用来源） */
  answer?: QaAnswer;
  /** assistant 专用：是否为错误提示 */
  error?: boolean;
}

/**
 * AI 智能问答悬浮机器人：
 * - 可拖动（Pointer Events，视口内 clamp，拖动与点击按位移阈值区分）
 * - 点击机器人开/关问答面板，调 POST /v1/qa/ask
 * - NEWS 模式回答附引用来源（站内详情 + 原文外链）
 */
export function QaRobot() {
  const [pos, setPos] = useState(() => ({
    x: window.innerWidth - ROBOT_SIZE - 24,
    y: window.innerHeight - ROBOT_SIZE - 120,
  }));
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);

  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    origX: number;
    origY: number;
    moved: boolean;
  } | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  // 新消息自动滚到底部
  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [messages, loading]);

  // 面板定位：优先显示在机器人上方，视口内 clamp
  const panelStyle: React.CSSProperties = (() => {
    const belowRobot = pos.y < window.innerHeight / 2;
    const top = belowRobot
      ? Math.max(EDGE, Math.min(pos.y + ROBOT_SIZE + 12, window.innerHeight - PANEL_HEIGHT - EDGE))
      : Math.max(EDGE, pos.y - PANEL_HEIGHT - 12);
    const left = Math.max(
      EDGE,
      Math.min(pos.x + ROBOT_SIZE / 2 - PANEL_WIDTH / 2, window.innerWidth - PANEL_WIDTH - EDGE),
    );
    return { top, left, width: PANEL_WIDTH, height: `min(${PANEL_HEIGHT}px, calc(100vh - 16px))` };
  })();

  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    dragRef.current = {
      pointerId: e.pointerId,
      startX: e.clientX,
      startY: e.clientY,
      origX: pos.x,
      origY: pos.y,
      moved: false,
    };
    e.currentTarget.setPointerCapture(e.pointerId);
  };

  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const d = dragRef.current;
    if (!d || e.pointerId !== d.pointerId) return;
    const dx = e.clientX - d.startX;
    const dy = e.clientY - d.startY;
    if (!d.moved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return;
    d.moved = true;
    setPos({
      x: Math.max(EDGE, Math.min(d.origX + dx, window.innerWidth - ROBOT_SIZE - EDGE)),
      y: Math.max(EDGE, Math.min(d.origY + dy, window.innerHeight - ROBOT_SIZE - EDGE)),
    });
  };

  const onPointerUp = () => {
    const d = dragRef.current;
    dragRef.current = null;
    // 位移小于阈值视为点击：开/关面板
    if (d && !d.moved) setOpen((v) => !v);
  };

  const ask = async () => {
    const question = input.trim();
    if (!question || loading) return;
    setInput('');
    setMessages((prev) => [...prev, { role: 'user', content: question }]);
    setLoading(true);
    try {
      const answer = await api.post<never, QaAnswer>('/v1/qa/ask', { question });
      setMessages((prev) => [...prev, { role: 'assistant', content: answer.answer, answer }]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        { role: 'assistant', content: `回答失败：${getErrorMessage(err)}`, error: true },
      ]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      {/* 悬浮机器人（拖动 + 点击开关面板） */}
      <div
        className="qa-robot"
        style={{ left: pos.x, top: pos.y, width: ROBOT_SIZE, height: ROBOT_SIZE }}
        role="button"
        aria-label="AI 智能助手（可拖动）"
        title="AI 智能助手（可拖动）"
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setOpen((v) => !v);
          }
        }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      >
        <span className="qa-robot-icon">🤖</span>
        <span className="qa-robot-badge">AI</span>
      </div>

      {/* 问答面板 */}
      {open && (
        <div className="qa-panel" style={panelStyle} role="dialog" aria-label="AI 智能问答">
          <div className="qa-panel-header">
            <span className="qa-panel-title">AI 智能助手</span>
            <span className="qa-panel-sub">储能资讯平台</span>
            <Button
              type="text"
              size="small"
              className="qa-panel-close"
              icon={<CloseOutlined />}
              onClick={() => setOpen(false)}
              aria-label="关闭问答面板"
            />
          </div>

          <div className="qa-messages" ref={listRef}>
            {messages.length === 0 && (
              <div className="qa-empty">
                <div className="qa-empty-icon">💬</div>
                <div>向 AI 助手提问，例如：</div>
                <ul>
                  <li>最近储能行业有什么政策动态？</li>
                  <li>怎么创建关键词订阅？</li>
                </ul>
              </div>
            )}
            {messages.map((msg, i) =>
              msg.role === 'user' ? (
                <div key={i} className="qa-msg qa-msg-user">
                  {msg.content}
                </div>
              ) : (
                <div key={i} className="qa-msg qa-msg-assistant">
                  <div className={`qa-msg-text${msg.error ? ' qa-msg-error' : ''}`}>
                    {msg.content}
                  </div>
                  {msg.answer && msg.answer.mode === 'NEWS' && msg.answer.references.length > 0 && (
                    <div className="qa-refs">
                      <div className="qa-refs-title">参考来源</div>
                      {msg.answer.references.map((ref, idx) => (
                        <Link
                          key={ref.articleId}
                          className="qa-ref-item"
                          to={`/center/${ref.articleId}`}
                          title={ref.title}
                        >
                          <span className="qa-ref-no">[{idx + 1}]</span>
                          <span className="qa-ref-main">
                            <span className="qa-ref-title">{ref.title}</span>
                            <span className="qa-ref-meta">
                              {ref.sourceName}
                              {ref.publishDate ? ` · ${ref.publishDate}` : ''}
                            </span>
                          </span>
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              ),
            )}
            {loading && <div className="qa-loading">思考中…</div>}
          </div>

          <div className="qa-input-row">
            <Input
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onPressEnter={ask}
              maxLength={500}
              placeholder="输入问题，回车发送"
              disabled={loading}
              aria-label="问题输入框"
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              onClick={ask}
              loading={loading}
              aria-label="发送问题"
            />
          </div>
        </div>
      )}
    </>
  );
}
