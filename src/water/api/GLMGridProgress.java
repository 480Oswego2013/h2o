package water.api;

import hex.GLMSolver.GLMModel;
import hex.LSMSolver;

import java.util.Map;

import water.Key;
import water.Value;

import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class GLMGridProgress extends Request {
  protected final H2OExistingKey _taskey = new H2OExistingKey(DEST_KEY);

  public static Response redirect(JsonObject resp, Key taskey) {
    JsonObject redir = new JsonObject();
    redir.addProperty(DEST_KEY, taskey.toString());
    return Response.redirect(resp, GLMGridProgress.class, redir);
  }

  @Override protected Response serve() {
    Value v = _taskey.value();
    GLMGridStatus status = v.get(new GLMGridStatus());

    JsonObject response = new JsonObject();
    response.addProperty(Constants.DEST_KEY, v._key.toString());

    JsonArray models = new JsonArray();
    for( GLMModel m : status.computedModels() ) {
      JsonObject o = new JsonObject();
      LSMSolver lsm = m._solver;
      o.addProperty(KEY, m.key().toString());
      o.addProperty(LAMBDA_1, lsm._lambda);
      o.addProperty(LAMBDA_2, lsm._lambda2);
      o.addProperty(RHO, lsm._rho);
      o.addProperty(ALPHA, lsm._alpha);
      o.addProperty(BEST_THRESHOLD, m._vals[0].bestThreshold());
      o.addProperty(AUC, m._vals[0].AUC());
      double[] classErr = m._vals[0].classError();
      for( int j = 0; j < classErr.length; ++j ) {
        o.addProperty(ERROR +"_"+ j, classErr[j]);
      }
      models.add(o);
    }
    response.add(MODELS, models);

    Response r = status._working
      ? Response.poll(response,status.progress())
      : Response.done(response);

    r.setBuilder(Constants.DEST_KEY, new HideBuilder());
    r.setBuilder(MODELS, new GridBuilder2());
    r.setBuilder(MODELS+"."+KEY, new KeyCellBuilder());
    return r;
  }

  private static class GridBuilder2 extends ArrayBuilder {
    private final Map<String, String> _m = Maps.newHashMap(); {
      _m.put(KEY, "Model");
      _m.put(LAMBDA_1, "&lambda;<sub>1</sub>");
      _m.put(LAMBDA_2, "&lambda;<sub>2</sub>");
      _m.put(RHO, "&rho;");
      _m.put(ALPHA, "&alpha;");
    }
    @Override
    public String header(String key) {
      return Objects.firstNonNull(_m.get(key), super.header(key));
    }
  }
}
