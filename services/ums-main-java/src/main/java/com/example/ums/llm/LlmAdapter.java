package com.example.ums.llm;

/**
 * LLM 统一接入层：业务/工程流水线中所有 AI 能力（摘要、生成、评审、归因）的统一入口。
 *
 * <p>当前实现为 {@link OpenAiCompatAdapter}（OpenAI 兼容协议），
 * GLM 公网 API、OpenAI、公司 AI 网关、内网 vLLM 等均可通过配置切换，
 * 无需改动调用方代码。配置见 application.yml 的 ums.llm 段：
 * <pre>
 *   base-url: ${UMS_LLM_BASE_URL}   # 如 https://open.bigmodel.cn/api/paas/v4
 *   api-key:  ${UMS_LLM_API_KEY}    # 仅环境变量，不进仓库
 *   model:    ${UMS_LLM_MODEL}      # 如 glm-4-flash
 * </pre>
 */
public interface LlmAdapter {

    /** 单轮对话（默认参数），返回模型回复文本。 */
    String chat(String systemPrompt, String userPrompt);

    /** 单轮对话，带温度/输出上限/模型覆盖等参数。 */
    String chat(String systemPrompt, String userPrompt, LlmOptions options);

    /**
     * 结构化输出：引导模型返回 JSON 并反序列化为指定类型。
     * 模型输出带 ```json 代码围栏时自动剥离；解析失败抛出异常并携带原始输出便于排查。
     */
    <T> T structured(String systemPrompt, String userPrompt, Class<T> type);
}
