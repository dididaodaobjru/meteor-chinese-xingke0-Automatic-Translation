package com.nippaku_zanmu.trans_addon.util;

/**
 * 自动汉化使用的翻译源。
 * <p>
 * Bing 与 MyMemory 都是免费、无需 API Key 的公共接口；OpenAI 用于接入任意兼容
 * OpenAI Chat Completions 的服务（DeepSeek / 硅基流动 / Ollama / OpenAI 官方等）。
 */
public enum TranslateSource {
    Bing,
    MyMemory,
    OpenAI
}
