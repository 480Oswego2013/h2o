package water.api;

import hex.rf.RFModel;
import hex.rf.Tree;
import water.AutoBuffer;
import water.ValueArray;
import water.util.RString;
import water.util.TreeRenderer;

import com.google.gson.JsonObject;


public class RFTreeView extends Request {
  protected final RFModelKey _modelKey = new RFModelKey(MODEL_KEY);
  protected final Int _tree = new Int(TREE_NUM, 0);
  protected final H2OHexKey _dataKey = new H2OHexKey(DATA_KEY);
  protected final H2OHexKeyCol _classCol = new H2OHexKeyCol(CLASS,_dataKey,0);

  public static String link(RFModel model, int tree, ValueArray va, int clz, String body) {
    RString rs = new RString("<a href='/RFTreeView.html?" +
        "%modelKey=%$modelVal" +
        "&%treeKey=%treeVal" +
        "&%vaKey=%$vaVal" +
        "&%clzKey=%clzVal'>%body</a>");
    rs.replace("modelKey", MODEL_KEY);
    rs.replace("modelVal", model._selfKey.toString());
    rs.replace("treeKey", TREE_NUM);
    rs.replace("treeVal", tree);
    rs.replace("vaKey", DATA_KEY);
    rs.replace("vaVal", va._key.toString());
    rs.replace("clzKey", CLASS);
    rs.replace("clzVal", clz);
    rs.replace("body", body);
    return rs.toString();
  }

  @Override protected Response serve() {
    JsonObject res = new JsonObject();
    RFModel model = _modelKey.value();
    int tree = _tree.value();
    ValueArray va = _dataKey.value();
    int classCol = _classCol.value();

    byte[] tbits = model.tree(tree);
    long dl = Tree.depth_leaves(new AutoBuffer(tbits));
    int depth = (int)(dl>>>32);
    int leaves= (int)(dl&0xFFFFFFFFL);

    TreeRenderer renderer = new TreeRenderer(model, tree, va, classCol);
    String graph = renderer.graphviz();
    String code = renderer.code();

    res.addProperty(TREE_DEPTH, depth);
    res.addProperty(TREE_LEAVES, leaves);
    res.add("tree", renderer.json());

    Response r = Response.done(res);
    r.addHeader("<h2>Tree " + tree + "</h2>" + graph + code);
    return r;
  }
}
