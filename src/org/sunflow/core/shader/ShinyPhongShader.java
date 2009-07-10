package org.sunflow.core.shader;

import org.sunflow.SunflowAPI;
import org.sunflow.core.ParameterList;
import org.sunflow.core.Ray;
import org.sunflow.core.Shader;
import org.sunflow.core.ShadingState;
import org.sunflow.image.Color;
import org.sunflow.math.OrthoNormalBasis;
import org.sunflow.math.Vector3;

public class ShinyPhongShader implements Shader {
    private Color diff;
    private Color spec;
    private float power;
    private float refl;
    private int numRays;

    public ShinyPhongShader() {
        diff = Color.GRAY;
        spec = Color.GRAY;
        refl = 0.5f;
        power = 20;
        numRays = 4;
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        diff = pl.getColor("diffuse", diff);
        spec = pl.getColor("specular", spec);
        power = pl.getFloat("power", power);
        refl = pl.getFloat("shiny", refl);
        numRays = pl.getInt("samples", numRays);
        return true;
    }

    protected Color getDiffuse(ShadingState state) {
        return diff;
    }

    public Color getRadiance(ShadingState state) {
        // make sure we are on the right side of the material
        state.faceforward();
        // setup lighting
        state.initLightSamples();
        state.initCausticSamples();
        // execute shader
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
        //
        Color ret = Color.white();
        Color r = d.copy().mul(refl);
        ret.sub(r);
        ret.mul(cos5);
        ret.add(r);
        return state.diffuse(d).add(state.specularPhong(spec, power, numRays)).add(ret.mul(state.traceReflection(refRay, 0)));
    }

    public void scatterPhoton(ShadingState state, Color power) {
        // make sure we are on the right side of the material
        state.faceforward();
        Color d = getDiffuse(state);
        state.storePhoton(state.getRay().getDirection(), power, d);
        float avgD = d.getAverage();
        float avgS = spec.getAverage();
        double rnd = state.getRandom(0, 0, 1);
        if (rnd < avgD) {
            // photon is scattered diffusely
            power.mul(d).mul(1.0f / avgD);
            OrthoNormalBasis onb = state.getBasis();
            double u = 2 * Math.PI * rnd / avgD;
            double v = state.getRandom(0, 1, 1);
            float s = (float) Math.sqrt(v);
            float s1 = (float) Math.sqrt(1.0f - v);
            Vector3 w = new Vector3((float) Math.cos(u) * s, (float) Math.sin(u) * s, s1);
            w = onb.transform(w, new Vector3());
            state.traceDiffusePhoton(new Ray(state.getPoint(), w), power);
        } else if (rnd < avgD + avgS) {
            // photon is scattered specularly
            float dn = 2.0f * state.getCosND();
            // reflected direction
            Vector3 refDir = new Vector3();
            refDir.x = (dn * state.getNormal().x) + state.getRay().dx;
            refDir.y = (dn * state.getNormal().y) + state.getRay().dy;
            refDir.z = (dn * state.getNormal().z) + state.getRay().dz;
            power.mul(spec).mul(1.0f / avgS);
            OrthoNormalBasis onb = state.getBasis();
            double u = 2 * Math.PI * (rnd - avgD) / avgS;
            double v = state.getRandom(0, 1, 1);
            float s = (float) Math.pow(v, 1 / (this.power + 1));
            float s1 = (float) Math.sqrt(1 - s * s);
            Vector3 w = new Vector3((float) Math.cos(u) * s1, (float) Math.sin(u) * s1, s);
            w = onb.transform(w, new Vector3());
            state.traceReflectionPhoton(new Ray(state.getPoint(), w), power);
        }
    }
}

