package com.nippaku_zanmu.trans_addon.modules;


import com.nippaku_zanmu.trans_addon.MeteorTranslation;
import com.nippaku_zanmu.trans_addon.mixin.CategoryAccessor;
import com.nippaku_zanmu.trans_addon.mixin.ModuleAccessor;
import com.nippaku_zanmu.trans_addon.mixin.SettingAccessor;
import com.nippaku_zanmu.trans_addon.mixin.SettingGroupAccessor;
import com.nippaku_zanmu.trans_addon.mixin.TabAccessor;
import com.nippaku_zanmu.trans_addon.settings.StringSelectSetting;
import com.nippaku_zanmu.trans_addon.util.AutoTranslate;
import com.nippaku_zanmu.trans_addon.util.ChatTranslator;
import com.nippaku_zanmu.trans_addon.util.TransUtil;
import com.nippaku_zanmu.trans_addon.util.TranslateService;
import com.nippaku_zanmu.trans_addon.util.TranslateSource;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class Translation extends Module {
    /** 供自动翻译回调重新应用界面文本使用。 */
    public static Translation INSTANCE;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAuto = this.settings.createGroup("自动汉化");
    private final SettingGroup sgChat = this.settings.createGroup("聊天翻译");

    // ------------------------------------------------------------ 基础设置

    public final Setting<Boolean> bSetAutoTranslation = sgGeneral.add(new BoolSetting.Builder()
        .name("自动翻译")
        .description("模块开启时自动应用一次翻译")
        .defaultValue(true)
        .build());

    public final Setting<String> sSetDumpPath = sgGeneral.add(new StringSetting.Builder()
        .name("DumpPath")
        .defaultValue("D:\\hack\\Misc\\meteor-translation-addon\\test\\en_us.json")
        .visible(() -> false)
        .build());

    public final Setting<Boolean> bSetDumpFile = sgGeneral.add(new BoolSetting.Builder()
        .name("dump en_usJson")
        .defaultValue(false)
        .visible(() -> false)
        .build());

    public final Setting<Set<String>> translationModules = sgGeneral.add(new StringSelectSetting.Builder().validValues(TransUtil.getAddonsName())
        .defaultValue(TransUtil.getAddonsName()).name("翻译的模块").build());

    // ------------------------------------------------------------ 自动汉化

    public final Setting<Boolean> autoTranslateEnabled = sgAuto.add(new BoolSetting.Builder()
        .name("启用自动汉化")
        .description("语言文件里没有的模块名/描述/设置项，联网自动翻译成中文并缓存到本地")
        .defaultValue(true)
        .build());

    public final Setting<TranslateSource> translateSource = sgAuto.add(new EnumSetting.Builder<TranslateSource>()
        .name("翻译源")
        .description("Bing 与 MyMemory 免费且无需 Key；OpenAI 可接 DeepSeek 等兼容接口")
        .defaultValue(TranslateSource.Bing)
        .build());

    public final Setting<String> autoApiUrl = sgAuto.add(new StringSetting.Builder()
        .name("API地址")
        .description("OpenAI 兼容接口的基址，例如 https://api.deepseek.com/v1")
        .defaultValue("https://api.deepseek.com/v1")
        .visible(() -> translateSource.get() == TranslateSource.OpenAI)
        .build());

    public final Setting<String> autoApiKey = sgAuto.add(new StringSetting.Builder()
        .name("API Key")
        .description("会明文保存在 Meteor 配置里，注意不要泄露")
        .defaultValue("")
        .visible(() -> translateSource.get() == TranslateSource.OpenAI)
        .build());

    public final Setting<String> autoModel = sgAuto.add(new StringSetting.Builder()
        .name("模型")
        .description("例如 deepseek-chat、qwen2.5、gpt-4o-mini")
        .defaultValue("deepseek-chat")
        .visible(() -> translateSource.get() == TranslateSource.OpenAI)
        .build());

    public final Setting<Integer> autoMaxPerRun = sgAuto.add(new IntSetting.Builder()
        .name("单次上限")
        .description("一次游戏最多自动翻译多少条，防止免费接口被刷爆")
        .defaultValue(400)
        .min(10)
        .sliderRange(10, 3000)
        .build());

    // ------------------------------------------------------------ 聊天翻译

    public final Setting<ChatTranslator.ReceiveMode> chatReceiveMode = sgChat.add(new EnumSetting.Builder<ChatTranslator.ReceiveMode>()
        .name("收到的消息")
        .description("Merge=原文与译文合并成一行；Append=原文照常显示、译文另起一行")
        .defaultValue(ChatTranslator.ReceiveMode.Merge)
        .build());

    public final Setting<ChatTranslator.SendMode> chatSendMode = sgChat.add(new EnumSetting.Builder<ChatTranslator.SendMode>()
        .name("发送的消息")
        .description("ZhToEn=把中文翻成英文再发出；EnToZh=把英文翻成中文再发出")
        .defaultValue(ChatTranslator.SendMode.Off)
        .build());

    public final Setting<Integer> chatMinLength = sgChat.add(new IntSetting.Builder()
        .name("最短长度")
        .description("短于该长度的消息不翻译，避免 gg、+1 这类噪音")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 30)
        .build());

    public final Setting<Boolean> chatShowSendResult = sgChat.add(new BoolSetting.Builder()
        .name("发送后提示译文")
        .description("翻译后在自己聊天栏里显示实际发出的内容")
        .defaultValue(true)
        .visible(() -> chatSendMode.get() != ChatTranslator.SendMode.Off)
        .build());

    public static BufferedWriter dumpBW;

    public Translation() {
        super(MeteorTranslation.CATEGORY, "中文翻译", "为 meteor客户端 提供中文支持");
        INSTANCE = this;
    }

    private boolean isTranslation = false;

    @Override
    public void onActivate() {
        if (bSetAutoTranslation.get() && !isTranslation) {
            isTranslation = true;
            tran();
        }

        ChatUtils.warning("已开启中文翻译-此插件完全开源免费");
    }

    @Override
    public void onDeactivate() {
        AutoTranslate.save();
    }

    // ------------------------------------------------------------ 事件入口

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        ChatTranslator.onReceive(event);
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        ChatTranslator.onSend(event);
    }

    /** 自动翻译完成一批后重新应用界面文本。 */
    public static void requestReapply() {
        Translation module = INSTANCE;
        if (module == null || !module.isActive()) return;

        module.tran();
    }

    // ------------------------------------------------------------ 界面

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WHorizontalList b = list.add(theme.horizontalList()).expandX().widget();

        WButton start = b.add(theme.button("开始翻译")).expandX().widget();
        start.action = () -> {
            if (this.isActive()) {
                if (bSetDumpFile.get()) {
                    try {
                        File path = new File(sSetDumpPath.get());
                        if (!path.exists() & !(path.createNewFile())) {
                            ChatUtils.sendMsg(Text.of("DumpError Can't Create Dump File"));
                            return;
                        }
                        dumpBW = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(path, false), StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        ChatUtils.error(e.getMessage());
                        return;
                    }
                }
                isTranslation = true;
                tran();

                try {
                    if (dumpBW != null)
                        dumpBW.close();
                } catch (IOException e) {

                }

            } else {
                ChatUtils.warning("你首先要开启此模块");
            }
        };

        WHorizontalList autoRow = list.add(theme.horizontalList()).expandX().widget();

        WButton auto = autoRow.add(theme.button("自动汉化")).expandX().widget();
        auto.action = () -> {
            if (!this.isActive()) {
                ChatUtils.warning("你首先要开启此模块");
                return;
            }
            if (!autoTranslateEnabled.get()) {
                ChatUtils.warning("请先在「自动汉化」分类里打开「启用自动汉化」");
                return;
            }

            tran();
            ChatUtils.sendMsg(Text.of("已扫描缺失词条，正在后台翻译，完成后会自动刷新界面"));
        };

        WButton status = autoRow.add(theme.button("状态")).widget();
        status.action = () -> {
            ChatUtils.sendMsg(Text.of("自动汉化缓存: " + AutoTranslate.size() + " 条"));
            ChatUtils.sendMsg(Text.of("待翻译队列: " + TranslateService.pendingCount() + " 条"));
            if (TranslateService.lastError() != null) {
                ChatUtils.sendMsg(Text.of("最近一次错误: " + TranslateService.lastError()));
            }
            if (AutoTranslate.cacheFile() != null) {
                ChatUtils.sendMsg(Text.of("缓存文件: " + AutoTranslate.cacheFile().getAbsolutePath()));
            }
        };

        WHorizontalList clearRow = list.add(theme.horizontalList()).expandX().widget();

        WButton clear = clearRow.add(theme.button("清空自动翻译缓存")).expandX().widget();
        clear.action = () -> {
            AutoTranslate.clear();
            ChatUtils.sendMsg(Text.of("已清空自动汉化缓存"));
        };

        WButton reload = clearRow.add(theme.button("重载缓存")).widget();
        reload.action = () -> {
            AutoTranslate.load();
            ChatUtils.sendMsg(Text.of("已重载自动汉化缓存: " + AutoTranslate.size() + " 条"));
        };

        return list;
    }


    public void tran() {
        for (Module module : Modules.get().getAll()) {
            String addonName = TransUtil.getAddonName(module);
            if (!translationModules.get().contains(addonName)) continue;
            //插件过滤

            String tranName = TransUtil.transModuleName(module);
            // 经过翻译的名称
            ((ModuleAccessor) module).setTitle(Utils.nameToTitle(tranName));
            //把标题设为翻译之后的名称

            String tranDescry = TransUtil.transModuleDescription(module);
            ((ModuleAccessor) module).setDescription(Utils.nameToTitle(tranDescry));
            //翻译简介

            for (SettingGroup group : module.settings.groups) {
                for (Setting<?> setting : ((SettingGroupAccessor) group).getSettings()) {
                    String tranSettName = TransUtil.transSettingName(module, group, setting);
                    ((SettingAccessor) setting).setTitle(Utils.nameToTitle(tranSettName));

                    String tranSettDesc = TransUtil.transSettingDes(module, group, setting);
                    ((SettingAccessor) setting).setDescription(Utils.nameToTitle(tranSettDesc));
                }
            }

        }

        translateCategories();
        translateTabs();
    }

    /** 模块分类名（Combat / Player / Movement ...）。 */
    private void translateCategories() {
        for (Category category : Modules.loopCategories()) {
            String originalName = TransUtil.original(category, category.name);
            String translated = TransUtil.transCategory(originalName);

            if (!translated.equals(category.name)) {
                ((CategoryAccessor) category).setCategoryName(translated);
            }
        }
    }

    /** 顶部标签页名（Modules / Config / GUI / HUD ...）。 */
    private void translateTabs() {
        for (Tab tab : Tabs.get()) {
            String originalName = TransUtil.original(tab, tab.name);
            String translated = TransUtil.transTab(originalName);

            if (!translated.equals(tab.name)) {
                ((TabAccessor) tab).setTabName(translated);
            }
        }
    }
}
