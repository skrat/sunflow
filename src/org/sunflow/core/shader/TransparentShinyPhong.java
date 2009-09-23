package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.ParameterList;
import org.sunflow.core.Ray;
import org.sunflow.core.ShadingState;
import org.sunflow.image.Color;
import org.sunflow.math.Vector3;

public class TransparentShinyPhong extends AlphaPhongShader {

    private float refl;

    public TransparentShinyPhong() {
        refl = 0.5f;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        refl = pl.getFloat("shiny", refl);
        return super.update(pl, api);
    }

    public Color getRadiance(ShadingState state) {
        Color result = super.getRadiance(state);

        Color d = getDiffuse(state);
        float cos = state.getCosND();
        float dn = 2 * cos;
        Vector3 refDir = new Vector3();
        refDir.x = (dn * state.getNormal().x) + state.getRay().getDirection().x;
        refDir.y = (dn * state.getNormal().y) + state.getRay().getDirection().y;
        refDir.z = (dn * state.getNormal().z) + state.getRay().getDirection().z;
        Ray refRay = new Ray(state.getPoint(), refDir);

        // compute Fresnel term
        cos = 1 - cos;
        float cos2 = cos * cos;
        float cos5 = cos2 * cos2 * cos;

        Color ret = Color.white();
        Color r = d.copy().mul(refl);
        ret.sub(r);
        ret.mul(cos5);
        ret.add(r);

        return result.add(ret.mul(state.traceReflection(refRay, 0)));
    }

}
