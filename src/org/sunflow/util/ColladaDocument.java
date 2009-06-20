package org.sunflow.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import org.sunflow.util.FloatArray;
import org.sunflow.util.IntArray;

public class ColladaDocument {
    public static FloatArray getGeometryPoints(Document doc, String id) {
        Element geometry = doc.getElementById(id);
        Element triangles = (Element) geometry.getElementsByTagName("triangles").item(0);
        Element verticesInput = (Element) triangles.getElementsByTagName("input").item(0);
        return null;
    }

    public static IntArray getGeometryTriangles(Document doc, String id) {
        return null;
    }
}
