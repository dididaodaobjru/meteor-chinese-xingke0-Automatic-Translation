package com.nippaku_zanmu.trans_addon.util;

import com.nippaku_zanmu.trans_addon.modules.Translation;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天翻译。
 * <p>
 * 收到外语消息时把译文附在原文后面（原文始终保留可见）；自己发的消息也可以先翻译再发出。
 * 译文由 {@link TranslateService} 在后台线程获取，回到主线程后再写进聊天栏。
 */
public final class ChatTranslator {
    /** 收到消息的处理方式。 */
    public enum ReceiveMode {
        Off,
        /** 拦住原文，等译文返回后合并成一行显示（原文 + [译文]）。 */
        Merge,
        /** 原文照常显示，译文另起一行。 */
        Append
    }

    /** 自己发送消息时的翻译方向。 */
    public enum SendMode {
        Off,
        ZhToEn,
        EnToZh
    }

    /** 最近自己发出的内容，避免服务端回显时又被翻译一遍。 */
    private static final Set<String> SELF_SENT = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** 重入保护：我们自己往聊天栏塞消息时不要再被当成新消息处理。 */
    private static boolean internal;

    private ChatTranslator() {}

    // ------------------------------------------------------------ 收到消息

    public static void onReceive(ReceiveMessageEvent event) {
        if (internal) return;

        Translation module = Translation.INSTANCE;
        if (module == null || !module.isActive()) return;

        ReceiveMode mode = module.chatReceiveMode.get();
        if (mode == ReceiveMode.Off) return;

        Text message = event.getMessage();
        if (message == null) return;

        String raw = message.getString();
        if (raw.isBlank()) return;
        if (raw.length() < module.chatMinLength.get()) return;
        if (!TranslateApi.looksTranslatable(raw)) return;
        if (TranslateApi.isChinese(raw)) return;
        if (isSelfEcho(raw)) return;

        if (mode == ReceiveMode.Merge) event.setCancelled(true);

        TranslateService.submit(new TranslateService.Task(
            raw,
            "zh-CN",
            null,   // 聊天内容语种未知，交给接口自动识别
            module.translateSource.get(),
            module.autoApiUrl.get(),
            module.autoApiKey.get(),
            module.autoModel.get(),
            (translated, lang, ok) -> {
                if (ok && TranslateApi.isChinese(translated)) {
                    RECENT.put(raw, translated);
                    addInternal(mode == ReceiveMode.Merge ? merge(message, translated) : appended(translated));
                } else {
                    // 翻译失败或没翻出中文：保证原文不丢
                    if (mode == ReceiveMode.Merge) addInternal(message);
                }
            }
        ));
    }

    // ------------------------------------------------------------ 发送消息

    public static void onSend(SendMessageEvent event) {
        if (internal) return;

        String message = event.message;
        if (message == null || message.isBlank()) return;

        Translation module = Translation.INSTANCE;
        if (module == null || !module.isActive()) return;

        SendMode mode = module.chatSendMode.get();
        if (mode == SendMode.Off) return;

        // 命令、Meteor 前缀指令不翻译
        if (message.startsWith("/") || message.startsWith(".") || message.startsWith("!")) return;
        if (!TranslateApi.looksTranslatable(message)) return;

        boolean shouldTranslate = switch (mode) {
            case ZhToEn -> TranslateApi.isChinese(message);
            case EnToZh -> !TranslateApi.isChinese(message);
            case Off -> false;
        };

        if (!shouldTranslate) return;

        String target = mode == SendMode.ZhToEn ? "en" : "zh-CN";

        event.setCancelled(true);

        TranslateService.submit(new TranslateService.Task(
            message,
            target,
            mode == SendMode.ZhToEn ? "zh-CN" : "en",
            module.translateSource.get(),
            module.autoApiUrl.get(),
            module.autoApiKey.get(),
            module.autoModel.get(),
            (translated, lang, ok) -> {
                String outgoing = (ok && translated != null && !translated.isBlank()) ? translated : message;

                if (module.chatShowSendResult.get()) {
                    addInternal(Text.literal("[发送] " + outgoing).formatted(Formatting.DARK_GRAY));
                }

                sendChat(outgoing);
            }
        ));
    }

    // -------------------------------------------------------------- 工具

    private static MutableText merge(Text original, String translated) {
        MutableText out = Text.empty();
        out.append(original);
        out.append(Text.literal(" [").formatted(Formatting.DARK_GRAY));
        out.append(Text.literal(translated).formatted(Formatting.GRAY));
        out.append(Text.literal("]").formatted(Formatting.DARK_GRAY));
        return out;
    }

    private static MutableText appended(String translated) {
        return Text.literal("[译] ").formatted(Formatting.DARK_GRAY)
            .append(Text.literal(translated).formatted(Formatting.GRAY));
    }

    /** 直接往聊天栏写消息，不触发本类逻辑。 */
    private static void addInternal(Text text) {
        if (MeteorClient.mc == null || MeteorClient.mc.inGameHud == null) return;

        internal = true;
        try {
            MeteorClient.mc.inGameHud.getChatHud().addMessage(text);
        } finally {
            internal = false;
        }
    }

    private static void sendChat(String message) {
        if (MeteorClient.mc == null || MeteorClient.mc.player == null) return;

        rememberSelf(message);

        internal = true;
        try {
            MeteorClient.mc.player.networkHandler.sendChatMessage(message);
        } finally {
            internal = false;
        }
    }

    private static void rememberSelf(String message) {
        if (SELF_SENT.size() > 64) SELF_SENT.clear();
        SELF_SENT.add(message);
    }

    private static boolean isSelfEcho(String raw) {
        if (SELF_SENT.isEmpty()) return false;

        for (String sent : SELF_SENT) {
            if (!sent.isEmpty() && raw.contains(sent)) return true;
        }

        return false;
    }

    /** 最近翻译过的消息，供界面展示用。 */
    private static final Map<String, String> RECENT = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 20;
        }
    });

    public static Map<String, String> recent() {
        return RECENT;
    }
}