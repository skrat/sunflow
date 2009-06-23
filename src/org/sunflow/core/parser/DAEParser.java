package org.sunflow.core.parser;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;

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
import org.sunflow.image.Color;
import org.sunflow.util.FloatArray;
import org.sunflow.util.IntArray;
import org.sunflow.util.FastHashMap;

public class DAEParser implements SceneParser {

    private SunflowAPIInterface api;
    private Document dae;
    private XPath xpath;
    private FastHashMap<String, Integer> geometriesCache;

    private String actualSceneId;

    public DAEParser() {
        xpath = XPathFactory.newInstance().newXPath();
    }

    public boolean parse(String filename, SunflowAPIInterface api) {
        this.api = api;
        UI.printInfo(Module.GEOM, "COLLADA - Parsing file: %s ...", filename);
        try {
            DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            dae = parser.parse(new File(filename));
            actualSceneId = getSceneId();

            setImage();
            setBackground();
            setGI();
            setTrace();
            setCamera();

            loadGeometries();

        } catch(ParserConfigurationException e) {
            e.printStackTrace();
        } catch(SAXException e) {
            e.printStackTrace();
        } catch(XPathExpressionException e) {
            e.printStackTrace();
        } catch(IOException e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "COLLADA - Can't read DAE file \"%s\" - IO error", filename);
        }
        return true;
    }

    private void setImage() {
        int[] size = getImageDimensions(actualSceneId);
        if (size != null) {
            api.parameter("resolutionX", size[0]);
            api.parameter("resolutionY", size[1]);
        }

        try {
            String sampler = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/sampler/text()", dae);
            if (sampler != "") {
                api.parameter("sampler", sampler.trim());
            }
        } catch(XPathExpressionException e) { }

        try {
            String aa = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/aa/text()", dae);
            if (aa != "") {
                String[] aaStrings = aa.trim().split("\\s+");
                int[] aaInts = new int[2];
                aaInts[0] = Integer.parseInt(aaStrings[0]);
                aaInts[1] = Integer.parseInt(aaStrings[1]);
                api.parameter("aa.min", aaInts[0]);
                api.parameter("aa.max", aaInts[1]);
            }
        } catch(XPathExpressionException e) { }

        try {
            String samples = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/samples/text()", dae);
            if (samples != "") {
                api.parameter("aa.samples", Integer.parseInt(samples.trim()));
            }
        } catch(XPathExpressionException e) { }

        try {
            String contrast = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/contrast/text()", dae);
            if (contrast != "") {
                api.parameter("aa.contrast", Float.parseFloat(contrast.trim()));
            }
        } catch(XPathExpressionException e) { }

        try {
            String jitter = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/jitter/text()", dae);
            if (jitter != "") {
                api.parameter("aa.jitter", Boolean.valueOf(jitter).booleanValue());
            }
        } catch(XPathExpressionException e) { }

        try {
            String cache = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/cache/text()", dae);
            if (cache != "") {
                api.parameter("aa.cache", Boolean.valueOf(cache).booleanValue());
            }
        } catch(XPathExpressionException e) { }

        try {
            String filter = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image/filter/text()", dae);
            if (filter != "") {
                api.parameter("filter", filter.trim());
            }
        } catch(XPathExpressionException e) { }

        api.options(SunflowAPI.DEFAULT_OPTIONS);
    }

    private void setBackground() {
        Color c = getBackgroundColor(actualSceneId);
        if (c != null) {
            api.parameter("color", null, c.getRGB());
            api.shader("background.shader", "constant");
            api.geometry("background", "background");
            api.parameter("shaders", "background.shader");
            api.instance("background.instance", "background");
        }
    }

    private void setGI() {
        try {
            String type = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/type",dae).trim();
            if (type.equals("irr-cache")) {

                api.parameter("gi.engine", "irr-cache");
                try {
                    String samples = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/samples/text()", dae);
                    if (samples != "") {
                        api.parameter("gi.irr-cache.samples", Integer.parseInt(samples.trim()));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String tolerance = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/tolerance/text()", dae);
                    if (tolerance != "") {
                        api.parameter("gi.irr-cache.samples", Float.parseFloat(tolerance.trim()));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String spacing = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/spacing/text()", dae);
                    if (spacing != "") {
                        String[] spacingStrings = spacing.trim().split("\\s+");
                        api.parameter("gi.irr-cache.min_spacing", Float.parseFloat(spacingStrings[0]));
                        api.parameter("gi.irr-cache.max_spacing", Float.parseFloat(spacingStrings[1]));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String global = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/global/text()", dae);
                    if (global != "") {
                        String[] globalStrings = global.trim().split("\\s+");
                        api.parameter("gi.irr-cache.gmap.emit", Integer.parseInt(globalStrings[0]));
                        api.parameter("gi.irr-cache.gmap", globalStrings[1]);
                        api.parameter("gi.irr-cache.gmap.gather", Integer.parseInt(globalStrings[2]));
                        api.parameter("gi.irr-cache.gmap.radius", Float.parseFloat(globalStrings[3]));
                    }
                } catch(XPathExpressionException e) { }

            } else if (type.equals("path")) {

                api.parameter("gi.engine", "path");
                try {
                    String samples = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/samples/text()", dae);
                    if (samples != "") {
                        api.parameter("gi.path.samples", Integer.parseInt(samples.trim()));
                    }
                } catch(XPathExpressionException e) { }
            
            } else if (type.equals("fake")) {

                api.parameter("gi.engine", "fake");
                try {
                    String up = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/up/text()", dae);
                    if (up != "") {
                        api.parameter("gi.fake.up", parseVector(up));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String sky = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/sky/text()", dae);
                    if (sky != null) {
                        api.parameter("gi.fake.sky", null, parseColor(sky).getRGB());
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String ground = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/ground/text()", dae);
                    if (ground != null) {
                        api.parameter("gi.fake.ground", null, parseColor(ground).getRGB());
                    }
                } catch(XPathExpressionException e) { }

            } else if (type.equals("igi")) {

                api.parameter("gi.engine", "igi");
                try {
                    String samples = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/samples/text()", dae);
                    if (samples != "") {
                        api.parameter("gi.igi.samples", Integer.parseInt(samples.trim()));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String sets = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/sets/text()", dae);
                    if (sets != "") {
                        api.parameter("gi.igi.sets", Integer.parseInt(sets.trim()));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String bias = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/bias/text()", dae);
                    if (bias != "") {
                        api.parameter("gi.igi.c", Float.parseFloat(bias.trim()));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String biasSamples = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/bias_samples/text()", dae);
                    if (biasSamples != "") {
                        api.parameter("gi.igi.bias_samples", Integer.parseInt(biasSamples.trim()));
                    }
                } catch(XPathExpressionException e) { }

            } else if (type.equals("ambocc")) {

                api.parameter("gi.engine", "ambocc");
                try {
                    String samples = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/samples/text()", dae);
                    if (samples != "") {
                        api.parameter("gi.ambocc.samples", Integer.parseInt(samples.trim()));
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String bright = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/bright/text()", dae);
                    if (bright != null) {
                        api.parameter("gi.ambocc.bright", null, parseColor(bright).getRGB());
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String dark = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/dark/text()", dae);
                    if (dark != null) {
                        api.parameter("gi.ambocc.dark", null, parseColor(dark).getRGB());
                    }
                } catch(XPathExpressionException e) { }

                try {
                    String maxdist = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi/maxdist/text()", dae);
                    if (maxdist != "") {
                        api.parameter("gi.ambocc.maxdist", Float.parseFloat(maxdist.trim()));
                    }
                } catch(XPathExpressionException e) { }

            }
        } catch(XPathExpressionException e) {
            api.parameter("gi.engine", "none");
        }
        api.options(SunflowAPI.DEFAULT_OPTIONS);
    }

    private void setTrace() {
        try {
            String diff = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/trace_depths/diffuse/text()", dae);
            if (diff != "") {
                api.parameter("depths.diffuse", Integer.parseInt(diff.trim()));
            }
        } catch(XPathExpressionException e) { }

        try {
            String refl = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/trace_depths/reflection/text()", dae);
            if (refl != "") {
                api.parameter("depths.reflection", Integer.parseInt(refl.trim()));
            }
        } catch(XPathExpressionException e) { }

        try {
            String refr = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/trace_depths/refraction/text()", dae);
            if (refr != "") {
                api.parameter("depths.refraction", Integer.parseInt(refr.trim()));
            }
        } catch(XPathExpressionException e) { }
        api.options(SunflowAPI.DEFAULT_OPTIONS);
    }

    private void setCamera() {
        try {
            Element cameraInstance = (Element) xpath.evaluate(getSceneQuery(actualSceneId)+"//instance_camera", dae, XPathConstants.NODE);
            String cameraId = cameraInstance.getAttribute("url").substring(1);

            UI.printInfo(Module.SCENE, "Got camera: %s ...", cameraId);

            Float xfov, yfov, fov;
            try {
                xfov = Float.parseFloat(xpath.evaluate(getCameraQuery(cameraId)+"/optics/technique_common/perspective/xfov", dae));
            } catch(Exception e) {
                xfov = null;
            }
            try {
                yfov = Float.parseFloat(xpath.evaluate(getCameraQuery(cameraId)+"/optics/technique_common/perspective/yfov", dae));
            } catch(Exception e) {
                yfov = null;
            }
            fov = 0.0f;
            if (xfov != null)  {
                fov += xfov;
            }
            if (yfov != null) {
                fov += yfov;
            }
            // just one FOV
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
            } catch(Exception e) {
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
            UI.printError(Module.SCENE, "Error getting camera: is there any?");
        }
    }

    private void loadGeometries() {
        try {
                api.parameter("diffuse", null, parseColor("0.5 0.8 0.1").getRGB());
                api.shader("std", "diffuse");

            geometriesCache = new FastHashMap<String, Integer>();
            NodeList nodes = (NodeList) xpath.evaluate(getSceneQuery(actualSceneId)+"/node", dae, XPathConstants.NODESET);
            for (int i=0; i < nodes.getLength(); i++) {
                Element node = (Element) nodes.item(i);
                NodeList geometries = node.getElementsByTagName("instance_geometry");
                for (int j=0; j < geometries.getLength(); j++) {
                    Element geometryInstance = (Element) geometries.item(j);
                    String geometryId = geometryInstance.getAttribute("url").substring(1);

                    if ( !geometriesCache.containsKey(geometryId) ) {
                        loadGeometry(geometryId);
                        geometriesCache.put(geometryId,0);
                    }
                    Integer ii = geometriesCache.get(geometryId);

                    transform(geometryInstance);
                    api.instance(geometryId + "." + ii.toString() + ".instance", geometryId);
                    geometriesCache.put(geometryId,ii+1);
                }
            }
        } catch(XPathExpressionException e) { }
    }

    private void loadGeometry(String geometryId) {
        try {
            Integer offset = ((NodeList) xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles/input", dae, XPathConstants.NODESET)).getLength();

            String verticesId    = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles/input[@semantic='VERTEX']/@source", dae).substring(1);
            String normalsId     = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles/input[@semantic='NORMAL']/@source", dae);
            String uvsId         = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles/input[@semantic='TEXCOORD']/@source", dae);

            String verticesSourceId = xpath.evaluate(getGeometryQuery(geometryId)+String.format("/mesh/vertices[@id='%s']/input[@semantic='POSITION']/@source", verticesId), dae).substring(1);
            String verticesData = xpath.evaluate(getGeometryQuery(geometryId)+String.format("/mesh/source[@id='%s']/float_array/text()", verticesSourceId), dae);

            // load vertices
            String[] verticesStrings = verticesData.trim().split("\\s+");
            float[] points = new float[verticesStrings.length];
            for (int i = 0; i < verticesStrings.length; i++) {
                points[i] = Float.parseFloat(verticesStrings[i]);
            }
            
            // load normals
            float[] normals = null;
            if (normalsId != "") {
                String normalsData = xpath.evaluate(getGeometryQuery(geometryId)+String.format("/mesh/source[@id='%s']/float_array/text()", normalsId.substring(1)), dae);
                if (normalsData != "") {
                    String[] normalsStrings = normalsData.trim().split("\\s+");
                    normals = new float[normalsStrings.length];
                    for (int i=0; i<normals.length; i++) {
                        normals[i] = Float.parseFloat(normalsStrings[i]);
                    }
                }
            }

            // load UVs
            float[] texcoords = null;
            if (uvsId != "") {
                String uvsData = xpath.evaluate(getGeometryQuery(geometryId)+String.format("/mesh/source[@id='%s']/float_array/text()", uvsId.substring(1)), dae);
                if (uvsData != "") {
                    String[] uvsStrings = uvsData.trim().split("\\s+");
                    texcoords = new float[uvsStrings.length];
                    for (int i=0; i<texcoords.length; i++) {
                        texcoords[i] = Float.parseFloat(uvsStrings[i]);
                    }
                }
            }

            String trianglesData = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles/p/text()", dae);
            UI.printInfo(Module.GEOM, "Reading mesh: %s ...", geometryId);

            String[] trianglesStrings = trianglesData.trim().split("\\s+");
            int[] triangles = new int[trianglesStrings.length/offset];
            for (int i = 0; i < triangles.length; i++) {
                triangles[i] = Integer.parseInt(trianglesStrings[i*offset]);
            }

                api.parameter("triangles", triangles);
                api.parameter("points", "point", "vertex", points);
                api.geometry(geometryId, "triangle_mesh");
                api.parameter("shaders", new String[]{"std"});

        } catch(Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading mesh: %s ...", geometryId);
        }
    }

    private void transform(Element geometryInstance) {
        LinkedList<LinkedList> transforms = new LinkedList<LinkedList>();
        Element node = (Element) geometryInstance.getParentNode();

        // collect transformations
        for (; node != null && node.getTagName().equals("node"); node = (Element) node.getParentNode()) {

            LinkedList<Matrix4> levelTransforms = new LinkedList<Matrix4>();
            for(Node childNode = node.getFirstChild(); childNode!=null;){

                Node nextChild = childNode.getNextSibling();
                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) childNode;
                    String tagname = child.getTagName();

                    if (tagname.equals("scale")) {
                        Vector3 scale = parseVector(child.getTextContent());
                        levelTransforms.add( Matrix4.scale(scale.x,scale.y,scale.z) );

                    } else if (tagname.equals("translate")) {
                        Vector3 translation = parseVector(child.getTextContent());
                        levelTransforms.add( Matrix4.translation(translation.x,translation.y,translation.z) );

                    }
                }
                childNode = nextChild;
            }
            transforms.add(levelTransforms);
        }


        // iterate them from the end (topmost node)
        Matrix4 m = Matrix4.IDENTITY;
        for (int i = transforms.size()-1; i > -1; i--) {
            ListIterator li = transforms.get(i).listIterator();
            while( li.hasNext() ) {
                Matrix4 t = (Matrix4) li.next();
                m = m.multiply(t);
            }
        }
        api.parameter("transform", m);
    }

    private String getSceneId() throws XPathExpressionException {
        return xpath.evaluate("/COLLADA/scene/instance_visual_scene/@url", dae).substring(1); 
    }

    private String getSceneQuery(String sceneId) {
        return String.format("/COLLADA/library_visual_scenes/visual_scene[@id='%s']", sceneId);
    }

    private String getSunflowSceneQuery(String sceneId) {
        return String.format(getSceneQuery(sceneId)+"/extra/technique[@sid='sunflow']", sceneId);
    }

    private String getCameraQuery(String sceneId) {
        return String.format("/COLLADA/library_cameras/camera[@id='%s']", sceneId);
    }

    private String getGeometryQuery(String geometryId) {
        return String.format("/COLLADA/library_geometries/geometry[@id='%s']", geometryId);
    }

    private void transformLookAt(Node lookAtNode) {
        // TODO: motion blur
        // String offset = index < 0 ? "" : String.format("[%d]", index);
        
        String offset = "";
        float[] lookAtFloats = new float[9];
        String[] lookAtStrings = lookAtNode.getTextContent().trim().split("\\s+");
        
        for (int i=0; i < lookAtStrings.length; i++) {
            lookAtFloats[i] = Float.parseFloat(lookAtStrings[i]);
        }

        Point3 eye = new Point3(lookAtFloats[0],lookAtFloats[1],lookAtFloats[2]);
        Point3 target = new Point3(lookAtFloats[3],lookAtFloats[4],lookAtFloats[5]);
        Vector3 up = new Vector3(lookAtFloats[6],lookAtFloats[7],lookAtFloats[8]);

        api.parameter(String.format("transform%s", offset), Matrix4.lookAt(eye, target, up));
    }

    private Color getBackgroundColor(String sceneId) {
        try {
            String colorString = xpath.evaluate(getSunflowSceneQuery(sceneId)+"/background/color", dae);
            return parseColor(colorString);
        } catch(XPathExpressionException e) {
            return null;
        }
    }

    private int[] getImageDimensions(String sceneId) {
        try {
            String intsString = xpath.evaluate(getSunflowSceneQuery(sceneId)+"/image/resolution", dae);
            String[] dimensionsString = intsString.trim().split("\\s+");
            int[] dimensions = new int[2];
            dimensions[0] = Integer.parseInt(dimensionsString[0]);
            dimensions[1] = Integer.parseInt(dimensionsString[1]);

            return dimensions;
        } catch(XPathExpressionException e) {
            return null;
        }
    }

    private Color parseColor(String colorString) {
        float[] rgb = new float[3];
        String[] colors = colorString.trim().split("\\s+");
        for(int i=0; i<3; i++) {
            rgb[i] = Float.parseFloat(colors[i]);
        }
        return new Color(rgb[0],rgb[1],rgb[2]);
    }

    private Vector3 parseVector(String vectorString) {
        String[] vectorStrings = vectorString.trim().split("\\s+");
        float[] vectorFloats = new float[3];
        for(int i=0; i<3; i++) {
            vectorFloats[i] = Float.parseFloat(vectorStrings[i]);
        }
        return new Vector3(vectorFloats[0],vectorFloats[1],vectorFloats[2]);
    }
}
