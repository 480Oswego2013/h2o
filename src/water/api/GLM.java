package water.api;

import hex.*;
import hex.GLMSolver.CaseMode;
import hex.GLMSolver.Family;
import hex.GLMSolver.GLMException;
import hex.GLMSolver.GLMModel;
import hex.GLMSolver.GLMParams;
import hex.GLMSolver.GLMValidation;
import hex.GLMSolver.Link;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;

import water.*;
import water.web.RString;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class GLM extends Request {

  protected final H2OHexKey _key = new H2OHexKey(KEY);
  protected final H2OHexKeyCol _y = new H2OHexKeyCol(Y, _key);
  protected final HexColumnSelect _x = new HexNonConstantColumnSelect(X, _key, _y);
  protected final H2OGLMModelKey _modelKey = new H2OGLMModelKey(MODEL_KEY,false);
  protected final EnumArgument<Family> _family = new EnumArgument(FAMILY,Family.gaussian,true);
  protected final LinkArg _link = new LinkArg(_family,LINK);
  protected final Real _lambda = new Real(LAMBDA, LSMSolver.DEFAULT_LAMBDA); // TODO I do not know the bounds
  protected final Real _alpha = new Real(ALPHA, LSMSolver.DEFAULT_ALPHA, 0, 1);
  protected final Real _betaEps = new Real(BETA_EPS,GLMSolver.DEFAULT_BETA_EPS);
  protected final Int _maxIter = new Int(MAX_ITER, GLMSolver.DEFAULT_MAX_ITER, 1, 1000000);
  protected final Real _caseWeight = new Real(WEIGHT,1.0);
  protected final CaseModeSelect _caseMode = new CaseModeSelect(_key,_y,_family, CASE_MODE,CaseMode.none);
  protected final CaseSelect _case = new CaseSelect(_key,_y,_caseMode,CASE);
  protected final RSeq _thresholds = new RSeq(DTHRESHOLDS, false, new NumberSequence("0:1:0.01", false, 0.01),false);
  protected final Int _xval = new Int(XVAL, 10, 0, 1000000);

  public GLM() {
    _modelKey._hideInQuery = true;
    _requestHelp = "Compute a generalized linear model.";
  }


  public static String link(Key k, String content) {
    RString rs = new RString("<a href='GLM.query?%key_param=%$key'>%content</a>");
    rs.replace("key_param", KEY);
    rs.replace("key", k.toString());
    rs.replace("content", content);
    return rs.toString();
  }

  public static String link(Key k, GLMModel m, String content) {
    try {
      StringBuilder sb = new StringBuilder("<a href='GLM.query?");
      sb.append(KEY + "=" + k.toString());
      sb.append("&y=" + m.ycolName());
      sb.append("&x=" + URLEncoder.encode(m.xcolNames(),"utf8"));
      sb.append("&family=" + m._glmParams._f.toString());
      sb.append("&link=" + m._glmParams._l.toString());
      sb.append("&lambda=" + m._solver._lambda);
      sb.append("&alpha=" + m._solver._alpha);
      sb.append("&beta_eps=" + m._glmParams._betaEps);
      sb.append("&weight=" + m._glmParams._caseWeight);
      sb.append("&max_iter=" + m._glmParams._maxIter);
      sb.append("&caseMode=" + URLEncoder.encode(m._glmParams._caseMode.toString(),"utf8"));
      sb.append("&case=" + m._glmParams._caseVal);
      sb.append("'>" + content + "</a>");
      return sb.toString();
    } catch( UnsupportedEncodingException e ) {
      throw new RuntimeException(e);
    }
  }

  @Override protected void queryArgumentValueSet(Argument arg, Properties inputArgs) {
    if(arg == _caseMode){
      if(_caseMode.value() == CaseMode.none)
        _case.disable("n/a");
    } else if (arg == _family) {
      if (_family.value() != Family.binomial) {
        _case.disable("Only for family binomial");
        _caseMode.disable("Only for family binomial");
        _caseWeight.disable("Only for family binomial");
        _thresholds.disable("Only for family binomial");
      }
    }
  }

  /** Returns an array of columns to use for GLM, the last of them being the
   * result column y.
   */
  private  int[] createColumns() {
    BitSet cols = new BitSet();
    for( int i :    _x.value() ) cols.set  (i);
    //for( int i : _negX.value() ) cols.clear(i);
    int[] res = new int[cols.cardinality()+1];
    int x=0;
    for( int i = cols.nextSetBit(0); i >= 0; i = cols.nextSetBit(i+1))
      res[x++] = i;
    res[x] = _y.value();
    return res;
  }

  static JsonObject getCoefficients(int [] columnIds, ValueArray ary, double [] beta){
    JsonObject coefficients = new JsonObject();
    for( int i = 0; i < beta.length; ++i ) {
      String colName = (i == (beta.length - 1)) ? "Intercept" : ary._cols[columnIds[i]]._name;
      coefficients.addProperty(colName, beta[i]);
    }
    return coefficients;
  }

  double [] getFamilyArgs(Family f){
    double [] res = null;
    if( f == Family.binomial ) {
      res = new double []{1.0,1.0};
     // res[GLMSolver.FAMILY_ARGS_CASE] = _case.value();
      res[GLMSolver.FAMILY_ARGS_WEIGHT] = _caseWeight.value();
    }
    return res;
  }

  GLMParams getGLMParams(){
    GLMParams res = new GLMParams();
    res._f = _family.value();
    res._l = _link.value();
    if( res._l == Link.familyDefault )
      res._l = res._f.defaultLink;
    res._maxIter = _maxIter.value();
    res._betaEps = _betaEps.value();
    if(_caseWeight.valid())
      res._caseWeight = _caseWeight.value();
    if(_case.valid())
      res._caseVal = _case.value();
    res._caseMode = _caseMode.valid()?_caseMode.value():CaseMode.none;
    return res;
  }


  @Override protected Response serve() {
    try {
      JsonObject res = new JsonObject();
      ValueArray ary = _key.value();
      int[] columns = createColumns();

      res.addProperty("key", ary._key.toString());
      res.addProperty("h2o", H2O.SELF.toString());

      GLMParams glmParams = getGLMParams();
      LSMSolver lsm = LSMSolver.makeSolver(_lambda.value(),_alpha.value());
      GLMModel m = new GLMModel(ary, columns, lsm, glmParams, null);
      if(_modelKey.specified()){
        GLMModel previousModel = _modelKey.value();
        // set the beta to previous result to have wamr start
        if(previousModel.beta() != null && previousModel.beta().length == m.beta().length)
          m.setBeta(previousModel.beta());
      }
      m.compute();
      if( m.is_solved() ) {     // Solved at all?
        NumberSequence nseq = _thresholds.value();
        double[] arr = nseq == null ? null : nseq._arr;
        if( _xval.specified() && _xval.value() > 1 ) // ... and x-validate
          m.xvalidate(_xval.value(),arr);
        else
          m.validateOn(ary, null,arr); // Full scoring on original dataset
      }
      m.store();
      // Convert to JSON
      res.add("GLMModel", m.toJson());

      // Display HTML setup
      Response r = Response.done(res);
      r.setBuilder(""/*top-level do-it-all builder*/,new GLMBuilder(m));
      return r;
    }catch(GLMException e){
      return Response.error(e.getMessage());
    } catch (Throwable t) {
      t.printStackTrace();
      return Response.error(t.getMessage());
    }
  }



  static class GLMBuilder extends ObjectBuilder {
    final GLMModel _m;
    GLMBuilder( GLMModel m) { _m=m; }
    public String build(Response response, JsonObject json, String contextName) {
      StringBuilder sb = new StringBuilder();;
      modelHTML(_m,json.get("GLMModel").getAsJsonObject(),sb);
      return sb.toString();
    }

    private static void modelHTML( GLMModel m, JsonObject json, StringBuilder sb ) {
      sb.append("<div class='alert'>Actions: " + ((m.is_solved())?(GLMScore.link(m.key(),m._vals[0].bestThreshold(), "Validate on another dataset") + ", "):"") + GLM.link(m._dataset,m, "Compute new model") + "</div>");
      RString R = new RString(
          "<div class='alert %succ'>GLM on data <a href='/Inspect.html?"+KEY+"=%key'>%key</a>. %iterations iterations computed in %time. %warnings %action</div>" +
          "<h4>GLM Parameters</h4>" +
          " %GLMParams %LSMParams" +
          "<h4>Equation: </h4>" +
          "<div><code>%modelSrc</code></div>"+
          "<h4>Coefficients</h4>" +
          "<div>%coefficients</div>");

      // Warnings

      if( m._warnings != null ) {
        StringBuilder wsb = new StringBuilder();
        for( String s : m._warnings )
          wsb.append(s).append("<br>");
        R.replace("warnings",wsb);
        R.replace("succ","alert-warning");
        if(!m.converged())
          R.replace("action","Suggested action: Go to " + ((m.is_solved())?(GLMGrid.link(m, "Grid search") + ", "):"") + " to search for better paramters");
      } else
        R.replace("succ","alert-success");

      // Basic model stuff
      R.replace("key",m._dataset);
      R.replace("time",PrettyPrint.msecs(m._time,true));
      R.replace("iterations",m._iterations);
      R.replace("GLMParams",glmParamsHTML(m));
      R.replace("LSMParams",lsmParamsHTML(m));

      // Pretty equations
      if( m.is_solved() ) {
        JsonObject coefs = json.get("coefficients").getAsJsonObject();
        R.replace("modelSrc",equationHTML(m,coefs));
        R.replace("coefficients",coefsHTML(coefs));
      }
      sb.append(R);
      // Validation / scoring
      if(m._vals != null)
        validationHTML(m._vals,sb);
    }

    private static final String ALPHA   = "&alpha;";
    private static final String LAMBDA  = "&lambda;";
    private static final String EPSILON = "&epsilon;<sub>&beta;</sub>";

    private static final DecimalFormat DFORMAT = new DecimalFormat("###.####");
    private static final String dformat( double d ) {
      return Double.isNaN(d) ? "NaN" : DFORMAT.format(d);
    }

    private static void parm( StringBuilder sb, String x, Object... y ) {
      sb.append("<span><b>").append(x).append(": </b>").append(y[0]).append("</span> ");
    }

    private static String glmParamsHTML( GLMModel m ) {
      StringBuilder sb = new StringBuilder();
      GLMParams glmp = m._glmParams;
      parm(sb,"family",glmp._f);
      parm(sb,"link",glmp._l);
      parm(sb,EPSILON,glmp._betaEps);

      if( glmp._caseMode != CaseMode.none) {
         parm(sb,"case",glmp._caseMode.exp(glmp._caseVal));
         parm(sb,"weight",glmp._caseWeight);
      }
      return sb.toString();
    }

    private static String lsmParamsHTML( GLMModel m ) {
      StringBuilder sb = new StringBuilder();
      LSMSolver lsm = m._solver;
      parm(sb,LAMBDA,lsm._lambda);
      parm(sb,ALPHA  ,lsm._alpha);
      return sb.toString();
    }

    // Pretty equations
    private static String equationHTML( GLMModel m, JsonObject coefs ) {
      RString eq = null;
      switch( m._glmParams._l ) {
      case identity: eq = new RString("y = %equation");   break;
      case logit:    eq = new RString("y = 1/(1 + Math.exp(-(%equation)))");  break;
      default:       eq = new RString("equation display not implemented"); break;
      }
      StringBuilder sb = new StringBuilder();
      for( Entry<String,JsonElement> e : coefs.entrySet() ) {
        if( e.getKey().equals("Intercept") ) continue;
        double v = e.getValue().getAsDouble();
        if( v == 0 ) continue;
        sb.append(dformat(v)).append("*x[").append(e.getKey()).append("] + ");
      }
      sb.append(coefs.get("Intercept").getAsDouble());
      eq.replace("equation",sb.toString());
      return eq.toString();
    }

    private static String coefsHTML( JsonObject coefs ) {
      StringBuilder sb = new StringBuilder();
      sb.append("<table class='table table-bordered table-condensed'>");
      sb.append("<tr>");
      for( Entry<String,JsonElement> e : coefs.entrySet() )
        sb.append("<th>").append(e.getKey()).append("</th>");
      sb.append("</tr>");
      sb.append("<tr>");
      for( Entry<String,JsonElement> e : coefs.entrySet() )
        sb.append("<td>").append(e.getValue().getAsDouble()).append("</td>");
      sb.append("</tr>");
      sb.append("</table>");
      return sb.toString();
    }


    static void validationHTML(GLMValidation val, StringBuilder sb){

      RString valHeader = new RString("<div class='alert'>Validation of model <a href='/Inspect.html?"+KEY+"=%modelKey'>%modelKey</a> on dataset <a href='/Inspect.html?"+KEY+"=%dataKey'>%dataKey</a></div>");
      RString xvalHeader = new RString("<div class='alert'>%valName of model <a href='/Inspect.html?"+KEY+"=%modelKey'>%modelKey</a></div>");

      RString R = new RString("<table class='table table-striped table-bordered table-condensed'>"
          + "<tr><th>Degrees of freedom:</th><td>%DegreesOfFreedom total (i.e. Null);  %ResidualDegreesOfFreedom Residual</td></tr>"
          + "<tr><th>Null Deviance</th><td>%nullDev</td></tr>"
          + "<tr><th>Residual Deviance</th><td>%resDev</td></tr>"
          + "<tr><th>AIC</th><td>%AIC</td></tr>"
          + "<tr><th>Training Error Rate Avg</th><td>%err</td></tr>"
          +"%CM"
          + "</table>");
      RString R2 = new RString(
          "<tr><th>AUC</th><td>%AUC</td></tr>"
          + "<tr><th>Best Threshold</th><td>%threshold</td></tr>");
      if(val.fold() > 1){
        xvalHeader.replace("valName", val.fold() + " fold cross validation");
        xvalHeader.replace("modelKey", val.modelKey());
        sb.append(xvalHeader.toString());
      } else {
        valHeader.replace("modelKey", val.modelKey());
        valHeader.replace("dataKey",val.dataKey());
        sb.append(valHeader.toString());
      }

      R.replace("DegreesOfFreedom",val._n-1);
      R.replace("ResidualDegreesOfFreedom",val._dof);
      R.replace("nullDev",val._nullDeviance);
      R.replace("resDev",val._deviance);
      R.replace("AIC", dformat(val.AIC()));
      R.replace("err",val.err());


      if(val._cm != null){
        R2.replace("AUC", dformat(val.AUC()));
        R2.replace("threshold", dformat(val.bestThreshold()));
        R.replace("CM",R2);
      }
      sb.append(R);
      confusionHTML(val.bestCM(),sb);
      if(val.fold() > 1){
        int nclasses = 2;
        sb.append("<table class='table table-bordered table-condensed'>");
        if(val._cm != null){
          sb.append("<tr><th>Model</th><th>Best Threshold</th><th>AUC</th>");
          for(int c = 0; c < nclasses; ++c)
            sb.append("<th>Err(" + c + ")</th>");
          sb.append("</tr>");
          // Display all completed models
          int i=0;
          for(GLMModel xm:val.models()){
            String mname = "Model " + i++;
            sb.append("<tr>");
            try {
              sb.append("<td>" + "<a href='Inspect.html?"+KEY+"="+URLEncoder.encode(xm.key().toString(),"UTF-8")+"'>" + mname + "</a></td>");
            } catch( UnsupportedEncodingException e1 ) {
              throw new Error(e1);
            }
            sb.append("<td>" + dformat(xm._vals[0].bestThreshold()) + "</td>");
            sb.append("<td>" + dformat(xm._vals[0].AUC()) + "</td>");
            for(double e:xm._vals[0].classError())
              sb.append("<td>" + dformat(e) + "</td>");
            sb.append("</tr>");
          }
        } else {
          sb.append("<tr><th>Model</th><th>Error</th>");
          sb.append("</tr>");
          // Display all completed models
          int i=0;
          for(GLMModel xm:val.models()){
            String mname = "Model " + i++;
            sb.append("<tr>");
            try {
              sb.append("<td>" + "<a href='Inspect.html?"+KEY+"="+URLEncoder.encode(xm.key().toString(),"UTF-8")+"'>" + mname + "</a></td>");
            } catch( UnsupportedEncodingException e1 ) {
              throw new Error(e1);
            }
            sb.append("<td>" + ((xm._vals != null)?xm._vals[0]._err:Double.NaN) + "</td>");
            sb.append("</tr>");
          }
        }
        sb.append("</table>");
      }
    }

    private static void validationHTML( GLMValidation[] vals, StringBuilder sb) {
      if( vals == null || vals.length == 0 ) return;
      sb.append("<h4>Validations</h4>");
      for( GLMValidation val : vals )
        if(val != null)validationHTML(val, sb);
    }

    private static void cmRow( StringBuilder sb, String hd, double c0, double c1, double cerr ) {
      sb.append("<tr><th>").append(hd).append("</th><td>");
      if( !Double.isNaN(c0  )) sb.append( dformat(c0  ));
      sb.append("</td><td>");
      if( !Double.isNaN(c1  )) sb.append( dformat(c1  ));
      sb.append("</td><td>");
      if( !Double.isNaN(cerr)) sb.append( dformat(cerr));
      sb.append("</td></tr>");
    }

    private static void confusionHTML( GLMSolver.ConfusionMatrix cm, StringBuilder sb) {
      if( cm == null ) return;
      sb.append("<table class='table table-bordered table-condensed'>");
      sb.append("<tr><th>Actual / Predicted</th><th>false</th><th>true</th><th>Err</th></tr>");
      double err0 = cm._arr[0][1]/(double)(cm._arr[0][0]+cm._arr[0][1]);
      cmRow(sb,"false",cm._arr[0][0],cm._arr[0][1],err0);
      double err1 = cm._arr[1][0]/(double)(cm._arr[1][0]+cm._arr[1][1]);
      cmRow(sb,"true ",cm._arr[1][0],cm._arr[1][1],err1);
      double err2 = cm._arr[1][0]/(double)(cm._arr[0][0]+cm._arr[1][0]);
      double err3 = cm._arr[0][1]/(double)(cm._arr[0][1]+cm._arr[1][1]);
      cmRow(sb,"Err ",err2,err3,cm.err());
      sb.append("</table>");
    }
  }
}
