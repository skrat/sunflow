package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.AlphaShader;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.core.TextureCache;
import org.sunflow.core.shader.ShinyPhongShader;
import org.sunflow.image.Bitmap;
import org.sunflow.image.Color;
import org.sunflow.math.MathUtils;

public class AlphaShinyPhong extends ShinyPhongShader implements AlphaShader {

    private Bitmap alpha;

    public AlphaShinyPhong() {
        alpha = null;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        String alphaFilename = pl.getString("alpha_texture", null);
        if (alphaFilename != null) {
            alpha = TextureCache.getTexture(api.resolveTextureFilename(alphaFilename), false).getBitmap();
        }
        return true && super.update(pl, api);
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
        float a = getAlpha(state);
        return new Color(a);
    }

    private float getAlpha(ShadingState state) {
        float x = MathUtils.frac(state.getUV().x);
        float y = MathUtils.frac(state.getUV().y);
        float dx = x * (alpha.getWidth() - 1);
        float dy = y * (alpha.getHeight() - 1);
        int ix = (int) dx;
        int iy = (int) dy;

        return alpha.readAlpha(ix, iy);
    }
}
