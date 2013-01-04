package water.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.regex.Pattern;
import water.H2O;

/** All statics for the Request api.
 *
 * Especially the JSON property names should be defined here. Some helper
 * functions too.
 *
 * @author peta
 */
public class RequestStatics extends Constants {
  public String requestName() {
    return getClass().getSimpleName();
  }


  /** Request type.
   *
   * Requests can have multiple types. Basic types include the plain json type
   * in which the result is returned as a JSON object, a html type that acts as
   * the webpage, or the help type that displays the extended help for the
   * request.
   *
   * The wiki type is also added that displays the markup of the wiki that
   * should be used to document the request as per Matt's suggestion.
   *
   * NOTE the requests are distinguished by their suffixes. Please make the
   * suffix start with the dot character to avoid any problems with request
   * names.
   */
  public static enum RequestType {
    json(".json"), ///< json type request, a result is a JSON structure
    www(".html"), ///< webpage request
    help(".help"), ///< should display the help on the given request
    query(".query"), ///< Displays the query for the argument in html mode
    debug(".debug"), ///< Displays the webpage without query
    ;
    /** Suffix of the request - extension of the URL.
     */
    public final String _suffix;

    RequestType(String suffix) {
      _suffix = suffix;
    }

    /** Returns the request type of a given URL. JSON request type is the default
     * type when the extension from the URL cannot be determined.
     */
    public static RequestType requestType(String requestUrl) {
      if (requestUrl.endsWith(www._suffix))
        return www;
      if (requestUrl.endsWith(help._suffix))
        return help;
      if (requestUrl.endsWith(query._suffix))
        return query;
      if (requestUrl.endsWith(debug._suffix))
        return debug;
      return json;
    }

    /** Returns the name of the request, that is the request url without the
     * request suffix.
     */
    public String requestName(String requestUrl) {
      String result = (requestUrl.endsWith(_suffix)) ? requestUrl.substring(0, requestUrl.length()-_suffix.length()) : requestUrl;
      if (result.charAt(0) == '/')
        return result.substring(1);
      return result;
    }
  }

  /** Returns the name of the JSON property pretty printed. That is spaces
   * instead of underscores and capital first letter.
   * @param name
   * @return
   */
  public static String JSON2HTML(String name) {
    return name.substring(0,1).toUpperCase()+name.replace("_"," ").substring(1);
  }


  private static Pattern _correctJsonName = Pattern.compile("^[_a-z][_a-z0-9]*$");

  /** Checks if the given JSON name is valid. A valid JSON name is a sequence of
   * small letters, numbers and underscores that does not start with number.
   */
  public static boolean checkJsonName(String name) {
    return _correctJsonName.matcher(name).find();
  }

  protected static JsonObject jsonError(String error) {
    JsonObject result = new JsonObject();
    result.addProperty(ERROR, error);
    return result;
  }

  protected static String encodeRedirectArgs(JsonObject args) {
    if (args == null)
      return "";
    StringBuilder sb = new StringBuilder();
    sb.append("?");
    for (Map.Entry<String,JsonElement> entry : args.entrySet()) {
      JsonElement e = entry.getValue();
      if (sb.length()!=1)
        sb.append("&");
      sb.append(entry.getKey());
      sb.append("=");
      try {
        sb.append(URLEncoder.encode(e.getAsString(),"UTF-8"));
      } catch (UnsupportedEncodingException ex) {
        assert (false): ex.toString();
      }
    }
    return sb.toString();
  }


}
