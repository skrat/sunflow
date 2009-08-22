package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.core.Texture;
import org.sunflow.core.TextureCache;
import org.sunflow.image.Color;

public class TexturedPhongShader extends PhongShader {

    private Texture tex;

    public TexturedPhongShader() {
        tex = null;
    }

    @Override
    public boolean update(ParameterList pl, SunflowAPI api) {
        String filename = pl.getString("texture", null);
        if (filename != null)
            tex = TextureCache.getTexture(api.resolveTextureFilename(filename), false);
        return tex != null && super.update(pl, api);
    }

    @Override
    public Color getDiffuse(ShadingState state) {
        float a = tex.getAlpha(state.getUV().x, state.getUV().y);
        if (a < 1f) {
          return Color.blend(
              super.getDiffuse(state),
              tex.getPixel(state.getUV().x, state.getUV().y), a );
        } else {
          return tex.getPixel(state.getUV().x, state.getUV().y);
        }
    }
}
