import unittest
import random, sys, time
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_browse as h2b, h2o_exec as h2e

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # for determinism, I guess we should spit out the seed?
        # random.seed(SEED)
        SEED = random.randint(0, sys.maxint)
        # if you have to force to redo a test
        # SEED = 
        random.seed(SEED)
        print "\nUsing random seed:", SEED

        h2o.build_cloud(1)

    @classmethod
    def tearDownClass(cls):
        # wait while I inspect things
        # time.sleep(1500)
        h2o.tear_down_cloud()

    def test_exec_covtype_cols(self):
        csvPathname = h2o.find_dataset('UCI/UCI-large/covtype/covtype.data')
        parseKey = h2o_cmd.parseFile(None, csvPathname, 'covtype.data', 'c.hex', 10)
        print "\nParse key is:", parseKey['destination_key']

        ### h2b.browseTheCloud()
        start = time.time()
        # passes with suffix, fails without?
        # suffix = ""
        suffix = ".hex"
        print "Using .hex suffix everywhere until we get type checking in H2O..fails with first size=1 otherwise"
        for i in range(54):
            execExpr = "Result" + str(i) + suffix + " = c.hex[" + str(i) + "]"
            print "execExpr:", execExpr
            h2e.exec_expr(h2o.nodes[0], execExpr, resultKey="Result" + str(i) + suffix, timeoutSecs=4)

        if (h2o.check_sandbox_for_errors()):
            raise Exception("Found errors in sandbox stdout or stderr, on trial #%s." % trial)

        print "exec end on ", "covtype.data" , 'took', time.time() - start, 'seconds'


if __name__ == '__main__':
    h2o.unit_main()
