
package water.api;

import java.io.File;

import com.google.gson.*;

public abstract class TypeaheadRequest extends Request {
  protected final Str _filter = new Str(FILTER,"");
  protected final Int _limit = new Int(LIMIT,1024,0,10240);

  public TypeaheadRequest(String help) {
    _requestHelp = help;
    _filter._requestHelp = "Only items matching this filter will be returned.";
    _limit._requestHelp = "Max number of items to be returned.";
  }

  @Override final protected Response serve() {
    JsonArray array = serve(_filter.value(), _limit.value());
    JsonObject response = new JsonObject();
    response.add(ITEMS, array);
    return Response.done(response);
  }

  abstract protected JsonArray serve(String filter, int limit);
}
