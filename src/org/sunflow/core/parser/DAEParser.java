package org.sunflow.core.parser;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Iterator;

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

    private FastHashMap<String, FastHashMap<String, Integer>> geometriesCache;
    private LinkedList<String> shadersCache;

    private String actualSceneId; // TODO: handle multiple scenes

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
            setGlobalIllumination();
            setTraceDepths();
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

    private void setGlobalIllumination() {
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

    private void setTraceDepths() {
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

            transform(cameraInstance);
            api.parameter("fov", fov);
            api.parameter("aspect", aspectRatio);
            api.camera(cameraId, "pinhole");
            api.parameter("camera", cameraId);
            api.options(SunflowAPI.DEFAULT_OPTIONS);

        } catch(Exception e) {
            UI.printError(Module.SCENE, "Error loading camera: is there any?");
            UI.printInfo(Module.SCENE, "Using auto-positioned camera instead ...");
        }
    }

    private void loadGeometries() {
        try {
            geometriesCache = new FastHashMap<String, FastHashMap<String ,Integer>>();
            shadersCache = new LinkedList<String>();
            
            NodeList nodes = (NodeList) xpath.evaluate(getSceneQuery(actualSceneId)+"/node", dae, XPathConstants.NODESET);

            for (int i=0; i < nodes.getLength(); i++) {
                Element node = (Element) nodes.item(i);
                NodeList geometries = node.getElementsByTagName("instance_geometry");
                for (int j=0; j < geometries.getLength(); j++) {
                    Element geometryInstance = (Element) geometries.item(j);
                    String geometryId = geometryInstance.getAttribute("url").substring(1);

                    NodeList materials = geometryInstance.getElementsByTagName("instance_material");
                    String material = null;
                    if ( materials.getLength() > 0 ) {
                        // TODO: multiple materials per geometry
                        String materialId = ((Element) materials.item(0)).getAttribute("target").substring(1);
                        if ( !shadersCache.contains(materialId) ) {
                            loadShader(materialId);
                            shadersCache.add(materialId);
                        }
                        material = materialId;
                    }

                    FastHashMap<String, Integer> geoms = null;
                    if ( !geometriesCache.containsKey(geometryId) ) {
                        geoms = loadGeometry(geometryId);
                        geometriesCache.put(geometryId,geoms);
                    } else {
                        geoms = (FastHashMap<String, Integer>) geometriesCache.get(geometryId);
                    }


                    Iterator<FastHashMap.Entry<String, Integer>> it = geoms.iterator();
                    while ( it.hasNext() ) {
                        FastHashMap.Entry<String, Integer> g = it.next();
                        String gid = (String) g.getKey();
                        Integer ii = (Integer) g.getValue();

                        transform(geometryInstance);
                        if ( material != null ) {
                            api.parameter("shaders", new String[]{material});
                        }
                        api.instance(gid + "." + ii.toString() + ".instance", gid);

                        // instance counter
                        geoms.put(gid, ii+1);
                    }
                }
            }
        } catch(XPathExpressionException e) { }
    }

    private FastHashMap<String, Integer> loadGeometry(String geometryId) {
        try {
            NodeList trianglesList = (NodeList) xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles", dae, XPathConstants.NODESET);
            int trianglesNum = trianglesList.getLength();
            int[] triangles = null;
            FastHashMap<String, Integer> geoms = new FastHashMap<String, Integer>();

            UI.printInfo(Module.GEOM, "Reading mesh: %s ...", geometryId);

            // handle multiple <triangles> elements
            for (int i=0; i<trianglesNum; i++) {
                Element trisEl = (Element) trianglesList.item(i);
                NodeList inputs = (NodeList) trisEl.getElementsByTagName("input");
                Integer offset = inputs.getLength();

                float[] vertices = null;
                float[] normals  = null;
                float[] texcoord = null;
                for (int j=0; j<inputs.getLength(); j++) {
                    Element in = (Element) inputs.item(j);
                    String semantic = in.getAttribute("semantic");
                    String sourceId = in.getAttribute("source").substring(1);
                    if ( semantic.equals("VERTEX") ) {
                        String vertexDataId = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/vertices/input[@semantic='POSITION']/@source", dae).substring(1);
                        String vertexData = xpath.evaluate(getGeometrySourceQuery(geometryId, vertexDataId), dae);
                        vertices = parseFloats(vertexData);
                    } else if ( semantic.equals("NORMAL") ) {
                        String normalData = xpath.evaluate(getGeometrySourceQuery(geometryId, sourceId), dae);
                        normals = parseFloats(normalData);
                    } else if ( semantic.equals("TEXCOORD") ) {
                        String texcoordData = xpath.evaluate(getGeometrySourceQuery(geometryId, sourceId), dae);
                        texcoord = parseFloats(texcoordData);
                    }
                }

                String trianglesData = trisEl.getElementsByTagName("p").item(0).getTextContent();
                String[] trianglesStrings = trianglesData.trim().split("\\s+");
                int[] trianglez = new int[trianglesStrings.length/offset];
                for (int j=0; j < trianglez.length; j++) {
                    trianglez[j] = Integer.parseInt(trianglesStrings[j*offset]);
                }

                String gid = geometryId+"."+ Integer.toString(i);
                api.parameter("triangles", trianglez);
                api.parameter("points", "point", "vertex", vertices);
                api.geometry(gid, "triangle_mesh");
                geoms.put(gid, 0);

            }

            return geoms;

        } catch(Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading mesh: %s ...", geometryId);
            return null;
        }
    }

    private void loadShader(String materialId) {
        try {
            String effectId = xpath.evaluate(getMaterialQuery(materialId)+"/instance_effect/@url", dae).substring(1);
            Node technique = (Node) xpath.evaluate(getEffectQuery(effectId)+"/profile_COMMON/technique", dae, XPathConstants.NODE);
            for (Node childNode = technique.getFirstChild(); childNode != null;) {
                Node nextChild = childNode.getNextSibling();

                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) childNode;
                    String tagname = child.getTagName();
                    FastHashMap<String, Object> shaderParams = getShaderParams(child);

                    if ( tagname.equals("phong") ) {

                        Object diffuseObj = shaderParams.get("diffuse");
                        boolean texture = false;
                        try {
                            // try to get color
                            float[] df = (float[]) diffuseObj;
                            api.parameter("diffuse", null, new Color(df[0],df[1],df[2]).getRGB());
                        } catch (ClassCastException e) {
                            // handle texture
                            Element df = (Element) diffuseObj;
                            api.parameter("texture", getTexture(effectId, df));
                            texture = true;
                        }
                        float[] sf = (float[]) shaderParams.get("specular");
                        float[] pf = (float[]) shaderParams.get("shininess");
                        api.parameter("specular", null, new Color(sf[0],sf[1],sf[3]).getRGB());
                        api.parameter("power", pf[0]);
                        api.parameter("samples", 0); // TODO: fix this
                        if (texture) {
                            api.shader(materialId, "textured_phong");
                        } else {
                            api.shader(materialId, "phong");
                        }

                    } else if ( tagname.equals("lambert") ) {

                        Object diffuseObj = shaderParams.get("diffuse");
                        try {
                            // try to get color
                            float[] df = (float[]) diffuseObj;
                            api.parameter("diffuse", null, new Color(df[0],df[1],df[2]).getRGB());
                            api.shader(materialId, "diffuse");
                        } catch (Exception e) {
                            // handle texture
                            Element df = (Element) diffuseObj;
                            api.parameter("texture", getTexture(effectId, df));
                            api.shader(materialId, "textured_diffuse");
                        }

                    }
                }

                childNode = nextChild;
            }

        } catch(Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading material: %s ...", materialId);
        }
    }

    private FastHashMap<String, Object> getShaderParams(Element shaderEl) {
        FastHashMap<String, Object> result = new FastHashMap<String, Object>();
        for (Node childNode = shaderEl.getFirstChild(); childNode != null;) {
            Node nextChild = childNode.getNextSibling();

            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child  = (Element) childNode;
                String tagname = child.getTagName();
                String value   = child.getTextContent();

                try {
                    result.put(tagname, parseFloats(value));
                } catch (NumberFormatException e) {
                    result.put(tagname, child);
                }
            }

            childNode = nextChild;
        }
        return result;
    }

    private String getTexture(String effectId, Element param) {
        try {
            String texture = ((Element) param.getElementsByTagName("texture").item(0)).getAttribute("texture");
            String surface = xpath.evaluate(getEffectQuery(effectId)+String.format("/profile_COMMON/newparam[@sid='%s']/sampler2D/source/text()", texture), dae);
            String image   = xpath.evaluate(getEffectQuery(effectId)+String.format("/profile_COMMON/newparam[@sid='%s']/surface[@type='2D']/init_from/text()", surface), dae);
            return xpath.evaluate(getImageQuery(image), dae).trim();
        } catch (Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading texture: for effect %s ...", effectId);
            return null;
        }
    }

    private void transform(Element geometryInstance) {
        LinkedList<LinkedList> transforms = new LinkedList<LinkedList>();
        Element node = (Element) geometryInstance.getParentNode();

        // collect transformations
        for (; node != null && node.getTagName().equals("node"); node = (Element) node.getParentNode()) {

            LinkedList<Matrix4> levelTransforms = new LinkedList<Matrix4>();
            for (Node childNode = node.getFirstChild(); childNode!=null;) {

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

                    } else if(tagname.equals("rotate")) {
                        float[] floats = parseFloats(child.getTextContent());
                        if (floats[0] == 1.0) {
                            levelTransforms.add( Matrix4.rotateX((float) Math.toRadians(floats[3])) );
                        } else if (floats[1] == 1.0) {
                            levelTransforms.add( Matrix4.rotateY((float) Math.toRadians(floats[3])) );
                        } else if (floats[2] == 1.0) {
                            levelTransforms.add( Matrix4.rotateZ((float) Math.toRadians(floats[3])) );
                        }

                    } else if(tagname.equals("lookat")) {
                        float[] floats = parseFloats(child.getTextContent());
                        Point3 eye     = new Point3(floats[0],floats[1],floats[2]);
                        Point3 target  = new Point3(floats[3],floats[4],floats[5]);
                        Vector3 up     = new Vector3(floats[6],floats[7],floats[8]);
                        levelTransforms.add( Matrix4.lookAt(eye, target, up) );
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
        } catch(Exception e) {
            return null;
        }
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

    private String getGeometrySourceQuery(String geometryId, String inputId) {
        return "/COLLADA/library_geometries/geometry[@id='"+geometryId+"']/mesh/source[@id='"+inputId+"']/float_array/text()";
    }

    private String getMaterialQuery(String materialId) {
        return String.format("/COLLADA/library_materials/material[@id='%s']", materialId);
    }

    private String getEffectQuery(String effectId) {
        return String.format("/COLLADA/library_effects/effect[@id='%s']", effectId);
    }

    private String getImageQuery(String imageId) {
        return String.format("/COLLADA/library_images/image[@id='%s']/init_from/text()", imageId);
    }

    private Color parseColor(String colorString) {
        try {
            float[] rgb = parseFloats(colorString);
            return new Color(rgb[0],rgb[1],rgb[2]);
        } catch(Exception e) {
            return null;
        }
    }

    private Vector3 parseVector(String vectorString) {
        float[] vectorFloats = parseFloats(vectorString);
        return new Vector3(vectorFloats[0],vectorFloats[1],vectorFloats[2]);
    }

    private float[] parseFloats(String floatString) {
        String[] floatStrings = floatString.trim().split("\\s+");
        float[] floats = new float[floatStrings.length];
        for (int i=0; i<floats.length; i++) {
            floats[i] = Float.parseFloat(floatStrings[i]);
        }
        return floats;
    }
}
