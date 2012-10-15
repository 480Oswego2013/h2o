package water.exec;
import water.Key;

/**
 * Execute a generic R string, in the context of an H2O Cloud
 *
 * @author cliffc@0xdata.com
 */
public class Exec {
  // Execute some generic R string.  Return a
  public static Key exec( String x ) {
    Key k = Key.make("Result");
    try {
      Expr e = new RLikeParser().parse(x);
      Expr.Result r = e.eval();
      Expr.assign(0,k, r);
      r.dispose();
    } catch (ParserException e) {
      System.out.println(e.report(x));
    } catch (EvaluationException e) {
      System.out.println(e.report(x));
    }
    return k;
  }
}