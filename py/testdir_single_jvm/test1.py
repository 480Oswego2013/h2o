import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd as cmd

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_Basic(self):
        for n in h2o.nodes:
            c = n.get_cloud()
            self.assertEqual(c['cloud_size'], len(h2o.nodes), 'inconsistent cloud size')

    def notest_RF_iris2(self):
        trees = 6
        timeoutSecs = 20
        csvPathname = h2o.find_file('smalldata/iris/iris2.csv')
        cmd.runRF(trees=trees, timeoutSecs=timeoutSecs, csvPathname=csvPathname)

    def notest_RF_poker100(self):
        trees = 6
        timeoutSecs = 20
        csvPathname = h2o.find_file('smalldata/poker/poker100')
        cmd.runRF(trees=trees, timeoutSecs=timeoutSecs, csvPathname=csvPathname)

    def test_GenParity1(self):
        SYNDATASETS_DIR = h2o.make_syn_dir()
        parityPl = h2o.find_file('syn_scripts/parity.pl')

# two row dataset gets this. Avoiding it for now
# java.lang.ArrayIndexOutOfBoundsException: 1
# at hex.rf.Data.sample_fair(Data.java:149)

        # always match the run below!
        print "\nAssuming two row dataset is illegal. avoiding"

        for x in xrange (3,100,10):
            shCmdString = "perl " + parityPl + " 128 4 "+ str(x) + " quad"
            h2o.spawn_cmd_and_wait('parity.pl', shCmdString.split())
            # algorithm for creating the path and filename is hardwired in parity.pl.
            csvFilename = "parity_128_4_" + str(x) + "_quad.data"  

        trees = 6
        timeoutSecs = 20
        # always match the gen above!
        for x in xrange (3,100,10):
            sys.stdout.write('.')
            sys.stdout.flush()
            csvFilename = "parity_128_4_" + str(x) + "_quad.data"  
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename
            cmd.runRF(trees=trees, timeoutSecs=timeoutSecs, csvPathname=csvPathname)

            trees += 10
            timeoutSecs += 2

if __name__ == '__main__':
    h2o.unit_main()
