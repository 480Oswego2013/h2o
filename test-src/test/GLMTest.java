package test;
import static org.junit.Assert.assertEquals;
import com.google.gson.*;
import hex.LinearRegression;
import hex.GLMSolver;
import hex.LSMSolver;
import hex.GLMSolver.GLMModel;

import java.util.Map;
import java.util.Random;
import org.junit.*;
import water.*;
import water.exec.Exec;
import water.exec.PositionedException;
import water.parser.ParseDataset;

// A series of tests designed to validate GLM's *statistical results* and not,
// i.e. correct behavior when handed bad/broken/null arguments (although those
// tests are also good).

public class GLMTest extends TestUtil {

  JsonObject computeGLMlog( LSMSolver lsms, ValueArray va, boolean cat ) {
    return computeGLM( GLMSolver.Family.binomial, lsms, va, cat, null); }

  JsonObject computeGLM( GLMSolver.Family family, LSMSolver lsms, ValueArray va, boolean cat, int[] cols ) {
    // All columns in order, and use last as response variable
    if( cols == null ) {
      cols= new int[va._cols.length];
      for( int i=0; i<cols.length; i++ ) cols[i]=i;
    }

    // Now a Gaussian GLM model for the same thing
    GLMSolver.GLMParams glmp = new GLMSolver.GLMParams();
    glmp._f = family;
    glmp._l = glmp._f.defaultLink;
    //glmp._familyArgs = glmp._f.defaultArgs;
    glmp._betaEps = 0.000001;
    glmp._maxIter = 100;
    glmp._expandCat = cat;
    // Solver
    GLMModel m = new GLMModel(va, cols, lsms, glmp, null);
    // Solve it!
    m.compute();
    JsonObject glm = m.toJson();
    return glm;
  }

  // ---
  // Test GLM on a simple dataset that has an easy Linear Regression.
  @Test public void testLinearRegression() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.
      // Equation is: y = 0.1*x+0
      ValueArray va =
        va_maker(datakey,
                 new byte []{  0 ,  1 ,  2 ,  3 ,  4 ,  5 ,  6 ,  7 ,  8 ,  9 },
                 new float[]{0.0f,0.1f,0.2f,0.3f,0.4f,0.5f,0.6f,0.7f,0.8f,0.9f});

      // Compute LinearRegression between columns 0 & 1
      JsonObject lr = LinearRegression.run(va,0,1);
      assertEquals( 0.0, lr.get("Beta0"   ).getAsDouble(), 0.000001);
      assertEquals( 0.1, lr.get("Beta1"   ).getAsDouble(), 0.000001);
      assertEquals( 1.0, lr.get("RSquared").getAsDouble(), 0.000001);

      LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
      JsonObject glm = computeGLM(GLMSolver.Family.gaussian,lsms,va,false,null); // Solve it!
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals( 0.0, coefs.get("Intercept").getAsDouble(), 0.000001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);

    } finally {
      UKV.remove(datakey);
    }
  }

  // Now try with a more complex binomial regression
  @Test public void testLogReg_Basic() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.  2 columns, all numbers from 0-9
      ValueArray va = va_maker(datakey,2,10, new DataExpr() {
          double expr( byte[] x ) { return 1.0/(1.0+Math.exp(-(0.1*x[0]+0.3*x[1]-2.5))); } } );

      LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
      JsonObject glm = computeGLMlog(lsms,va,false); // Solve it!
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.000001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

    } finally {
      UKV.remove(datakey);
    }
  }

  // Compute the 'expr' result from the sum of coefficients,
  // plus a small random value.
  public static class DataExpr_Dirty extends DataExpr {
    final Random _R;
    final double _coefs[];
    DataExpr_Dirty( Random R, double[] coefs ) { _R = R; _coefs = coefs; }
    double expr( byte[] cols ) {
      double sum = _coefs[_coefs.length-1]+
        (_R.nextDouble()-0.5)/1000.0; // Add some noise
      for( int i = 0; i< cols.length; i++ )
        sum += cols[i]*_coefs[i];
      return 1.0/(1.0+Math.exp(-sum));
    }
  }

  @Test public void testLogReg_Dirty() {
    Key datakey = Key.make("datakey");
    try {
      Random R = new Random(0x987654321L);
      for( int i=0; i<10; i++ ) {
        double[] coefs = new double[] { R.nextDouble(),R.nextDouble(),R.nextDouble() };
        ValueArray va = va_maker(datakey,2,10, new DataExpr_Dirty(R, coefs));

        LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
        JsonObject glm = computeGLMlog(lsms,va,false); // Solve it!
        JsonObject res = glm.get("coefficients").getAsJsonObject();
        assertEquals(coefs[0], res.get("0")        .getAsDouble(), 0.001);
        assertEquals(coefs[1], res.get("1")        .getAsDouble(), 0.001);
        assertEquals(coefs[2], res.get("Intercept").getAsDouble(), 0.001);
        UKV.remove(datakey);
      }
    } finally {
      UKV.remove(datakey);
    }
  }

  @Test public void testLogReg_Penalty() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.  2 columns, all numbers from 0-9
      ValueArray va = va_maker(datakey,2,10, new DataExpr() {
          double expr( byte[] x ) { return 1.0/(1.0+Math.exp(-(0.1*x[0]+0.3*x[1]-2.5))); } } );

      // No penalty
      LSMSolver lsms0 = LSMSolver.makeSolver();
      JsonObject glm = computeGLMlog(lsms0,va,false); // Solve it!
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

      // L1 penalty
      LSMSolver lsms1 = LSMSolver.makeL1Solver(LSMSolver.DEFAULT_LAMBDA);
      glm = computeGLMlog(lsms1,va,false); // Solve it!
      coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

      // L2 penalty
      LSMSolver lsms2 = LSMSolver.makeL2Solver(LSMSolver.DEFAULT_LAMBDA);
      glm = computeGLMlog(lsms2,va,false); // Solve it!
      coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

      // ELASTIC penalty
      LSMSolver lsmsx = LSMSolver.makeElasticNetSolver(LSMSolver.DEFAULT_LAMBDA);
      glm = computeGLMlog(lsmsx,va,false); // Solve it!
      coefs = glm.get("coefficients").getAsJsonObject();
      assertEquals(-2.5, coefs.get("Intercept").getAsDouble(), 0.00001);
      assertEquals( 0.1, coefs.get("0")        .getAsDouble(), 0.000001);
      assertEquals( 0.3, coefs.get("1")        .getAsDouble(), 0.000001);

    } finally {
      UKV.remove(datakey);
    }
  }


  // Predict whether or not a car has a 3-cylinder engine
  @Test public void testLogReg_CARS_CSV() {
    Key k1=null,k2=null;
    try {
      k1 = loadAndParseKey("h.hex","smalldata/cars.csv");
      // Fold the cylinders down to 1/0 for 3/not-3
      k2 = Exec.exec("colSwap(h.hex,2,h.hex$cylinders==3?1:0)","h2.hex");
      // Columns for displacement, power, weight, 0-60, year, then response is cylinders
      int[] cols= new int[]{3,4,5,6,7,2};
      ValueArray va = ValueArray.value(DKV.get(k2));
      // Compute the coefficients
      LSMSolver lsmsx = LSMSolver.makeElasticNetSolver(LSMSolver.DEFAULT_LAMBDA);
      JsonObject glm = computeGLM( GLMSolver.Family.binomial, lsmsx, va, false, cols );

      // Now run the dataset through the equation and see how close we got
      JsonObject coefs = glm.get("coefficients").getAsJsonObject();
      double icept = coefs.get("Intercept").getAsDouble();
      double disp  = coefs.get("displacement (cc)").getAsDouble();
      double power = coefs.get("power (hp)").getAsDouble();
      double weight= coefs.get("weight (lb)").getAsDouble();
      double accel = coefs.get("0-60 mph (s)").getAsDouble();
      double year  = coefs.get("year").getAsDouble();
      AutoBuffer ab = va.getChunk(0);

      ROWS:                     // Skip bad rows
      for( int i=0; i<va._numrows; i++ ) {
        for( int j=2; j<8; j++ ) if( va.isNA(ab,i,j) ) continue ROWS;
        double x =
          disp  *va.datad(ab,i,3) +
          power *va.datad(ab,i,4) +
          weight*va.datad(ab,i,5) +
          accel *va.datad(ab,i,6) +
          year  *va.datad(ab,i,7) +
          icept;
        double p = 1.0/(1.0+Math.exp(-x)); // Prediction
        double cyl = va.data(ab,i,2); // 1==3-cyl, 0==not-3-cyl
        assertEquals(cyl,p,0.005); // Hopefully fairly close to 0 for 3-cylinder, 1 for not-3
      }

    } catch( PositionedException pe ) {
      throw new Error(pe);
    } finally {
      UKV.remove(k1);
      if( k2 != null ) UKV.remove(k2);
    }
  }

  // Categorical Test!  Lets make a simple categorical test case
  @Test public void testLogRegCat_Basic() {
    Key datakey = Key.make("datakey");
    try {
      // Make some data to test with.
      // Low's = 0,0,0  ==> should predict as 0
      // Med's = 0,1,0  ==> should predict as 0.3333...
      // Highs = 1,1,1  ==> should predict as 1
      ValueArray va =
        va_maker(datakey,
                 new String[]{ "Low", "Med", "High", "Low", "Med", "High", "Low", "Med", "High" },
                 new byte  []{     0,     0,      1,     0,     1,      1,     0,     0,     1  });

      LSMSolver lsms = LSMSolver.makeSolver(); // Default normalization of NONE
      JsonObject glm = computeGLMlog(lsms,va,true); // Solve it!
      JsonObject jcoefs = glm.get("coefficients").getAsJsonObject();
      double icept = jcoefs.get("Intercept").getAsDouble();

      assertCat(jcoefs,icept,"Low" ,0.0      );
      assertCat(jcoefs,icept,"Med" ,0.3333333);
      assertCat(jcoefs,icept,"High",1.0      );
    } finally {
      UKV.remove(datakey);
    }
  }

  // Assert reasonable results for the categorical predictor
  static void assertCat(JsonObject jcoefs, double icept, String category, double expected) {
    // For categoricals, we expanded the terms into an array of boolean
    // predictors all zero, except for the given term which is set to 1.
    // Example: factors/categories: Low, Med, High.
    // Since 3 factors, we make an array of size 3.
    // Low maps to {1,0,0}, Med maps to {0,1,0} and High maps to {0,0,1}.
    // The equation is normally: 1/1+exp(-(c0*x[0] + c1*x[1]+ c2*x[2]... + icept))
    // When computing the math, all predictors are zero except the one...  so
    // the equation expansion only needs to sum the one coeficient multiplied
    // by 1, plus the intercept.
    double coef = jcoefs.get("0."+category).getAsDouble();
    double predict = 1.0/(1.0+Math.exp(-(coef*1.0/* + all other terms are 0 */+icept)));
    assertEquals(expected,predict,0.000001);
  }
}
