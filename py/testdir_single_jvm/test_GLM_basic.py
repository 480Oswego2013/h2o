import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])
import h2o, h2o_cmd, h2o_glm

class Basic(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_B_benign(self):
        print "\nStarting benign.csv"
        csvFilename = "benign.csv"
        csvPathname = h2o.find_file('smalldata/logreg' + '/' + csvFilename)
        parseKey = h2o_cmd.parseFile(csvPathname=csvPathname, key2=csvFilename)
        # columns start at 0
        y = "3"
        x = ""
        # cols 0-13. 3 is output
        # no member id in this one
        for appendx in xrange(14):
            if (appendx == 3): 
                print "\n3 is output."
            else:
                if x == "": 
                    x = str(appendx)
                else:
                    x = x + "," + str(appendx)

                csvFilename = "benign.csv"
                csvPathname = h2o.find_file('smalldata/logreg' + '/' + csvFilename)
                print "\nx:", x
                print "y:", y
                
                kwargs = {'x': x, 'y':  y}
                # fails with num_cross_validation_folds
                print "Not doing num_cross_validation_folds with benign. Fails with 'unable to solve?'"
                glm = h2o_cmd.runGLMOnly(parseKey=parseKey, timeoutSecs=5, **kwargs)
                # no longer look at STR?
                h2o_glm.simpleCheckGLM(self, glm, None, **kwargs)

    def test_C_prostate(self):
        print "\nStarting prostate.csv"
        # columns start at 0
        y = "1"
        x = ""
        csvFilename = "prostate.csv"
        csvPathname = h2o.find_file('smalldata/logreg' + '/' + csvFilename)
        parseKey = h2o_cmd.parseFile(csvPathname=csvPathname, key2=csvFilename)

        for appendx in xrange(9):
            if (appendx == 0):
                print "\n0 is member ID. not used"
            elif (appendx == 1):
                print "\n1 is output."
            else:
                if x == "": 
                    x = str(appendx)
                else:
                    x = x + "," + str(appendx)

                sys.stdout.write('.')
                sys.stdout.flush() 
                print "\nx:", x
                print "y:", y

                kwargs = {'x': x, 'y':  y, 'num_cross_validation_folds': 5}
                glm = h2o_cmd.runGLMOnly(parseKey=parseKey, timeoutSecs=2, **kwargs)
                # ID,CAPSULE,AGE,RACE,DPROS,DCAPS,PSA,VOL,GLEASON
                h2o_glm.simpleCheckGLM(self, glm, 'AGE', **kwargs)

if __name__ == '__main__':
    h2o.unit_main()
