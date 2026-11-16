package com.structengine.platform.llm;

/**
 * AI 推理调用抽象，便于测试注入 mock。
 */
public interface LlmClient {

    /**
     * 将图片字节与文字指令交给多模态模型推理，返回原始文本。
     *
     * @param images      图片字节数组（每张一元素）
     * @param instruction 随图片发送的补充指令
     * @return AI 返回的原始文本
     */
    String generate(byte[][] images, String instruction);
}
