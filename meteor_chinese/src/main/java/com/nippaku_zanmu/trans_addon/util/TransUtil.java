package com.nippaku_zanmu.trans_addon.util;

import com.nippaku_zanmu.trans_addon.MeteorTranslation;
import com.nippaku_zanmu.trans_addon.modules.Translation;
import meteordevelopment.meteorclient.addons.AddonManager;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TransUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /**
     * 取翻译文本。优先级：静态语言文件（zh_cn.json） → 自动汉化缓存 → 原文。
     * 都没命中时把词条排进自动翻译队列，等译好后再由 {@link Translation} 重新应用。
     */
    private static String trans(String key, String english) {
        String translated = Text.translatable(key).getString();

        // 语言文件里有就直接用
        if (!translated.equals(key)) return translated;

        // 运行时自动汉化缓存
        String auto = AutoTranslate.get(key);
        if (auto != null) return auto;

        AutoTranslate.request(key, english);
        return english;
    }

    /** 对外暴露一份，供翻译分类名/标签页名使用。 */
    public static String translate(String key, String english) {
        return trans(key, english);
    }

    // ------------------------------------------------------- 原始名字记录
    // 分类名和标签页名会被本插件就地改写。改写之后 category.name 已经是中文，
    // 再拿它去拼语言文件的 key 就永远查不到了，所以这里记住它们最初的名字。

    private static final Map<Object, String> ORIGINALS =
        Collections.synchronizedMap(new IdentityHashMap<>());

    /** 取对象最初的名字；第一次调用时记下来。 */
    public static String original(Object owner, String current) {
        String known = ORIGINALS.get(owner);
        if (known != null) return known;

        ORIGINALS.put(owner, current);
        return current;
    }

    public static String categoryName(Category category) {
        return original(category, category.name);
    }

    /** 分类名，key 约定为 meteor.category.&lt;分类&gt;。 */
    public static String transCategory(String name) {
        return trans("meteor.category." + baseFormat(name), name);
    }

    /** 顶部标签页名，key 约定为 meteor.tab.&lt;标签&gt;。 */
    public static String transTab(String name) {
        return trans("meteor.tab." + baseFormat(name), name);
    }

    // ------------------------------------------------------- Meteor 界面文字
    // Meteor 的 GUI 文字（Select / Copy / Search / Bind ...）是硬编码英文，
    // 这里把经过 GuiTheme 创建的控件文本也接进同一套翻译管线。

    /**
     * 翻译界面控件文本。与其它词条不同，这里查不到时必须原样返回（连首尾空格都不能动），
     * 否则界面布局会变。
     */
    public static String translateGui(String text) {
        if (text == null) return null;

        String trimmed = text.trim();
        if (!isAsciiWords(trimmed)) return text;

        String keyPart = guiFormat(trimmed);
        if (keyPart.length() < 3) return text;

        String key = "meteor.gui." + keyPart;

        String translated = Text.translatable(key).getString();
        if (!translated.equals(key)) return translated;

        String auto = AutoTranslate.get(key);
        if (auto != null) return auto;

        AutoTranslate.request(key, trimmed);
        return text;
    }

    /** 只处理纯 ASCII 且含字母的文本，避免把中文、玩家名、数字送去翻译。 */
    private static boolean isAsciiWords(String s) {
        if (s.isEmpty()) return false;

        boolean hasLetter = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c > 127) return false;
            if (Character.isLetter(c)) hasLetter = true;
        }

        return hasLetter;
    }

    /** "Access Token: " → "access_token"，"Left click - Toggle module" → "left_click_toggle_module"。 */
    private static String guiFormat(String s) {
        String formatted = s.toLowerCase(Locale.ROOT)
            .replace(" ", "_")
            .replace("-", "_")
            .replace(".", "_")
            .replace("/", "_")
            .replace(":", "")
            .replace("!", "")
            .replace(",", "")
            .replace("?", "");

        formatted = formatted.replaceAll("_+", "_");
        formatted = formatted.replaceAll("^_|_$", "");

        return formatted;
    }

    public static Set<String> getAddonsName() {
        return AddonManager.ADDONS.stream().map(addon -> addon.name).map(TransUtil::baseFormat).collect(Collectors.toSet());
    }

    public static String getAddonName(Module m) {
        return TransUtil.baseFormat(m.addon == null ? "unknow_addon" : m.addon.name);
    }

    public static String transModuleName(Module m) {
        String moduleName = m.name;
        String key = "meteor." + getAddonName(m) + "." + baseFormat(categoryName(m.category)) + "." + baseFormat(moduleName);
        //转换成规范的key
        //meteor.模块类型.模块名

        dumpEn_USJson(key, moduleName);
        //开发者使用 导出英文语言文件

        //调用翻译：语言文件 → 自动汉化缓存 → 原文
        return trans(key, moduleName);
    }

    public static String transModuleDescription(Module m) {
        String moduleName = m.name;
        String moduleDes = m.description;
        String key = "meteor." + getAddonName(m) + "." + baseFormat(categoryName(m.category)) + "." + baseFormat(moduleName) + ".description";
        //转换成规范的key
        dumpEn_USJson(key, moduleDes);

        //调用翻译：语言文件 → 自动汉化缓存 → 原文
        return trans(key, moduleDes);
    }

    public static String transSettingName(Module module, SettingGroup group, Setting s) {
        String key = "meteor." +
            getAddonName(module) + "." +
            baseFormat(categoryName(module.category)) + "." +
            baseFormat(module.name) + "." +
            "setting." +
            group.name + "." +
            s.name;

        dumpEn_USJson(key, s.name);
        //dump

        //调用翻译：语言文件 → 自动汉化缓存 → 原文
        return trans(key, s.name);
    }

    public static String transSettingDes(Module module, SettingGroup group, Setting s) {
        String key = "meteor." +
            getAddonName(module) + "." +
            baseFormat(categoryName(module.category)) + "." +
            baseFormat(module.name) + "." +
            "setting." +
            group.name + "." +
            s.name + "." +
            "description";

        dumpEn_USJson(key, s.description);
        //dump

        //调用翻译：语言文件 → 自动汉化缓存 → 原文
        return trans(key, s.description == null ? s.name : s.description);
    }


    public static String baseFormat(String s) {
        //把某些Addon作者的不规范模块命名还原
        s = s.toLowerCase();
        s = s.replace(" ", "_");
        s = s.replace("-", "_");
        s = s.replace(".", "_");
        return s;
    }

    public static void dumpEn_USJson(String key, String en_usName) {
        Translation translation = Modules.get().get(Translation.class);

        if ((!translation.bSetDumpFile.get()) || translation.bSetAutoTranslation.get() || mc.world == null || mc.player == null)
            return;
        try {

            en_usName = en_usName.replace("\"", "\\\"");
            Translation.dumpBW.write("\"" + key + "\"" + ":" + "\"" + en_usName + "\"" + ",");
            Translation.dumpBW.newLine();
            Translation.dumpBW.flush();
        } catch (IOException e) {
            ChatUtils.error(e.getMessage());
        }
    }

}
