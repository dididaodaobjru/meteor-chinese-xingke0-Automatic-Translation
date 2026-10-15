package com.nippaku_zanmu.trans_addon.mixin;

import com.nippaku_zanmu.trans_addon.util.TransUtil;
import meteordevelopment.meteorclient.gui.GuiTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Meteor 的界面文字（Select / Copy / Search / Bind / Back ...）都是硬编码英文，
 * 但所有控件都必须经过 GuiTheme 这几个工厂方法创建，所以在这里统一改写入参即可。
 * <p>
 * 只处理有方法体的重载：{@code horizontalSeparator(String)} 与 {@code tooltip(String)}
 * 在 GuiTheme 里是抽象方法，注入不进去，这两个由子类自己去调用其它重载。
 * <p>
 * 查不到译文时 {@link TransUtil#translateGui} 会原样返回，且中文、玩家名、纯数字一律跳过。
 * 所有注入都用 require = 0：这门功能纯属锦上添花，将来 Meteor 改了方法签名时
 * 应当退化成"界面文字不翻译"，而不是让游戏崩在启动阶段。
 */
@Mixin(value = GuiTheme.class, remap = false)
public abstract class GuiThemeMixin {

    @ModifyVariable(method = "label(Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/WLabel;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$label(String text) {
        return TransUtil.translateGui(text);
    }

    @ModifyVariable(method = "label(Ljava/lang/String;Z)Lmeteordevelopment/meteorclient/gui/widgets/WLabel;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$labelTitle(String text) {
        return TransUtil.translateGui(text);
    }

    @ModifyVariable(method = "label(Ljava/lang/String;D)Lmeteordevelopment/meteorclient/gui/widgets/WLabel;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$labelWidth(String text) {
        return TransUtil.translateGui(text);
    }

    @ModifyVariable(method = "button(Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/pressable/WButton;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$button(String text) {
        return TransUtil.translateGui(text);
    }

    @ModifyVariable(method = "window(Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/containers/WWindow;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$window(String title) {
        return TransUtil.translateGui(title);
    }

    @ModifyVariable(method = "section(Ljava/lang/String;Z)Lmeteordevelopment/meteorclient/gui/widgets/containers/WSection;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$section(String title) {
        return TransUtil.translateGui(title);
    }

    @ModifyVariable(method = "textBox(Ljava/lang/String;Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/input/WTextBox;",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0)
    private String meteor$textBoxPlaceholder(String placeholder) {
        return TransUtil.translateGui(placeholder);
    }

    @ModifyVariable(method = "confirmedButton(Ljava/lang/String;Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/pressable/WConfirmedButton;",
        at = @At("HEAD"), argsOnly = true, index = 1, require = 0)
    private String meteor$confirmedButtonText(String text) {
        return TransUtil.translateGui(text);
    }

    @ModifyVariable(method = "confirmedButton(Ljava/lang/String;Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/pressable/WConfirmedButton;",
        at = @At("HEAD"), argsOnly = true, index = 2, require = 0)
    private String meteor$confirmedButtonConfirmText(String confirmText) {
        return TransUtil.translateGui(confirmText);
    }
}
