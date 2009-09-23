package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.core.TextureCache;
import org.sunflow.core.Texture;
import org.sunflow.core.shader.AlphaShinyPhong;
import org.sunflow.image.Color;

public class AlphaTexturedShinyPhong extends AlphaShinyPhong {

    private Texture tex;

    public AlphaTexturedShinyPhong() {
        tex = null;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        String textureFilename = pl.getString("texture", null);
        if (textureFilename != null) {
            tex = TextureCache.getTexture(api.resolveTextureFilename(textureFilename), false);
        }
        return true && super.update(pl, api);
    }

    public Color getDiffuse(ShadingState state) {
        return tex.getPixel(state.getUV().x, state.getUV().y);
    }
}
