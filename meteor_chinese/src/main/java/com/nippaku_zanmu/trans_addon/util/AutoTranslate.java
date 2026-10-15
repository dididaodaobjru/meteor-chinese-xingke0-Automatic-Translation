package com.nippaku_zanmu.trans_addon.util;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nippaku_zanmu.trans_addon.MeteorTranslation;
import com.nippaku_zanmu.trans_addon.modules.Translation;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 界面文本的自动汉化。
 * <p>
 * 静态语言文件（zh_cn.json）里没有的 key 会走这里：先查内存/磁盘缓存，缓存未命中就排进
 * 翻译队列，翻译完成后写入缓存并落盘。落盘文件与 zh_cn.json 结构完全一致，
 * 可以直接合并回语言文件提 PR 给上游。
 */
public final class AutoTranslate {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> REQUESTED = ConcurrentHashMap.newKeySet();

    private static File cacheFile;
    private static volatile boolean dirty;
    private static volatile boolean needsReapply;

    private AutoTranslate() {}

    public static void init(File folder) {
        cacheFile = new File(folder, "meteor-chinese-auto.json");
        load();
    }

    public static File cacheFile() {
        return cacheFile;
    }

    public static synchronized void load() {
        CACHE.clear();

        if (cacheFile == null || !cacheFile.isFile()) return;

        try {
            String text = Files.readString(cacheFile.toPath(), StandardCharsets.UTF_8);
            JsonObject root = TransHttp.parse(text).getAsJsonObject();

            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    CACHE.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            MeteorTranslation.LOG.warn("读取自动汉化缓存失败: {}", e.getMessage());
        }
    }

    public static synchronized void save() {
        if (cacheFile == null || !dirty) return;

        try {
            File parent = cacheFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();

            JsonObject root = new JsonObject();
            CACHE.forEach(root::addProperty);

            Files.writeString(cacheFile.toPath(),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root),
                StandardCharsets.UTF_8);

            dirty = false;
        } catch (Exception e) {
            MeteorTranslation.LOG.warn("保存自动汉化缓存失败: {}", e.getMessage());
        }
    }

    public static synchronized void clear() {
        CACHE.clear();
        REQUESTED.clear();
        dirty = true;
        save();
    }

    public static int size() {
        return CACHE.size();
    }

    /** 查缓存，未命中返回 null。 */
    public static String get(String key) {
        return CACHE.get(key);
    }

    /** 排队翻译一条界面文本。同一个 key 只会请求一次。 */
    public static void request(String key, String english) {
        if (key == null || english == null || english.isBlank()) return;
        if (CACHE.containsKey(key)) return;
        if (!REQUESTED.add(key)) return;

        Translation module = Translation.INSTANCE;
        if (module == null || !module.autoTranslateEnabled.get()) return;
        if (REQUESTED.size() > module.autoMaxPerRun.get()) return;
        if (!TranslateApi.looksTranslatable(english)) return;

        TranslateService.submit(new TranslateService.Task(
            english,
            "zh-CN",
            "en",   // 界面文本一律按英文处理，避免被误判成其它语种
            module.translateSource.get(),
            module.autoApiUrl.get(),
            module.autoApiKey.get(),
            module.autoModel.get(),
            (translated, lang, ok) -> {
                if (ok) {
                    CACHE.put(key, translated);
                    dirty = true;
                    needsReapply = true;
                    save();
                }
            }
        ));
    }

    /** 取走"需要重新应用界面文本"的标记；聊天翻译不会触发它。 */
    public static boolean consumeReapply() {
        boolean value = needsReapply;
        needsReapply = false;
        return value;
    }
}
