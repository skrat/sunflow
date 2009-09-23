package org.sunflow.core.tesselatable;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;

import org.sunflow.SunflowAPI;
import org.sunflow.core.PrimitiveList;
import org.sunflow.core.ParameterList;
import org.sunflow.core.Tesselatable;
import org.sunflow.core.ParameterList.InterpolationType;
import org.sunflow.core.primitive.TriangleMesh;
import org.sunflow.math.BoundingBox;
import org.sunflow.math.Matrix4;
import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;
import org.sunflow.util.ColladaDocument;
import org.sunflow.io.Resources;

public class ColladaGeometry implements Tesselatable {
    private String filename = null;
    private String xmlId = null;
    private boolean smoothNormals = false;

    public BoundingBox getWorldBounds(Matrix4 o2w) {
        // world bounds can't be computed without reading file
        // return null so the mesh will be loaded right away
        return null;
    }

    public PrimitiveList tesselate() {
        TriangleMesh m = new TriangleMesh();
        ParameterList pl = new ParameterList();
        addParams(pl);

        if (m.update(pl, null))
            return m;
        return null;
    }

    public void addParams(ParameterList pl) {
        Resources r = Resources.getInstance();
        Document dae = null;

        if ( r.contains( (Object) filename) ) {
            UI.printInfo(Module.GEOM, "COLLADA - Cached resource: %s ...", filename+"#"+xmlId);
            dae = (Document) r.get( (Object) filename);
        } else {
            UI.printInfo(Module.GEOM, "COLLADA - Reading geometry: %s ...", filename+"#"+xmlId);
            try {
                DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                dae = parser.parse(new File(filename));
                r.store( (Object) filename, dae);
            } catch(ParserConfigurationException e) {
                e.printStackTrace();
                UI.printError(Module.GEOM, "COLLADA - Unable to read mesh file \"%s\" - parser error", filename);
            } catch(SAXException e) {
                e.printStackTrace();
                UI.printError(Module.GEOM, "COLLADA - Unable to read mesh file \"%s\" - parser error", filename);
            } catch(IOException e) {
                e.printStackTrace();
                UI.printError(Module.GEOM, "COLLADA - Unable to read mesh file \"%s\" - IO error", filename);
            }
        }

        if ( dae == null ) {
            // throw...
        }

        float[] verts = ColladaDocument.getGeometryPoints(dae, xmlId).trim();
        int[] tris = ColladaDocument.getGeometryTriangles(dae, xmlId).trim();
        
        pl.addPoints("points", InterpolationType.VERTEX, verts);
        pl.addIntegerArray("triangles", tris);
    }

    public boolean update(ParameterList pl, SunflowAPI api) {
        String file = pl.getString("filename", null);
        if (file != null)
            filename = api.resolveIncludeFilename(file);
        smoothNormals = pl.getBoolean("smooth_normals", smoothNormals);
        xmlId = pl.getString("id", null);

        return filename != null && xmlId != null;
    }
}
