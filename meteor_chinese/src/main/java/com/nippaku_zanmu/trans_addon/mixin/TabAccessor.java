package com.nippaku_zanmu.trans_addon.mixin;

import meteordevelopment.meteorclient.gui.tabs.Tab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 顶部标签页名（Modules / Config / GUI / HUD ...）。WTopBar 与 TabScreen 都直接读
 * {@code tab.name}，所以改写这里就能同时改掉按钮文字和窗口标题。
 */
@Mixin(value = Tab.class, remap = false)
public interface TabAccessor {
    @Mutable
    @Accessor("name")
    void setTabName(String name);
}
