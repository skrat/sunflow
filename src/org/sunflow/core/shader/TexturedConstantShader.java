package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.core.Texture;
import org.sunflow.core.TextureCache;
import org.sunflow.core.shader.ConstantShader;
import org.sunflow.image.Color;

public class TexturedConstantShader extends ConstantShader {

    private Texture tex;

    public TexturedConstantShader() {
        tex = null;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        String filename = pl.getString("texture", null);
        if (filename != null) {
            tex = TextureCache.getTexture(api.resolveTextureFilename(filename), false);
        }
        return tex != null;
    }

    public Color getRadiance(ShadingState state) {
        return getDiffuse(state);
    }

    public Color getDiffuse(ShadingState state) {
        return tex.getPixel(state.getUV().x, state.getUV().y);
    }
}
