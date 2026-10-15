package com.nippaku_zanmu.trans_addon.mixin;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = Module.class,remap = false)
public interface ModuleAccessor {


    @Mutable
    @Accessor("title")
    public void setTitle(String title);
    @Mutable
    @Accessor("description")
    public void setDescription(String description);
}
