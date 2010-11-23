package org.sunflow.core.camera;

import org.sunflow.SunflowAPI;
import org.sunflow.core.CameraLens;
import org.sunflow.core.ParameterList;
import org.sunflow.core.Ray;

public class OrthogonalLens implements CameraLens {

    public boolean update(ParameterList pl, SunflowAPI api) {
        return true;
    }

    public Ray getRay(float x, float y, int imageWidth, int imageHeight, double lensX, double lensY, double time) {
        float du = (2f * x) / (imageWidth - 1.0f);
        float dv = (2f * y) / (imageHeight - 1.0f);
        return new Ray(0, 0, 0, du, dv, -1);
    }

}
