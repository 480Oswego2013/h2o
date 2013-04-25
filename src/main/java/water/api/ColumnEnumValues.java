package water.api;

import hex.rf.RFModel;
import hex.rf.Tree;
import water.AutoBuffer;
import water.ValueArray;
import water.util.RString;
import water.util.TreeRenderer;

import com.google.gson.*;


/**
 * Returns the "string" values for a classification column, including enum column values.
 */
public class ColumnEnumValues extends Request {
  protected final H2OHexKey _dataKey = new H2OHexKey(DATA_KEY);
  protected final H2OHexKeyCol _classCol = new H2OHexKeyCol(CLASS,_dataKey,0);

  @Override protected Response serve() {
    JsonObject res = new JsonObject();
    ValueArray va = _dataKey.value();
    int classCol = _classCol.value();

    H2OCategoryWeights foo = new H2OCategoryWeights(WEIGHTS, _dataKey, _classCol, 1);

    String [] classNames = foo.determineColumnClassNames(1024);

    JsonArray array = new JsonArray();
    for(int i = 0; i < classNames.length; i++){
        array.add(new JsonPrimitive(classNames[i]));
    }


    res.add("enum_values", array);

    Response r = Response.done(res);
    return r;
  }
}

