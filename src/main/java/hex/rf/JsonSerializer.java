package hex.rf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hex.rf.Tree.ExclusionNode;
import hex.rf.Tree.LeafNode;
import hex.rf.Tree.SplitNode;
import java.io.IOException;
import java.util.HashMap;
import water.AutoBuffer;
import water.ValueArray.Column;

/**
 *
 */
public class JsonSerializer extends TreePrinter
{
    JsonObject root;

    public JsonSerializer(JsonObject root, Column[] columns, int[] colMapping, String[]classNames) {
        super(columns, colMapping, classNames);
        this.root = root;
    }

    @Override
    public void printTree(Tree t) throws IOException
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    void printNode(LeafNode t) throws IOException
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    void printNode(SplitNode t) throws IOException
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    void printNode(ExclusionNode t) throws IOException
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void walk_serialized_tree(AutoBuffer tbits)
    {
        try {
            final HashMap<Integer, JsonObject> nodes = new HashMap<Integer, JsonObject>();
            final HashMap<Integer, Integer> relations = new HashMap<Integer, Integer>();
            new Tree.TreeVisitor<IOException>(tbits)
            {
                protected Tree.TreeVisitor leaf(int tclass) throws IOException
                {
                    String label = _classNames != null && tclass < _classNames.length
                        ? _classNames[tclass]
                        : String.format("Class %d", tclass);
                    JsonObject node = new JsonObject();
                    node.addProperty("label", label);
                    nodes.put(_ts.position() - 2, node);

                    return this;
                }

                protected Tree.TreeVisitor pre(int col, float fcmp, int off0, int offl, int offr) throws IOException
                {
                    byte b = (byte)_ts.get1(off0);
                    JsonObject node = new JsonObject();
                    node.addProperty("field", _cols[col]._name);
                    node.addProperty("condition", ((b == 'E') ? "==" : "<="));
                    node.addProperty("value", fcmp);
                    node.add("children", new JsonArray());
                    nodes.put(off0, node);
                    relations.put(offl, off0);
                    relations.put(offr, off0);

                    if (!root.has("value")) {
                        root.add("value", node);
                    }
                    return this;
                }
            }.visit();
            for (Integer child : relations.keySet()) {
                Integer parent = relations.get(child);
                ((JsonArray)(nodes.get(parent).get("children"))).add(nodes.get(child));
            }
        }
        catch (IOException e) {
            throw new Error(e);
        }
    }
}
