package com.example.ums.llm;

import lombok.Getter;

/**
 * 单次 LLM 调用参数（均可选，缺省走 ums.llm 配置）。
 * 使用 builder：LlmOptions.builder().temperature(0.2).maxTokens(2048).build()
 */
@Getter
public class LlmOptions {

    /** 采样温度，0-2；抽取/分类类任务建议 0.1-0.3，生成类建议 0.7 */
    private final Double temperature;

    /** 输出 token 上限 */
    private final Integer maxTokens;

    /** 本次调用覆盖默认模型（如长文本用大模型、批量摘要用 flash 档） */
    private final String model;

    private LlmOptions(Double temperature, Integer maxTokens, String model) {
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.model = model;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 默认参数 */
    public static LlmOptions defaults() {
        return builder().build();
    }

    public static class Builder {
        private Double temperature;
        private Integer maxTokens;
        private String model;

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public LlmOptions build() {
            return new LlmOptions(temperature, maxTokens, model);
        }
    }
}
