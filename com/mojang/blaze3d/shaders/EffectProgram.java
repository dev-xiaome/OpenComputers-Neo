package com.mojang.blaze3d.shaders;

import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class EffectProgram extends Program {
    private static final GlslPreprocessor PREPROCESSOR = new GlslPreprocessor() {
        @Override
        public String applyImport(boolean p_166595_, String p_166596_) {
            return "#error Import statement not supported";
        }
    };
    private int references;

    private EffectProgram(Program.Type type, int id, String name) {
        super(type, id, name);
    }

    public void attachToEffect(Effect effect) {
        RenderSystem.assertOnRenderThread();
        this.references++;
        this.attachToShader(effect);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        this.references--;
        if (this.references <= 0) {
            super.close();
        }
    }

    public static EffectProgram compileShader(Program.Type type, String name, InputStream shaderData, String sourceName) throws IOException {
        RenderSystem.assertOnRenderThread();
        int i = compileShaderInternal(type, name, shaderData, sourceName, PREPROCESSOR);
        EffectProgram effectprogram = new EffectProgram(type, i, name);
        type.getPrograms().put(name, effectprogram);
        return effectprogram;
    }
}
