package com.nippaku_zanmu.trans_addon.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 翻译接口封装。共三个翻译源：
 * <ul>
 *     <li>{@link TranslateSource#Bing} —— 必应中国站网页接口，免费、无需 Key、自带语种识别，质量最好</li>
 *     <li>{@link TranslateSource#MyMemory} —— MyMemory 公共接口，免费、无需 Key，作为兜底</li>
 *     <li>{@link TranslateSource#OpenAI} —— 任意 OpenAI 兼容的 Chat Completions 服务，需要用户自填 Key</li>
 * </ul>
 */
public final class TranslateApi {
    public record Result(String text, String lang) {}

    private static final Pattern BING_HELPER =
        Pattern.compile("params_AbusePreventionHelper\\s*=\\s*\\[\\s*(\\d+)\\s*,\\s*\"([^\"]+)\"");
    private static final long BING_TOKEN_TTL = 8 * 60 * 1000L;

    private static String bingKey;
    private static String bingToken;
    private static long bingTokenTime;
    private static final String BING_IG = randomHex(32);

    private static final String OPENAI_SYSTEM_PROMPT =
        "你是 Minecraft 模组的界面本地化翻译。把用户给出的文本翻译成简体中文。"
            + "要求：只输出译文本身，不要解释、不要引号、不要标点补充；"
            + "若是模组名或设置项名这类短语，保持简短，不要扩写成句子；保留其中的占位符、数字与英文专有名词。";

    private TranslateApi() {}

    /**
     * @param sourceLang 源语言；传 null 表示自动识别。
     *                   界面文本（模块名/设置项）一律强制 {@code en}，否则必应会把
     *                   "Auto Totem" 这类短语误判成塞尔维亚语，翻出"汽车图腾"。
     */
    public static Result translate(String text, String targetLang, String sourceLang, TranslateSource source,
                                   String apiUrl, String apiKey, String model) throws Exception {
        return switch (source) {
            case Bing -> bing(text, targetLang, sourceLang);
            case MyMemory -> myMemory(text, targetLang, sourceLang);
            case OpenAI -> openAi(text, targetLang, apiUrl, apiKey, model);
        };
    }

    // ---------------------------------------------------------------- 必应

    private static Result bing(String text, String targetLang, String sourceLang) throws Exception {
        String to = bingLang(targetLang);
        String from = sourceLang == null || sourceLang.isBlank() ? "auto-detect" : sourceLang;

        String raw;
        try {
            raw = bingRequest(text, from, to);
        } catch (IllegalStateException e) {
            // token 过期或频率限制，强制刷新后重试一次
            refreshBingToken();
            raw = bingRequest(text, from, to);
        }

        JsonArray array = TransHttp.parse(raw).getAsJsonArray();
        JsonObject first = array.get(0).getAsJsonObject();

        String translated = first.getAsJsonArray("translations").get(0).getAsJsonObject().get("text").getAsString();
        String lang = first.has("detectedLanguage")
            ? first.getAsJsonObject("detectedLanguage").get("language").getAsString()
            : "";

        return new Result(translated, lang);
    }

    private static String bingRequest(String text, String from, String to) throws Exception {
        ensureBingToken();

        String body = "fromLang=" + from
            + "&text=" + TransHttp.enc(text)
            + "&to=" + to
            + "&token=" + bingToken
            + "&key=" + bingKey;

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://cn.bing.com/translator");
        headers.put("Origin", "https://cn.bing.com");

        return TransHttp.post(
            "https://cn.bing.com/ttranslatev3?isVertical=1&IG=" + BING_IG + "&IID=translator.5028",
            body, "application/x-www-form-urlencoded", headers);
    }

    private static void ensureBingToken() throws Exception {
        if (bingToken == null || System.currentTimeMillis() - bingTokenTime > BING_TOKEN_TTL) {
            refreshBingToken();
        }
    }

    private static void refreshBingToken() throws Exception {
        String html = TransHttp.get("https://cn.bing.com/translator", null);
        Matcher matcher = BING_HELPER.matcher(html);

        if (!matcher.find()) {
            throw new IllegalStateException("无法从必应页面获取翻译令牌");
        }

        bingKey = matcher.group(1);
        bingToken = matcher.group(2);
        bingTokenTime = System.currentTimeMillis();
    }

    private static String bingLang(String targetLang) {
        return isChineseTarget(targetLang) ? "zh-Hans" : "en";
    }

    // ------------------------------------------------------------- MyMemory

    private static Result myMemory(String text, String targetLang, String sourceLang) throws Exception {
        boolean toChinese = isChineseTarget(targetLang);
        String target = toChinese ? "zh-CN" : "en";

        // MyMemory 不支持自动识别，只能自己猜
        String source = sourceLang == null || sourceLang.isBlank()
            ? (isChinese(text) ? "zh-CN" : "en")
            : sourceLang;

        String url = "https://api.mymemory.translated.net/get?q=" + TransHttp.enc(text)
            + "&langpair=" + TransHttp.enc(source + "|" + target);

        JsonObject root = TransHttp.parse(TransHttp.get(url, null)).getAsJsonObject();
        JsonObject data = root.getAsJsonObject("responseData");

        if (data == null || !data.has("translatedText")) {
            throw new IllegalStateException("MyMemory 返回内容异常");
        }

        return new Result(data.get("translatedText").getAsString(), "");
    }

    // --------------------------------------------------------------- OpenAI

    private static Result openAi(String text, String targetLang, String apiUrl, String apiKey, String model) throws Exception {
        if (apiUrl == null || apiUrl.isBlank()) throw new IllegalStateException("未填写 API 地址");

        String url = apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions";

        JsonArray messages = new JsonArray();
        messages.add(message("system", OPENAI_SYSTEM_PROMPT));
        messages.add(message("user", text));

        JsonObject body = new JsonObject();
        body.addProperty("model", model == null || model.isBlank() ? "deepseek-chat" : model);
        body.addProperty("temperature", 0.2);
        body.addProperty("stream", false);
        body.add("messages", messages);

        Map<String, String> headers = new HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) headers.put("Authorization", "Bearer " + apiKey);

        String raw = TransHttp.post(url, body.toString(), "application/json", headers);
        JsonObject root = TransHttp.parse(raw).getAsJsonObject();

        if (!root.has("choices")) {
            throw new IllegalStateException("接口返回内容异常");
        }

        String content = root.getAsJsonArray("choices").get(0).getAsJsonObject()
            .getAsJsonObject("message").get("content").getAsString();

        return new Result(content.trim(), "");
    }

    private static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content);
        return object;
    }

    // ------------------------------------------------------------- 语言判断

    private static boolean isChineseTarget(String targetLang) {
        return targetLang == null || targetLang.isEmpty() || targetLang.toLowerCase().startsWith("zh");
    }

    /** 是否含有中文（含中文即认为无需翻译成中文）。 */
    public static boolean isChinese(String s) {
        if (s == null) return false;

        int cjk = 0;
        int letters = 0;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isCjk(c)) cjk++;
            if (Character.isLetter(c)) letters++;
        }

        return cjk > 0 && letters > 0 && cjk * 2 >= letters;
    }

    /** 是否值得翻译：至少要有两个字母，过滤掉纯数字、纯符号和单字符噪音。 */
    public static boolean looksTranslatable(String s) {
        if (s == null) return false;

        int letters = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) letters++;
        }

        return letters >= 2;
    }

    /** 把必应返回的语言代码归一成 zh / en / other。 */
    public static String normalizeLang(String lang) {
        if (lang == null || lang.isEmpty()) return "";
        String lower = lang.toLowerCase();
        if (lower.startsWith("zh")) return "zh";
        if (lower.startsWith("en")) return "en";
        return lower;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)     // 基本汉字
            || (c >= 0x3400 && c <= 0x4DBF)     // 扩展 A
            || (c >= 0xF900 && c <= 0xFAFF);    // 兼容汉字
    }

    private static String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        String hex = "0123456789ABCDEF";
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) sb.append(hex.charAt(random.nextInt(hex.length())));
        return sb.toString();
    }
}
