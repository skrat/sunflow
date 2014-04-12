package org.sunflow.core.parser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Iterator;
import java.util.LinkedList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.sunflow.SunflowAPI;
import org.sunflow.SunflowAPIInterface;
import org.sunflow.core.SceneParser;
import org.sunflow.image.Color;
import org.sunflow.math.Matrix4;
import org.sunflow.math.Point3;
import org.sunflow.math.Vector3;
import org.sunflow.system.Timer;
import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;
import org.sunflow.util.FastHashMap;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class DAEParser implements SceneParser {

    private SunflowAPI api;
    private Document dae;
    private XPath xpath;
    private DocumentBuilder parser;
    private String camera;

    private FastHashMap<String, FastHashMap<String, Geometry>> geometryCache;
    private FastHashMap<String, Integer> lightCache;
    private FastHashMap<Node, Matrix4> transformCache;
    private LinkedList<String> shaderCache;
    private FastHashMap<String, Document> documentCache;

    private String actualSceneId; // TODO: handle multiple scenes

    private static int FACE = 11;
    private static int VERTEX = 12;

    public static String SCHEMA_LANGUAGE =
    "http://java.sun.com/xml/jaxp/properties/schemaLanguage",
                         XML_SCHEMA =
    "http://www.w3.org/2001/XMLSchema",
                         SCHEMA_SOURCE =
    "http://java.sun.com/xml/jaxp/properties/schemaSource";


    private class Geometry {
        public String material;
        public int instancesCount;

        public Geometry(String mat, int c) {
            material = mat;
            instancesCount = c;
        }
    }

    public DAEParser() {
        xpath = XPathFactory.newInstance().newXPath();
    }

    public boolean parse(String filename, SunflowAPIInterface api) {
        this.api = (SunflowAPI) api;
        UI.printInfo(Module.API, "COLLADA - Parsing file: %s ...", filename);
        Timer timer = new Timer();
        timer.start();
        try {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            parser = f.newDocumentBuilder();

            dae = parser.parse(new File(filename));
            actualSceneId = getSceneId(dae);
            camera = null;

            setImage();
            setBackground();
            setSunsky();
            setPhoton();
            setGlobalIllumination();
            setTraceDepths();

            loadScene();

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
        timer.end();
        UI.printInfo(Module.API, "Done parsing.");
        UI.printInfo(Module.API, "Parsing time: %s", timer.toString());
        return true;
    }

    private void setImage() {
        try {
            Element imageElement = (Element) xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/image", dae, XPathConstants.NODE);
            FastHashMap<String, Object> imageParams = getParams(imageElement);

            try {
                float[] size = (float[]) imageParams.get("resolution");
                if (size != null && size.length == 2) {
                    api.parameter("resolutionX", (int) size[0]);
                    api.parameter("resolutionY", (int) size[1]);
                }
            } catch(Exception e) { }

            try {
                String sampler = ((Element) imageParams.get("sampler")).getTextContent();
                if (sampler != "") {
                    api.parameter("sampler", sampler.trim());
                }
            } catch(Exception e) { }

            try {
                float[] aa = (float[]) imageParams.get("aa");
                if (aa != null && aa.length == 2) {
                    api.parameter("aa.min", (int) aa[0]);
                    api.parameter("aa.max", (int) aa[1]);
                }
            } catch(Exception e) { }

            try {
                api.parameter("aa.samples", (int)((float[]) imageParams.get("samples"))[0]);
            } catch(Exception e) { }

            try {
                api.parameter("aa.contrast", ((float[]) imageParams.get("contrast"))[0]);
            } catch(Exception e) { }

            try {
                String jitter = ((Element) imageParams.get("jitter")).getTextContent().trim();
                if (jitter != "") {
                    api.parameter("aa.jitter", Boolean.valueOf(jitter).booleanValue());
                }
            } catch(Exception e) { }

            try {
                String cache = ((Element) imageParams.get("cache")).getTextContent().trim();
                if (cache != "") {
                    api.parameter("aa.cache", Boolean.valueOf(cache).booleanValue());
                }
            } catch(Exception e) { }

            try {
                String filter = ((Element) imageParams.get("filter")).getTextContent().trim();
                if (filter != "") {
                    api.parameter("filter", filter);
                }
            } catch(Exception e) { }

        } catch(Exception e) { }

        api.options(SunflowAPI.DEFAULT_OPTIONS);
    }

    private void setBackground() {
        try {
            String colorString = xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/background/color", dae);
            Color c = parseColor(colorString);
            if (c != null) {
                api.parameter("color", null, c.getRGB());
                api.shader("background.shader", "constant");
                api.geometry("background", "background");
                api.parameter("shaders", "background.shader");
                api.instance("background.instance", "background");
            }
        } catch(XPathExpressionException e) { }
    }

    private void setSunsky() {
        try {
            Element sunskyElement = (Element) xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/sunsky", dae, XPathConstants.NODE);
            FastHashMap<String, Object> sunsky = getParams(sunskyElement);

            try {
                float[] up = (float[]) sunsky.get("up");
                api.parameter("up", new Vector3(up[0],up[1],up[2]));
            } catch(Exception e) { }

            try {
                float[] east = (float[]) sunsky.get("east");
                api.parameter("east", new Vector3(east[0],east[1],east[2]));
            } catch(Exception e) { }

            try {
                float[] sundir = (float[]) sunsky.get("sundir");
                api.parameter("sundir", new Vector3(sundir[0],sundir[1],sundir[2]));
            } catch(Exception e) { }

            try {
                api.parameter("turbidity", ((float[]) sunsky.get("turbidity"))[0]);
            } catch(Exception e) { }

            try {
                api.parameter("samples", (int)((float[]) sunsky.get("samples"))[0]);
            } catch(Exception e) { }

            try {
                String extendsky = ((Element) sunsky.get("ground_extendsky")).getTextContent().trim();
                api.parameter("ground.extendsky", Boolean.valueOf(extendsky).booleanValue());
            } catch(Exception e) { }

            try {
                float[] ground = (float[]) sunsky.get("ground");
                api.parameter("ground.color", null, new Color(ground[0],ground[1],ground[2]).getRGB());
            } catch(Exception e) { }

            api.light("sunsky", "sunsky");

        } catch(Exception e) { }
    }

    private void setPhoton() {
        try {
            Element photonElement = (Element) xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/photon", dae, XPathConstants.NODE);
            FastHashMap<String, Object> photon = getParams(photonElement);

            try {
                api.parameter("caustics", ((Element) photon.get("caustics")).getTextContent());
            } catch(Exception e) { }

            try {
                api.parameter("caustics.emit", (int)((float[]) photon.get("caustics_emit"))[0]);
            } catch(Exception e) { }

            try {
                api.parameter("caustics.gather", (int)((float[]) photon.get("caustics_gather"))[0]);
            } catch(Exception e) { }

            try {
                api.parameter("caustics.radius", ((float[]) photon.get("caustics_radius"))[0]);
            } catch(Exception e) { }

            api.options(SunflowAPI.DEFAULT_OPTIONS);

        } catch(Exception e) { }
    }

    private void setGlobalIllumination() {
        try {
            Element giElement = (Element) xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/gi", dae, XPathConstants.NODE);
            FastHashMap<String, Object> gi = getParams(giElement);
            String type = ((Element) gi.get("type")).getTextContent();
            if (type.equals("irr-cache")) {

                api.parameter("gi.engine", "irr-cache");
                try {
                    api.parameter("gi.irr-cache.samples", (int)((float[]) gi.get("samples"))[0]);
                } catch(Exception e) { }

                try {
                    api.parameter("gi.irr-cache.tolerance", ((float[]) gi.get("tolerance"))[0]);
                } catch(Exception e) { }

                try {
                    float[] spacing = (float[]) gi.get("spacing");
                    if (spacing != null && spacing.length == 2) {
                        api.parameter("gi.irr-cache.min_spacing", spacing[0]);
                        api.parameter("gi.irr-cache.max_spacing", spacing[1]);
                    }
                } catch(Exception e) { }

                try {
                    String global = ((Element) gi.get("global")).getTextContent();
                    if (global != "") {
                        String[] globalStrings = global.trim().split("\\s+");
                        api.parameter("gi.irr-cache.gmap.emit", Integer.parseInt(globalStrings[0]));
                        api.parameter("gi.irr-cache.gmap", globalStrings[1]);
                        api.parameter("gi.irr-cache.gmap.gather", Integer.parseInt(globalStrings[2]));
                        api.parameter("gi.irr-cache.gmap.radius", Float.parseFloat(globalStrings[3]));
                    }
                } catch(Exception e) { }

            } else if (type.equals("path")) {

                api.parameter("gi.engine", "path");
                try {
                    api.parameter("gi.path.samples", (int)((float[]) gi.get("samples"))[0]);
                } catch(Exception e) { }

            } else if (type.equals("fake")) {

                api.parameter("gi.engine", "fake");
                try {
                    float[] up = (float[]) gi.get("up");
                    if (up != null && up.length == 3) {
                        api.parameter("gi.fake.up", new Vector3(up[0],up[1],up[2]));
                    }
                } catch(Exception e) { }

                try {
                    float[] sky = (float[]) gi.get("sky");
                    if (sky != null && sky.length == 3) {
                        api.parameter("gi.fake.sky", null, new Color(sky[0],sky[1],sky[2]).getRGB());
                    }
                } catch(Exception e) { }

                try {
                    float[] ground = (float[]) gi.get("ground");
                    if (ground != null && ground.length == 3) {
                        api.parameter("gi.fake.ground", null, new Color(ground[0],ground[1],ground[2]).getRGB());
                    }
                } catch(Exception e) { }

            } else if (type.equals("igi")) {

                api.parameter("gi.engine", "igi");
                try {
                    api.parameter("gi.igi.samples", (int)((float[]) gi.get("samples"))[0]);
                } catch(Exception e) { }

                try {
                    api.parameter("gi.igi.sets", (int)((float[]) gi.get("sets"))[0]);
                } catch(Exception e) { }

                try {
                    api.parameter("gi.igi.c", ((float[]) gi.get("bias"))[0]);
                } catch(Exception e) { }

                try {
                    api.parameter("gi.igi.bias_samples", (int)((float[]) gi.get("bias_samples"))[0]);
                } catch(Exception e) { }

            } else if (type.equals("ambocc")) {

                api.parameter("gi.engine", "ambocc");
                try {
                    api.parameter("gi.ambocc.samples", (int)((float[]) gi.get("samples"))[0]);
                } catch(Exception e) { }

                try {
                    float[] bright = (float[]) gi.get("bright");
                    if (bright != null) {
                        api.parameter("gi.ambocc.bright", null, new Color(bright[0],bright[1],bright[2]).getRGB());
                    }
                } catch(Exception e) { }

                try {
                    float[] dark = (float[]) gi.get("dark");
                    if (dark != null) {
                        api.parameter("gi.ambocc.dark", null, new Color(dark[0],dark[1],dark[2]).getRGB());
                    }
                } catch(Exception e) { }

                try {
                    api.parameter("gi.ambocc.maxdist", ((float[]) gi.get("maxdist"))[0]);
                } catch(Exception e) { }

            }
        } catch(Exception e) {
            api.parameter("gi.engine", "none");
        }
        api.options(SunflowAPI.DEFAULT_OPTIONS);
    }

    private void setTraceDepths() {
        try {
            Element depthsElement = (Element) xpath.evaluate(getSunflowSceneQuery(actualSceneId)+"/trace_depths", dae, XPathConstants.NODE);
            FastHashMap<String, Object> depths = getParams(depthsElement);

            try {
                api.parameter("depths.diffuse", (int)((float[]) depths.get("diffuse"))[0]);
            } catch(Exception e) { }

            try {
                api.parameter("depths.reflection", (int)((float[]) depths.get("reflection"))[0]);
            } catch(Exception e) { }

            try {
                api.parameter("depths.refraction", (int)((float[]) depths.get("refraction"))[0]);
            } catch(Exception e) { }

        } catch(Exception e) { }

        api.options(SunflowAPI.DEFAULT_OPTIONS);
    }

    private void setCamera(Matrix4 transform) {
        try {
            String cameraId = this.camera;

            UI.printInfo(Module.SCENE, "Got camera: %s ...", cameraId);

            Element opticsElement = (Element) xpath.evaluate(getCameraQuery(cameraId)+"/optics/technique_common/perspective", dae, XPathConstants.NODE);
            FastHashMap<String, Object> optics = getParams(opticsElement);
            Float xfov = null,
                  yfov = null,
                   fov = null,
                 znear = null,
                  zfar = null;

            try {
                xfov = ((float[]) optics.get("xfov"))[0];
            } catch(Exception e) { }
            try {
                yfov = ((float[]) optics.get("yfov"))[0];
            } catch(Exception e) { }
            fov = 0.0f;
            if (xfov != null)  {  fov += xfov;    }
            if (yfov != null)  {  fov += yfov;    }
            if (xfov != null && yfov != null)
                               {  fov = fov/2.0f; }

            try {
                znear = ((float[]) optics.get("znear"))[0];
            } catch(Exception e) { }
            try {
                zfar = ((float[]) optics.get("zfar"))[0];
            } catch(Exception e) { }

            // default value
            Float aspectRatio = 1.333f;
            try {
                aspectRatio = ((float[]) optics.get("aspect_ratio"))[0];
            } catch(Exception e) { }

            api.parameter("transform", transform);
            api.parameter("fov", fov);
            api.parameter("aspect", aspectRatio);

            if (zfar != null || znear != null) {
              float fdist = 0;
              float lensr = 1;
              if (zfar != null && znear != null) {
                fdist = ( znear + zfar ) / 2f;
                lensr = zfar - znear;
              } else {
                fdist = (zfar == null) ? znear : zfar;
                lensr = 1;
              }

              api.parameter("focus.distance", fdist);
              api.parameter("lens.radius", lensr);
              api.camera(cameraId, "thinlens");
            } else if (fov == 0f) {
              api.camera(cameraId, "ortho");
            } else {
              api.camera(cameraId, "pinhole");
            }

            api.parameter("camera", cameraId);
            api.options(SunflowAPI.DEFAULT_OPTIONS);

        } catch(Exception e) {
            UI.printError(Module.SCENE, "Error loading camera: is there any?");
            UI.printInfo(Module.SCENE, "Using auto-positioned camera instead ... (TODO)");
        }
    }

    private void loadScene() {
        geometryCache  = new FastHashMap<String, FastHashMap<String, Geometry>>();
        shaderCache    = new LinkedList<String>();
        lightCache     = new FastHashMap<String, Integer>();
        transformCache = new FastHashMap<Node, Matrix4>();
        documentCache  = new FastHashMap<String, Document>();

        try {
            Element scene = (Element) xpath.evaluate(getSceneQuery(actualSceneId), dae, XPathConstants.NODE);
            parseNode(scene);

        } catch (XPathExpressionException e) {
            UI.printError(Module.SCENE, "Error loading nodes: are there any?");
        }
    }

    private void parseNode(Element node) {
        Matrix4 transformation = null;
        if ( transformCache.containsKey(node) ) {
            transformation = transformCache.get(node);
        } else {
            transformation = transform(node);
            transformCache.put(node, transformation);
        }

        for (Node childNode = node.getFirstChild(); childNode != null;) {
            Node nextChild = childNode.getNextSibling();

            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) childNode;
                String tagname = child.getTagName();

                // CAMERA INSTANCE (only 1)
                if (tagname.equals("instance_camera") && this.camera == null) {
                    this.camera = child.getAttribute("url").substring(1);
                    setCamera(transformation);

                // GEOMETRY INSTANCE
                } else if (tagname.equals("instance_geometry")) {
                    String url = child.getAttribute("url");
                    String id  = url.substring(1);
                    Document doc = getDocument(child);
                    url = doc.getDocumentURI()+"#"+id;
                    if ( !geometryCache.containsKey(url) ) {
                        geometryCache.put(url, loadGeometry(id, doc));
                    }
                    instantiateGeometry(doc, child, transformation, url);

                // LIGHT INSTANCE
                } else if (tagname.equals("instance_light")) {
                    String id = child.getAttribute("url").substring(1);
                    if ( !lightCache.containsKey(id) ) {
                        lightCache.put(id,0);
                    }
                    instantiateLight(child, transformation, id);

                // NODE INSTANCE
                } else if (tagname.equals("instance_node")) {
                    expandNodeInstance(child);
                // NODE
                } else if (tagname.equals("node")) {
                    parseNode(child);
                }
            }

            childNode = nextChild;
        }
    }

    private void expandNodeInstance(Element nodeInstance) {
        String nodeId = nodeInstance.getAttribute("url");
        Element parent = (Element) nodeInstance.getParentNode();
        UI.printInfo(Module.GEOM, "Expanding node: %s ...", nodeId);

        Element node = getElement(nodeInstance, "node", nodeId);
        if (node != null) {
            Element cnode = (Element) node.cloneNode(true);
            cnode.setAttribute("id", node.getAttribute("id")+"_"+Integer.toString(nodeInstance.hashCode()));
            parent.appendChild(cnode);
        }
    }

    private void instantiateGeometry(Document doc, Element instance, Matrix4 transformation, String geometryId) {
        UI.printInfo(Module.GEOM, "Instantiating mesh: %s ...", geometryId);

        try {
            FastHashMap<String, Geometry> geoms = geometryCache.get(geometryId);
            FastHashMap<String, String> materials = new FastHashMap<String, String>();
            NodeList matInstances = instance.getElementsByTagName("instance_material");

            if ( matInstances.getLength() > 0 ) {
                for (int i=0; i < matInstances.getLength(); i++) {
                    Element imat = (Element) matInstances.item(i);
                    String materialId = imat.getAttribute("target").substring(1);
                    String symbol = imat.getAttribute("symbol");
                    String url = doc.getDocumentURI()+"#"+materialId;
                    materials.put(symbol, url);

                    if ( !shaderCache.contains(url) ) {
                        loadShader(materialId, getDocument(instance), url);
                        shaderCache.add(url);
                    }
                }
            }

            Iterator<FastHashMap.Entry<String, Geometry>> it = geoms.iterator();
            while ( it.hasNext() ) {

                FastHashMap.Entry<String, Geometry> g = it.next();
                String gid = (String) g.getKey();
                Geometry geom = (Geometry) g.getValue();
                Integer ii = geom.instancesCount;
                String material = null;
                if (materials.containsKey(geom.material)) {
                    material = (String) materials.get(geom.material);
                }

                api.parameter("transform", transformation);
                if ( material != null ) {
                    api.parameter("shaders", new String[]{material});
                    api.instance(gid + "." + ii.toString() + ".instance", gid);

                    // instance counter
                    geom.instancesCount = ii+1;
                }
            }
        } catch (Exception e) {
            UI.printError(Module.GEOM, "Error instantiating mesh: %s ...", geometryId);
        }
    }

    private FastHashMap<String, Geometry> loadGeometry(String geometryId, Document doc) {
        String url = doc.getDocumentURI()+"#"+geometryId;
        UI.printInfo(Module.GEOM, "Reading mesh: %s ...", url);
        try {
            NodeList trianglesList = (NodeList) xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/triangles", doc, XPathConstants.NODESET);
            int trianglesNum = trianglesList.getLength();
            FastHashMap<String, Geometry> geoms = new FastHashMap<String, Geometry>();

            // handle multiple <triangles> elements
            for (int i=0; i<trianglesNum; i++) {

                Element trisEl  = (Element) trianglesList.item(i);
                String trisMat  = loadTriangles(geometryId, doc, trisEl);

                String gid = url + "." + Integer.toString(i);
                api.geometry(gid, "triangle_mesh");

                geoms.put(gid, new Geometry(trisMat, 0));

            }

            return geoms;

        } catch(Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading mesh: %s ...", geometryId);
            return null;
        }
    }

    private String loadTriangles(String geometryId, Document doc, Element trisEl) {
        try {
            String trisMat  = trisEl.getAttribute("material");
            NodeList inputs = (NodeList) trisEl.getElementsByTagName("input");
            Integer numInputs = inputs.getLength();
            Integer vOffset = null,
                    nOffset = null,
                    tOffset = null,
                    tCount  = null;

            int normalsType  = FACE;
            float[] vertices = null;
            float[] normals  = null;
            float[] texcoord = null;
            for (int j=0; j<inputs.getLength(); j++) {
                Element in = (Element) inputs.item(j);
                String semantic = in.getAttribute("semantic");
                String sourceId = in.getAttribute("source").substring(1);
                if ( semantic.equals("VERTEX") ) {
                    String vertexDataId = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/vertices/input[@semantic='POSITION']/@source", doc).substring(1);
                    String vertexData = xpath.evaluate(getGeometrySourceQuery(geometryId, vertexDataId), doc);
                    vOffset = Integer.parseInt(in.getAttribute("offset"));
                    vertices = parseFloats(vertexData);
                } else if ( semantic.equals("NORMAL") ) {
                    String normalData = xpath.evaluate(getGeometrySourceQuery(geometryId, sourceId), doc);
                    nOffset = Integer.parseInt(in.getAttribute("offset"));
                    normals = parseFloats(normalData);
                } else if ( semantic.equals("TEXCOORD") ) {
                    String texcoordData = xpath.evaluate(getGeometrySourceQuery(geometryId, sourceId), doc);
                    tOffset = Integer.parseInt(in.getAttribute("offset"));
                    NodeList params = (NodeList) xpath.evaluate(getGeometrySourceParamsQuery(geometryId, sourceId), doc, XPathConstants.NODESET);
                    tCount = params.getLength();
                    texcoord = parseFloats(texcoordData);
                }
            }

            String pointsData = trisEl.getElementsByTagName("p").item(0).getTextContent();
            String[] pointsStrings = pointsData.trim().split("\\s+");
            int num = pointsStrings.length/numInputs;
            int[] trianglesOut = new int[num];
            for (int j=0; j < num; j++) {
                trianglesOut[j] = Integer.parseInt(pointsStrings[j*numInputs+vOffset]);
            }

            float[] normalsFloats = null;
            if (normals != null) {
                normalsFloats = new float[(pointsStrings.length/numInputs)*3];
                int nNum = normalsFloats.length/3;

                for (int j=0; j < nNum; j++) {
                    int nix = Integer.parseInt(pointsStrings[j*numInputs+nOffset]);  // normal index
                    try {
                        normalsFloats[j*3] = (normalsFloats[j*3] + normals[nix*3])/2.0f;
                    } catch (NullPointerException e){
                        normalsFloats[j*3] = normals[nix*3];
                    }
                    try {
                        normalsFloats[j*3+1] = (normalsFloats[j*3+1] + normals[nix*3+1])/2.0f;
                    } catch (NullPointerException e){
                        normalsFloats[j*3+1] = normals[nix*3+1];
                    }
                    try {
                        normalsFloats[j*3+2] = (normalsFloats[j*3+2] + normals[nix*3+2])/2.0f;
                    } catch (NullPointerException e){
                        normalsFloats[j*3+2] = normals[nix*3+2];
                    }
                }
            } else {
                try {
                    String normalDataId = xpath.evaluate(getGeometryQuery(geometryId)+"/mesh/vertices/input[@semantic='NORMAL']/@source", doc).substring(1);
                    normals = parseFloats(xpath.evaluate(getGeometrySourceQuery(geometryId, normalDataId), doc));
                } catch (Exception e) { }
            }

            float[] texcoordFloats = null;
            if (texcoord != null) {
                texcoordFloats = new float[(vertices.length/3)*2];

                for (int j=0; j < num; j++) {
                    int vix = Integer.parseInt(pointsStrings[j*numInputs]);    // vertex index
                    int tix = Integer.parseInt(pointsStrings[j*numInputs+tOffset]);  // texcoord index
                    texcoordFloats[vix*2] = texcoord[tix*tCount];
                    texcoordFloats[vix*2+1] = texcoord[tix*tCount+1];
                }
            }

            api.parameter("triangles", trianglesOut);
            api.parameter("points", "point", "vertex", vertices);
            if (normals != null) {
                if (normalsType == FACE) {
                    api.parameter("normals", "vector", "facevarying", normalsFloats);
                } else if (normalsType == VERTEX) {
                    api.parameter("normals", "vector", "vertex", normals);
                }
            }
            if (texcoord != null && texcoordFloats != null) {
                api.parameter("uvs", "texcoord", "vertex", texcoordFloats);
            }

            return trisMat;

        } catch(Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading mesh: %s ...", geometryId);
            return null;
        }
    }

    private void loadShader(String materialId, Document doc, String url) {
        try {
            String effectId = xpath.evaluate(getMaterialQuery(materialId)+"/instance_effect/@url", doc).substring(1);
            Node technique = (Node) xpath.evaluate(getEffectQuery(effectId)+"/profile_COMMON/technique", doc, XPathConstants.NODE);

            Element extraTechnique = null;
            try {
                extraTechnique = (Element) xpath.evaluate(getEffectQuery(effectId)+"/extra/technique[@sid='sunflow']", doc, XPathConstants.NODE);
            } catch(Exception e) { }

            for (Node childNode = technique.getFirstChild(); childNode != null;) {
                Node nextChild = childNode.getNextSibling();

                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) childNode;
                    String tagname = child.getTagName();
                    FastHashMap<String, Object> shaderParams = getParams(child);

                    ColladaShader s = getShader(tagname,effectId,url,doc);
                    s.create(shaderParams,extraTechnique);
                }

                childNode = nextChild;
            }
        } catch(Exception e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "Error reading material: %s ...", materialId);
        }
    }

    private ColladaShader getShader(String s, String effectId, String url, Document doc) {
        if      (s == "phong")    { return new PhongShader(effectId,url,doc);    }
        else if (s == "lambert")  { return new LambertShader(effectId,url,doc);  }
        else if (s == "constant") { return new ConstantShader(effectId,url,doc); }
        else if (s == "blinn")    { return new LambertShader(effectId,url,doc);  }
        return null;
    }

    class ColladaShader {
        String effectId;
        String url;
        Document doc;

        ColladaShader(String effectId, String url, Document doc) {
            this.effectId = effectId;
            this.url = url;
            this.doc = doc;
        }

        void create(FastHashMap<String, Object> shaderParams, Element extraTechnique) { }

    }

    class PhongShader extends ColladaShader {

        PhongShader(String effectId, String url, Document doc) {
            super(effectId, url, doc);
        }

        void create(FastHashMap<String, Object> shaderParams, Element extraTechnique) {
            Object diffuseObj = shaderParams.get("diffuse");
            String texture = null;
            Color extraColor = null;
            float transparency = 0f;

            try {
                // to get color
                float[] df = (float[]) diffuseObj;
                api.parameter("diffuse", null, new Color(df[0],df[1],df[2]).getRGB());
            } catch (ClassCastException e) {
                // or handle texture
                try {
                    // to get <extra> color
                    extraColor = parseColor(extraTechnique.getElementsByTagName("diffuse").item(0).getTextContent());
                    api.parameter("diffuse", null, extraColor.getRGB());
                } catch(Exception e2) { }

                Element df = (Element) diffuseObj;
                texture = getTexture(effectId, df, doc);
            }

            try {
                float[] spf = (float[]) shaderParams.get("specular");
                api.parameter("specular", null, new Color(spf[0],spf[1],spf[2]).getRGB());
            } catch (Exception e) { }

            try {
                float[] shf = (float[]) shaderParams.get("shininess");
                api.parameter("power", shf[0]);
            } catch (Exception e) { }

            try {
                transparency = ((float[]) shaderParams.get("transparency"))[0];
                api.parameter("transparency", transparency);
            } catch (Exception e) { }

            try {
                Integer samples = Integer.parseInt(extraTechnique.getElementsByTagName("samples").item(0).getTextContent());
                api.parameter("samples", samples);
            } catch(Exception e) {
                api.parameter("samples", 0);
            }

            float rf = 0f;
            try {
                rf = ((float[]) shaderParams.get("reflectivity"))[0];
            } catch (Exception e) { }

            if (rf > 0f) {
                // shiny variant
                api.parameter("shiny", rf);
                if (texture != null) {
                    api.parameter("texture", texture);
                    if (texture.endsWith(".png") && extraColor == null) {
                        api.parameter("alpha_texture", texture);
                        api.shader(url, "alpha_textured_shiny_phong");
                    } else {
                        api.shader(url, "textured_shiny_phong");
                    }
                } else {
                    if (transparency > 0f) {
                        api.shader(url, "transparent_shiny_phong");
                    } else {
                        api.shader(url, "shiny_phong");
                    }
                }
            } else {
                // no reflection (almost)
                if (texture != null) {
                    api.parameter("texture", texture);
                    if (texture.endsWith(".png") && extraColor == null) {
                        api.parameter("alpha_texture", texture);
                        api.shader(url, "alpha_textured_phong");
                    } else {
                        api.shader(url, "textured_phong");
                    }
                } else {
                    if (transparency > 0f) {
                        api.shader(url, "alpha_phong");
                    } else {
                        api.shader(url, "phong");
                    }
                }
            }
        }
    }

    class LambertShader extends ColladaShader {

        LambertShader(String effectId, String url, Document doc) {
          super(effectId,url,doc);
        }

        void create(FastHashMap<String, Object> shaderParams, Element extraTechnique) {
            Object diffuseObj = shaderParams.get("diffuse");
            Color extraColor = null;

            try {
                // try to get color
                float[] df = (float[]) diffuseObj;
                api.parameter("diffuse", null, new Color(df[0],df[1],df[2]).getRGB());
                api.shader(url, "diffuse");
            } catch (Exception e) {
                // handle texture
                try {
                    // to get <extra> color
                    extraColor = parseColor(extraTechnique.getElementsByTagName("diffuse").item(0).getTextContent());
                    api.parameter("diffuse", null, extraColor.getRGB());
                } catch(Exception e2) { }

                Element df = (Element) diffuseObj;
                String texture = getTexture(effectId, df, doc);
                api.parameter("texture", texture);
                if (texture.endsWith(".png") && extraColor == null) {
                    api.parameter("alpha_texture", texture);
                    api.shader(url, "alpha_textured_diffuse");
                } else {
                    api.shader(url, "textured_diffuse");
                }
            }
        }
    }

    class ConstantShader extends ColladaShader {

        ConstantShader(String effectId, String url, Document doc) {
          super(effectId,url,doc);
        }

        void create(FastHashMap<String, Object> shaderParams, Element extraTechnique) {
            Object diffuseObj = shaderParams.get("diffuse");
            try {
                // try to get color
                float[] df = (float[]) diffuseObj;
                api.parameter("color", null, new Color(df[0],df[1],df[2]).getRGB());
                api.shader(url, "constant");
            } catch (Exception e) {
                // handle texture
                Element df = (Element) diffuseObj;
                String texture = getTexture(effectId, df, doc);
                if (texture.endsWith(".png")) {
                    api.parameter("alpha_texture", texture);
                }
                api.parameter("texture", texture);
                api.shader(url, "textured_constant");
            }
        }
    }

    private void instantiateLight(Element lightInstance, Matrix4 transformation, String lightId) {
        UI.printInfo(Module.LIGHT, "Reading light: %s ...", lightId);
        try {
            Document doc = getDocument(lightInstance);
            Element light = (Element) xpath.evaluate(getLightQuery(lightId)+"/technique_common", doc, XPathConstants.NODE);
            for (Node childNode = light.getFirstChild(); childNode != null;) {
                Node nextChild = childNode.getNextSibling();

                if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) childNode;
                    String tagname = child.getTagName();
                    Integer ii = (Integer) lightCache.get(lightId);

                    // COLLADA directional lights are infinite
                    if (tagname.equals("directional")) {
                        api.parameter("source", new Point3(0.0f,0.0f,10000.0f));
                        Vector3 dir = transformation.transformV( new Vector3(0.0f,0.0f,-1.0f));
                        api.parameter("dir", dir);
                        api.parameter("radius", 10000.0f);

                        api.light(lightId+"."+Integer.toString(ii), "directional");

                    // POINT LIGHT
                    } else if (tagname.equals("point")) {
                        FastHashMap<String, Object> params = getParams(child);
                        Color power = null;
                        try {
                            float[] cf = (float[]) params.get("color");
                            power = new Color(cf[0],cf[1],cf[2]);
                        } catch (Exception e) {
                            power = new Color(1.0f,1.0f,1.0f);
                        }
                        try {
                            power.mul( ((float[]) params.get("constant_attenuation"))[0] );
                        } catch (Exception e) { }

                        api.parameter("center", transformation.transformP(new Point3(0.0f,0.0f,0.0f)));
                        api.parameter("power", null, power.getRGB());
                        api.light(lightId+"."+Integer.toString(ii), "point");

                    // IMAGE BASED LIGHT
                    } else if (tagname.equals("ambient")) {
                        try {
                            NodeList techniques = ((Element) child.getParentNode().getParentNode()).getElementsByTagName("technique");
                            int l = techniques.getLength();
                            for (int i=0; i<l; i++) {
                                Element t = (Element) techniques.item(i);
                                if (t.getAttribute("profile").equals("sunflow")) {
                                    FastHashMap<String, Object> params = getParams((Element) t.getElementsByTagName("ibl").item(0));

                                    String imgid = t.getElementsByTagName("init_from").item(0).getTextContent();
                                    String path = xpath.evaluate(getImageQuery(imgid.trim()), doc).trim();
                                    api.parameter("texture", api.resolveIncludeFilename(path));

                                    float[] center = (float[]) params.get("center");
                                    api.parameter("center", new Vector3(center[0],center[1],center[2]));

                                    try {
                                        float[] up = (float[]) params.get("up");
                                        api.parameter("up", new Vector3(up[0],up[1],up[2]));
                                    } catch(Exception e) {
                                        api.parameter("up", new Vector3(0f,0f,1f));
                                    }

                                    try {
                                        String fixed = ((Element) params.get("fixed")).getTextContent().trim();
                                        api.parameter("fixed", Boolean.valueOf(fixed).booleanValue());
                                    } catch(Exception e) { }

                                    try {
                                        int samples = (int) ((float[]) params.get("samples"))[0];
                                        api.parameter("samples", samples);
                                    } catch(Exception e) { }

                                    api.light(lightId+"."+Integer.toString(ii), "ibl");
                                    break;
                                }
                            }
                        } catch(Exception e) { }
                    }
                    ii++;
                    lightCache.put(lightId, ii);
                }

                childNode = nextChild;
            }
        } catch (Exception e) {
            UI.printError(Module.LIGHT, "Error reading light: %s ...", lightId);
        }
    }

    private Element getElement(Element source, String name, String id) {
        try {
            if (id.startsWith("#")) {
                // Local resource
                Document doc = getDocument(source);
                Element result = (Element) xpath.evaluate(String.format("//%s[@id='%s']", name, id.substring(1)), doc, XPathConstants.NODE);
                return (Element) dae.importNode(result, true);
            } else {
                // External resource
                UI.printInfo(Module.GEOM, "Loading external resource: %s", id);
                Element result = dae.createElement("node");

                String location = null;
                String foreignId = null;
                if (id.contains("#")) {
                    String[] ids = id.split("#");
                    location = api.resolveIncludeFilename(ids[0]);
                    foreignId = ids[1];
                } else {
                    location = api.resolveIncludeFilename(id);
                }
                result.setAttribute("doc", location);

                // Parse or retrieve document from cache
                Document doc = null;
                if (documentCache.containsKey(location)) {
                    doc = documentCache.get(location);
                } else {
                    doc = parser.parse(location);
                    documentCache.put(location, doc);
                }

                if (foreignId != null) {
                    // import resource
                    Node docNode = (Node) xpath.evaluate(String.format("//%s[@id='%s']", name, foreignId), doc, XPathConstants.NODE);
                    Node adoptedNode = dae.importNode(docNode, true);
                    result.appendChild(adoptedNode);
                } else {
                    // import main scene as node
                    String sceneId = getSceneId(doc);

                    Node scene = (Node) xpath.evaluate(getSceneQuery(sceneId), doc, XPathConstants.NODE);
                    for (Node childNode = scene.getFirstChild(); childNode!=null;) {
                        Node nextChild = childNode.getNextSibling();

                        if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                            Node adoptedNode = dae.importNode(childNode, true);
                            result.appendChild(adoptedNode);
                        }

                        childNode = nextChild;
                    }
                }
                return result;
            }
        } catch(FileNotFoundException e) {
            UI.printError(Module.API, "External resource not found: %s ...", id);
            return null;
        } catch(Exception e) {
            UI.printError(Module.API, "Error loading external resource: %s ...", id);
            return null;
        }
    }

    private Document getDocument(Node subject) {
        Document result = dae;
        for(Node n = subject; n != null; n = n.getParentNode()) {
            try {
                String loc = ((Element) n).getAttribute("doc");
                if ( !loc.equals("") ) {
                    result = (Document) documentCache.get(loc);
                    return result;
                }
            } catch(Exception e) { }
        }
        return result;
    }

    private Matrix4 transform(Element node) {
        Node parentNode = node.getParentNode();
        Matrix4 parent = null;
        if ( transformCache.containsKey(parentNode) ) {
            parent = (Matrix4) transformCache.get(parentNode);
        } else {
            // identity matrix if it's root
            if ( ! ((Element) parentNode).getTagName().equals("node") ) {
                parent = Matrix4.IDENTITY;
            // calculate parent
            } else {
                parent = transform( (Element) parentNode );
                transformCache.put(parentNode, parent);
            }
        }
        Matrix4 result = Matrix4.IDENTITY;
        result = result.multiply(parent);

        LinkedList<Matrix4> nodeTransforms = new LinkedList<Matrix4>();
        for (Node childNode = node.getFirstChild(); childNode!=null;) {

            Node nextChild = childNode.getNextSibling();
            if (childNode.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) childNode;
                String tagname = child.getTagName();

                if (tagname.equals("scale")) {
                    Vector3 scale = parseVector(child.getTextContent());
                    nodeTransforms.add( Matrix4.scale(scale.x,scale.y,scale.z) );

                } else if (tagname.equals("translate")) {
                    Vector3 translation = parseVector(child.getTextContent());
                    nodeTransforms.add( Matrix4.translation(translation.x,translation.y,translation.z) );

                } else if(tagname.equals("rotate")) {
                    float[] floats = parseFloats(child.getTextContent());
                    if (floats[0] == 1.0) {
                        nodeTransforms.add( Matrix4.rotateX((float) Math.toRadians(floats[3])) );
                    } else if (floats[1] == 1.0) {
                        nodeTransforms.add( Matrix4.rotateY((float) Math.toRadians(floats[3])) );
                    } else if (floats[2] == 1.0) {
                        nodeTransforms.add( Matrix4.rotateZ((float) Math.toRadians(floats[3])) );
                    }

                } else if(tagname.equals("lookat")) {
                    float[] floats = parseFloats(child.getTextContent());
                    Point3 eye     = new Point3(floats[0],floats[1],floats[2]);
                    Point3 target  = new Point3(floats[3],floats[4],floats[5]);
                    Vector3 up     = new Vector3(floats[6],floats[7],floats[8]);
                    nodeTransforms.add( Matrix4.lookAt(eye, target, up) );
                } else if(tagname.equals("matrix")) {
                    float[] floats = parseFloats(child.getTextContent());
                    nodeTransforms.add( new Matrix4(floats, true) );
                }
            }
            childNode = nextChild;
        }

        for (Matrix4 t: nodeTransforms)
            result = result.multiply(t);

        return result;
    }

    private FastHashMap<String, Object> getParams(Element el) {
        FastHashMap<String, Object> result = new FastHashMap<String, Object>();
        for (Node childNode = el.getFirstChild(); childNode != null;) {
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

    private String getTexture(String effectId, Element param, Document doc) {
        try {
            String baseURI = new URI(doc.getDocumentURI().replaceFirst("/[^/]+$","/")).getPath();

            String texture = ((Element) param.getElementsByTagName("texture").item(0)).getAttribute("texture");
            String surface = xpath.evaluate(getEffectQuery(effectId)+String.format("/profile_COMMON/newparam[@sid='%s']/sampler2D/source/text()", texture), doc);
            String image   = xpath.evaluate(getEffectQuery(effectId)+String.format("/profile_COMMON/newparam[@sid='%s']/surface[@type='2D']/init_from/text()", surface), doc);

            String path = xpath.evaluate(getImageQuery(image), doc).trim();
            String fullPath = baseURI + path;
            if (new File(path).exists()) {
              return path;
            } else {
              if (new File(fullPath).exists()) {
                return fullPath;
              } else {
                UI.printError(Module.GEOM, "Texture file not found: %s ...", path);
                return null;
              }
            }
        } catch (Exception e) {
            UI.printError(Module.GEOM, "Error reading texture: for effect %s ...", effectId);
            return null;
        }
    }

    /*
     *  XPath queries
     *
     **/

    private String getSceneId(Document doc) throws XPathExpressionException {
        return xpath.evaluate("/COLLADA/scene/instance_visual_scene/@url", doc).substring(1);
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

    private String getGeometrySourceParamsQuery(String geometryId, String inputId) {
        return "/COLLADA/library_geometries/geometry[@id='"+geometryId+"']/mesh/source[@id='"+inputId+"']/technique_common/accessor/param";
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

    private String getLightQuery(String lightId) {
        return String.format("/COLLADA/library_lights/light[@id='%s']", lightId);
    }

    // ---

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
