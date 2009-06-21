package org.sunflow.core.parser;

import java.io.File;
import java.io.IOException;

import javax.xml.xpath.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.sunflow.SunflowAPI;
import org.sunflow.SunflowAPIInterface;
import org.sunflow.core.SceneParser;
import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;
import org.sunflow.math.Matrix4;
import org.sunflow.math.Point3;
import org.sunflow.math.Vector3;
import org.sunflow.util.FloatArray;
import org.sunflow.util.IntArray;

public class DAEParser implements SceneParser {

    private SunflowAPIInterface api;
    private Document dae;
    private XPath xpath;

    public DAEParser() {
        xpath = XPathFactory.newInstance().newXPath();
    }

    public boolean parse(String filename, SunflowAPIInterface api) {
        this.api = api;
        UI.printInfo(Module.GEOM, "COLLADA - Parsing file: %s ...", filename);
        try {
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            dae = parser.parse(new File(filename));

            setCamera();
        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(SAXException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "COLLADA - Can't read DAE file \"%s\" - IO error", filename);
        }
        return true;
    }

    private void setCamera() {
        try {
            String actualSceneId = getSceneId();
            Element cameraInstance = (Element) xpath.evaluate(getSceneQuery(actualSceneId)+"//instance_camera", dae, XPathConstants.NODE);
            String cameraId = cameraInstance.getAttribute("url").substring(1);

            Float xfov, yfov, fov;
            try {
                xfov = Float.parseFloat(xpath.evaluate(getCameraQuery(cameraId)+"/optics/technique_common/perspective/xfov", dae));
            } catch(NumberFormatException e) {
                xfov = null;
            }
            try {
                yfov = Float.parseFloat(xpath.evaluate(getCameraQuery(cameraId)+"/optics/technique_common/perspective/yfov", dae));
            } catch(NumberFormatException e) {
                yfov = null;
            }
            fov = 0.0f;
            if (xfov != null)  {
                fov += xfov;
            }
            if (yfov != null) {
                fov += yfov;
            }
            if (xfov != null && yfov != null) {
                fov = fov/2.0f;
            }
            if (fov == 0.0f) {
                // default value
                fov = 45.0f;
            }


            Float aspectRatio;
            try {
                aspectRatio = Float.parseFloat(xpath.evaluate(getCameraQuery(cameraId)+"/optics/technique_common/perspective/aspect_ratio", dae));
            } catch(NumberFormatException e) {
                // default value
                aspectRatio = 1.333f;
            }

            Node lookAt = ((Element) cameraInstance.getParentNode()).getElementsByTagName("lookat").item(0);

            transformLookAt(lookAt);
            api.parameter("fov", fov);
            api.parameter("aspect", aspectRatio);
            api.camera(cameraId, "pinhole");
            api.parameter("camera", cameraId);
            api.options(SunflowAPI.DEFAULT_OPTIONS);

        } catch(XPathExpressionException e) {
            e.printStackTrace();
        }
    }

    private String getSceneId() throws XPathExpressionException {
        return xpath.evaluate("/COLLADA/scene/instance_visual_scene/@url", dae).substring(1); 
    }

    private String getSceneQuery(String id) {
        return String.format("/COLLADA/library_visual_scenes/visual_scene[@id='%s']", id);
    }

    private String getCameraQuery(String id) {
        return String.format("/COLLADA/library_cameras/camera[@id='%s']", id);
    }

    private void transformLookAt(Node lookAtNode) {
        // TODO: motion blur
        // String offset = index < 0 ? "" : String.format("[%d]", index);
        
        String offset = "";
        float[] lookAtFloats = new float[9];
        String[] lookAtStrs = lookAtNode.getTextContent().trim().split("\\s+");
        for (int i=0; i < lookAtStrs.length; i++) {
            lookAtFloats[i] = Float.parseFloat(lookAtStrs[i]);
        }
        Point3 eye = new Point3(lookAtFloats[0],lookAtFloats[1],lookAtFloats[2]);
        Point3 target = new Point3(lookAtFloats[3],lookAtFloats[4],lookAtFloats[5]);
        Vector3 up = new Vector3(lookAtFloats[6],lookAtFloats[7],lookAtFloats[8]);

        api.parameter(String.format("transform%s", offset), Matrix4.lookAt(eye, target, up));
    }
}
