package com.nippaku_zanmu.trans_addon.mixin.HudRenderer;

import com.nippaku_zanmu.trans_addon.font_fix.FontFix;
import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;

public   class FontHolder {
        public final FontFix font;
        public boolean visited;

        private MeshBuilder mesh;

        public FontHolder(FontFix font) {
            this.font = font;
        }

        public MeshBuilder getMesh() {
            if (mesh == null) mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXT);
            if (!mesh.isBuilding()) mesh.begin();
            return mesh;
        }

        public void destroy() {
            font.texture.close();
        }
    }
