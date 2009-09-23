package org.sunflow.util;

import javax.xml.xpath.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;
import org.sunflow.util.FloatArray;
import org.sunflow.util.IntArray;

public class ColladaDocument {

    private static XPath xpath = XPathFactory.newInstance().newXPath();

    public static FloatArray getGeometryPoints(Document dae, String id) {
        try {
            Element trianglesInput = (Element) xpath.evaluate( "/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/triangles/input", dae, XPathConstants.NODE);
            Element verticesInput = (Element) xpath.evaluate( "/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/vertices[@id='"+trianglesInput.getAttribute("source").substring(1)+"']/input", dae, XPathConstants.NODE);
            String verticesStr = xpath.evaluate( ("/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/source[@id='"+verticesInput.getAttribute("source").substring(1)+"']/float_array/text()"), dae);

            String[] vs = verticesStr.trim().split("\\s+");
            FloatArray verts = new FloatArray();

            for (int j=0; j<vs.length; j++) {
                verts.add(Float.parseFloat(vs[j]));
            }

            return verts;
        } catch(XPathExpressionException e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "COLLADA - Unable to parse mesh - \"%s\"", id);
            return null;
        }
    }

    public static IntArray getGeometryTriangles(Document dae, String id) {
        try {
            String triangles = xpath.evaluate( "/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/triangles/p/text()", dae);
            String[] ts = triangles.trim().split("\\s+");
            IntArray tris = new IntArray();

            for (int j=0; j<ts.length; j++) {
                tris.add(Integer.parseInt( ts[j]) );
            }

            return tris;
        } catch(XPathExpressionException e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "COLLADA - Unable to parse mesh - \"%s\"", id);
            return null;
        }
    }
}
