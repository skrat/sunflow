package org.sunflow.util;

import javax.xml.xpath.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.sunflow.system.UI;
import org.sunflow.system.UI.Module;
import org.sunflow.util.FloatArray;
import org.sunflow.util.IntArray;

public class ColladaDocument {
    public static FloatArray getGeometryPoints(Document dae, String id) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            Element trianglesInput   = (Element) xpath.evaluate( "/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/triangles/input", dae, XPathConstants.NODE);
            Element verticesInput    = (Element) xpath.evaluate( "/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/vertices[@id='"+trianglesInput.getAttribute("source").substring(1)+"']/input", dae, XPathConstants.NODE);

            String verticesStr = xpath.evaluate( ("/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/source[@id='"+verticesInput.getAttribute("source").substring(1)+"']/float_array/text()"), dae);
            String[] vs = verticesStr.trim().split("\\s+");

            FloatArray verts = new FloatArray();
            for (int i=0; i<vs.length; i++) {
                verts.add(Float.parseFloat(vs[i]));
            }
            return verts;
        } catch(XPathExpressionException e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "COLLADA - Unable to parse mesh - \"%s\"", id);
            return null;
        }
    }

    public static IntArray getGeometryTriangles(Document dae, String id) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        try {
            String trianglesStr = xpath.evaluate( "/COLLADA/library_geometries/geometry[@id='"+id+"']/mesh/triangles/p/text()", dae);
            String[] ts = trianglesStr.trim().split("\\s+");

            IntArray tris = new IntArray();
            for (int i=0; i<ts.length; i++) {
                tris.add(Integer.parseInt( ts[i]) );
            }
            return tris;
        } catch(XPathExpressionException e) {
            e.printStackTrace();
            UI.printError(Module.GEOM, "COLLADA - Unable to parse mesh - \"%s\"", id);
            return null;
        }
    }
}
