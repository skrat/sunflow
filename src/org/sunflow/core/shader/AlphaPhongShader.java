package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.AlphaShader;
import org.sunflow.core.ParameterList;
import org.sunflow.core.ShadingState;
import org.sunflow.image.Color;

public class AlphaPhongShader extends PhongShader implements AlphaShader {

    private float transparency;
    private Color transparencyColor;

    public AlphaPhongShader() {
        transparency = 0f;
        transparencyColor = new Color(transparency);
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        transparency = pl.getFloat("transparency", transparency);
        return super.update(pl,api);
    }

    public Color getRadiance(ShadingState state) {
        Color phong = super.getRadiance(state);
        return Color.blend(phong, state.traceTransparency(), transparency);
    }

    public Color getOpacity(ShadingState state) {
        return transparencyColor;
    }

}
