package test;
import static org.junit.Assert.*;
import com.google.common.io.Closeables;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import water.*;
import water.parser.ParseDataset;

public class TestUtil {
  private static int _initial_keycnt = 0;

  @BeforeClass public static void setupCloud() {
    H2O.main(new String[] { });
    _initial_keycnt = H2O.store_size();
  }

  @AfterClass public static void checkLeakedKeys() {
    DKV.write_barrier();
    int leaked_keys = H2O.store_size() - _initial_keycnt;
    if( leaked_keys != 0 ) 
      for( Key k : H2O.keySet() )
        System.err.println("Leaked key: "+k);
    assertEquals("No keys leaked", 0, leaked_keys);
  }

  // Stall test until we see at least X members of the Cloud
  public static void stall_till_cloudsize(int x) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < 10000) {
      if (H2O.CLOUD.size() >= x) break;
      try { Thread.sleep(100); } catch( InterruptedException ie ) {}
    }
    assertTrue("Cloud size of "+x, H2O.CLOUD.size() >=x );
  }

  public static File find_test_file( String fname ) {
    // When run from eclipse, the working directory is different.
    // Try pointing at another likely place
    File file = new File(fname);
    if( !file.exists() ) file = new File("build/"+fname);
    if( !file.exists() ) file = new File("../"+fname);
    if( !file.exists() ) file = new File("../build/"+fname);
    return file;
  }

  public static Key load_test_file( String fname ) {
    return load_test_file(find_test_file(fname));
  }

  public static Key load_test_file(File file, String keyname){
    Key key = null;
    FileInputStream fis = null;
    try {
      fis = new FileInputStream(file);
      key = ValueArray.readPut(keyname, fis);
    } catch( IOException e ) {
      Closeables.closeQuietly(fis);
    }
    if( key == null ) fail("failed load to "+file.getName());
    return key;
  }

  public static Key load_test_file( File file ) {
    return load_test_file(file, file.getPath());
  }

  public static ValueArray parse_test_key(Key fileKey, Key parsedKey) {
    System.out.println("PARSE: " + fileKey + ", " + parsedKey);
    ParseDataset.parse(parsedKey, DKV.get(fileKey));
    return ValueArray.value(DKV.get(parsedKey));
  }
  public static ValueArray parse_test_key(Key fileKey) {
    return parse_test_key(fileKey, Key.make());
  }

  public static String replaceExtension(String fname, String newExt){
    int i = fname.lastIndexOf('.');
    if(i == -1) return fname + "." + newExt;
    return fname.substring(0,i) + "." + newExt;
  }


  public static String getHexKeyFromFile(File f){
    return replaceExtension(f.getName(),"hex");
  }

  public static String getHexKeyFromRawKey(String str){
    if(str.startsWith("hdfs://"))str = str.substring(7);
    return replaceExtension(str,"hex");
  }


  // --------
  // Build a ValueArray from a collection of normal arrays.
  // The arrays must be all the same length.
  ValueArray va_maker( Key key, Object... arys ) {

    // Gather basic column info, 1 column per array
    ValueArray.Column cols[] = new ValueArray.Column[arys.length];
    char off = 0;
    int numrows = -1;
    for( int i=0; i<arys.length; i++ ) {
      ValueArray.Column col = cols[i] = new ValueArray.Column();
      col._name = Integer.toString(i);
      col._off = off;
      col._scale = 1;
      col._min = Double.MAX_VALUE;
      col._max = Double.MIN_VALUE;
      col._mean = 0.0;
      Object ary = arys[i];
      if( ary instanceof byte[] ) {
        col._size = 1;
        col._n = ((byte[])ary).length;
      } else if( ary instanceof float[] ) {
        col._size = -4;
        col._n = ((float[])ary).length;
      } else if( ary instanceof double[] ) {
        col._size = -8;
        col._n = ((double[])ary).length;
      } else {
        throw H2O.unimpl();
      }
      off += Math.abs(col._size);
      if( numrows == -1 ) numrows = (int)col._n;
      else assert numrows == col._n;
    }
    int rowsize = off;

    // Compact data into VA format, and compute min/max/mean
    AutoBuffer ab = new AutoBuffer(numrows*rowsize);
    for( int i=0; i<numrows; i++ ) {
      for( int j=0; j<arys.length; j++ ) {
        ValueArray.Column col = cols[j];
        double d;  float f;  byte b;
        switch( col._size ) {
        case  1: ab.put1 (b = ((byte  [])arys[j])[i]);  d = b;  break;
        case -4: ab.put4f(f = ((float [])arys[j])[i]);  d = f;  break;
        case -8: ab.put8d(d = ((double[])arys[j])[i]);  d = d;  break;
        default: throw H2O.unimpl();
        }
        if( d > col._max ) col._max = d;
        if( d < col._min ) col._min = d;
        col._mean += d;
      }
    }
    // Sum to mean
    for( ValueArray.Column col : cols )
      col._mean /= col._n;

    // 2nd pass for sigma.  Sum of squared errors, then divide by n and sqrt
    for( int i=0; i<numrows; i++ ) {
      for( int j=0; j<arys.length; j++ ) {
        ValueArray.Column col = cols[j];
        double d;
        switch( col._size ) {
        case  1: d = ((byte  [])arys[j])[i];  break;
        case -4: d = ((float [])arys[j])[i];  break;
        case -8: d = ((double[])arys[j])[i];  break;
        default: throw H2O.unimpl();
        }
        col._sigma += (d - col._mean)*(d-col._mean);
      }
    }
    // RSS to sigma
    for( ValueArray.Column col : cols )
      col._sigma = Math.sqrt(col._sigma/col._n);

    // Write out data & keys
    ValueArray ary = new ValueArray(key,numrows,rowsize,cols);
    Key ckey0 = ary.getChunkKey(0);
    UKV.put(ckey0,new Value(ckey0,ab.bufClose()));
    UKV.put( key ,ary.value());
    return ary;
  }

  // Make a M-dimensional data grid, with N points on each dimension running
  // from 0 to N-1.  The grid is flattened, so all N^M points are in the same
  // ValueArray.  Add a final column which is computed by running an expression
  // over the other columns, typically this final column is the input to GLM
  // which then attempts to recover the expression.
  public abstract static class DataExpr { abstract double expr( byte[] cols ); }
  ValueArray va_maker( Key key, int M, int N, DataExpr expr ) {
    if( N <= 0 || N > 127 || M <= 0 ) throw H2O.unimpl();
    long Q = 1;
    for( int i=0; i<M; i++ ) { Q *= N; if( (long)(int)Q != Q ) throw H2O.unimpl(); }
    byte[][] x = new byte[M][(int)Q];
    double[] d = new double [(int)Q];

    byte[] bs = new byte[M];
    int q = 0;
    int idx = M-1;
    d[q++] = expr.expr(bs);
    while( idx >= 0 ) {
      if( ++bs[idx] >= N ) {
        bs[idx--] = 0;
      } else {
        idx = M-1;
        for( int i=0; i<M; i++ ) x[i][q] = bs[i];
        d[q++] = expr.expr(bs);
      }
    }
    Object[] arys = new Object[M+1];
    for( int i=0; i<M; i++ ) arys[i] = x[i];
    arys[M] = d;
    return va_maker(key,arys);
  }
}