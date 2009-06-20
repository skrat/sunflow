package org.sunflow.core.tesselatable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

import org.sunflow.SunflowAPI;
import org.sunflow.core.ParameterList;
import org.sunflow.core.PrimitiveList;
import org.sunflow.core.Tesselatable;
import org.sunflow.core.ParameterList.InterpolationType;
import org.sunflow.core.primitive.TriangleMesh;
import org.sunflow.math.BoundingBox;
import org.sunflow.math.Matrix4;
import org.sunflow.math.Point3;
import org.sunflow.math.Vector3;
import org.sunflow.system.Memory;
import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;
import org.sunflow.util.FloatArray;
import org.sunflow.util.IntArray;
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
        UI.printInfo(Module.GEOM, "COLLADA - Reading geometry: \"%s\" ...", filename);
        Resources r = Resources.getInstance();
        Document doc = null;

        if ( r.contains( (Object) filename) ) {
            UI.printInfo(Module.GEOM, "COLLADA - Cached resource: \"%s\" ...", filename);
            doc = (Document) r.get( (Object) filename);
        } else {
            try {
                DocumentBuilder b = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                doc = b.parse(filename);
            } catch(javax.xml.parsers.ParserConfigurationException e) {
                e.printStackTrace();
                UI.printError(Module.GEOM, "Unable to read mesh file \"%s\" - parser error", filename);
            } catch(org.xml.sax.SAXException e) {
                e.printStackTrace();
                UI.printError(Module.GEOM, "Unable to read mesh file \"%s\" - parser error", filename);
            } catch(IOException e) {
                e.printStackTrace();
                UI.printError(Module.GEOM, "Unable to read mesh file \"%s\" - file error", filename);
            }

            r.store( (Object) filename, doc);
        }

        if ( doc == null ) {
            return null;
        }

        FloatArray verts = ColladaDocument.getGeometryPoints(doc, xmlId);
        IntArray tris = ColladaDocument.getGeometryTriangles(doc, xmlId);

        TriangleMesh m = new TriangleMesh();
        ParameterList pl = new ParameterList();

        pl.addIntegerArray("triangles", tris.trim());
        pl.addPoints("points", InterpolationType.VERTEX, verts.trim());

        if (m.update(pl, null))
            return m;
        return null;
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
