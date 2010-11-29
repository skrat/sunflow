package org.sunflow.core.camera;

import org.sunflow.SunflowAPI;
import org.sunflow.core.CameraLens;
import org.sunflow.core.ParameterList;
import org.sunflow.core.Ray;

public class OrthogonalLens implements CameraLens {

    private float scale;

    public Ray getRay(float x, float y, int imageWidth, int imageHeight,
            double lensX, double lensY, double time) {
        return new Ray(x / scale - imageWidth / (2f * scale), y / scale
                - imageHeight / (2f * scale), 0, 0, 0, -1);
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        this.scale = pl.getFloat("scale", 1f);
        return true;
    }

}
