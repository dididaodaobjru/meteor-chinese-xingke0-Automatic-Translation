package com.nippaku_zanmu.trans_addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Category;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 分类名（Combat / Player / Movement ...）是 {@code public final String}，
 * 用 @Mutable 访问器去掉 final 才能改写。
 * <p>
 * 安全性：Category 的 equals/hashCode 用的是构造时算好的 nameHash，改写 name 不影响
 * 分类匹配；Modules 保存/读取模块时按模块名匹配，也不写分类名。
 */
@Mixin(value = Category.class, remap = false)
public interface CategoryAccessor {
    @Mutable
    @Accessor("name")
    void setCategoryName(String name);
}
