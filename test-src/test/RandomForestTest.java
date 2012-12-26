package test;
import static org.junit.Assert.*;

import com.google.gson.JsonObject;
import hex.rf.*;
import hex.rf.Tree.StatType;
import java.util.Properties;
import org.junit.BeforeClass;
import water.*;
import water.parser.ParseDataset;
import water.web.RFView;
import water.web.RandomForestPage;

public class RandomForestTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(3); }

  // ---
  // Test parsing "iris2.csv" and running Random Forest - by driving the web interface
  @org.junit.Test public void testRF_Iris() throws Exception {
    final int CLASSES=3;        // Number of output classes in iris dataset
    Key fkey = load_test_file("smalldata/iris/iris2.csv");
    Key okey = Key.make("iris.hex");
    ParseDataset.parse(okey,DKV.get(fkey));
    UKV.remove(fkey);
    DKV.get(okey);
    final int NTREE=7;

    // Build a Random Forest
    try {
      // RF Page is driven by a Properties
      Properties p = new Properties();
      p.setProperty("Key",okey.toString());
      p.setProperty("ntree",Integer.toString(NTREE));
      p.setProperty("sample",Integer.toString(67));
      p.setProperty("OOBEE","true");
      RandomForestPage RFP = new RandomForestPage();

      // Start RFPage, get a JSON result.
      JsonObject res = RFP.serverJson(null,p,null);
      // From the JSON, get modelKey & ntree to be built
      Key modelKey = Key.make(res.get("modelKey").getAsString());
      Model model = new Model();
      int ntree = res.get("ntree").getAsInt();
      assertEquals(ntree,NTREE);
      // Wait for the trees to be built.  This should be a blocking call someday.
      while( true ) {
        // Peel out the model.
        model = UKV.get(modelKey,model);
        if( model.size() >= ntree ) break;
        try { Thread.sleep(100); } catch( InterruptedException ie ) { }
      }

      assertEquals(CLASSES,model._classes);

      // Now build the properties for a RFView page.
      p.setProperty("dataKey",okey.toString());
      p.setProperty("modelKey",modelKey.toString());
      p.setProperty("ntree",Integer.toString(ntree));
      p.setProperty("atree",Integer.toString(ntree));

      RFView rfv = new RFView();
      JsonObject rfv_res = rfv.serverJson(null,p,null);
      rfv.serveImpl(null,p,null); // Build the CM

      // Verify Goodness and Light
      Key oKey2 = Key.make(rfv_res.get("dataKey").getAsString());
      assertEquals(okey,oKey2);
      Key mkey2 = Key.make(rfv_res.get("modelKey").getAsString());
      assertEquals(modelKey,mkey2);
      Key confKey = Key.make(rfv_res.get("confusionKey").getAsString());
      // Should be a pre-built confusion
      Confusion C = UKV.get(confKey,new Confusion());
      for( long[] x : C._matrix ) {
        for( long y : x )
          System.out.print(" "+y);
        System.out.println();
      }

      // This should be a 7-tree confusion matrix on the iris dataset, build
      // with deterministic trees.
      // Confirm the actual results.
      long ans[][] = new long[][]{{47,0,0},{0,44,4},{0,1,47}};
      for( int i=0; i<ans.length; i++ )
        assertArrayEquals(ans[i],C._matrix[i]);

      // Cleanup
      UKV.get(modelKey,new Model()).deleteKeys();
      UKV.remove(modelKey);
      UKV.remove(confKey);

    } catch( water.web.Page.PageError pe ) {
      fail("RandomForestPage fails with "+pe);
    } finally {
      UKV.remove(okey);
    }
  }


  // Test kaggle/creditsample-test data
  @org.junit.Test public void kaggle_credit() throws Exception {
    Key fkey = load_test_file("smalldata/kaggle/creditsample-training.csv.gz");
    Key okey = Key.make("credit.hex");
    ParseDataset.parse(okey,DKV.get(fkey));
    UKV.remove(fkey);
    UKV.remove(Key.make("smalldata/kaggle/creditsample-training.csv.gz_UNZIPPED"));
    UKV.remove(Key.make("smalldata\\kaggle\\creditsample-training.csv.gz_UNZIPPED"));
    ValueArray val = ValueArray.value(okey);

    // Check parsed dataset
    assertEquals("Number of chunks", 4, val.chunks());
    assertEquals("Number of rows", 150000, val.numRows());
    assertEquals("Number of cols", 12, val.numCols());

    // setup default values for DRF
    int ntrees  = 3;
    int depth   = 30;
    int gini    = StatType.GINI.ordinal();
    int seed =  42;
    StatType statType = StatType.values()[gini];
    final int classcol = 1; // For credit: classify column 1
    final int ignore[] = new int[]{6}; // Ignore column 6

    // Start the distributed Random Forest
    DRF drf = hex.rf.DRF.web_main(val,ntrees,depth,1.0f,(short)1024,statType,seed,classcol,ignore, Key.make("model"),true,null,-1,false,null);
    // Just wait little bit
    drf.get();
    // Create incremental confusion matrix.
    Model model;
    while( true ) {
      // RACEY BUG HERE: Model is supposed to be complete after drf.get, but as
      // of 11/5/2012 it takes a little while for all trees to appear.
      model = UKV.get(drf._modelKey,new Model());
      if( model.size()==ntrees ) break;
      Thread.sleep(100);
    }
    assertEquals("Number of classes", 2,  model._classes);
    assertEquals("Number of trees", ntrees, model.size());

    model.deleteKeys();
    UKV.remove(drf._modelKey);
    UKV.remove(okey);
  }
}
