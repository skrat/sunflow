package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.AlphaShader;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.core.Texture;
import org.sunflow.core.TextureCache;
import org.sunflow.core.shader.TexturedPhongShader;
import org.sunflow.image.Color;

public class AlphaTexturedPhong extends TexturedPhongShader implements AlphaShader {

    private Texture alpha;

    public AlphaTexturedPhong() {
        alpha = null;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        String alphaFilename = pl.getString("alpha_texture", null);
        if (alphaFilename != null) {
            alpha = TextureCache.getTexture(api.resolveTextureFilename(alphaFilename), false);
        }
        return super.update(pl,api);
    }

    public Color getRadiance(ShadingState state) {
        Color result = super.getRadiance(state);
        if (alpha != null) {
            float a = getAlpha(state);
            if (a < 1.0f) {
                return Color.blend(state.traceTransparency(),result,a);
            } else {
                return result;
            }
        } else {
            return result;
        }
    }

    public Color getOpacity(ShadingState state) {
        return new Color( getAlpha(state) );
    }

    private float getAlpha(ShadingState state) {
        return alpha.getAlpha(state.getUV().x, state.getUV().y);
    }
}
