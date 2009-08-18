package org.sunflow.core;

import org.sunflow.core.Shader;
import org.sunflow.core.ShadingState;
import org.sunflow.image.Color;

public interface AlphaShader extends Shader {

    public Color getOpacity(ShadingState state);

}

