import os, json, unittest, time, shutil, sys
import h2o_cmd
import h2o

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # I suppose we really should move flatfile creation for hosts/ec2/and local
        # into build_cloud. it doesn't need to be here external
        h2o.write_flatfile(node_count=3)
        h2o.build_cloud(node_count=3,use_flatfile=True)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_A_Basic(self):
        for n in h2o.nodes:
            c = n.get_cloud()
            self.assertEqual(c['cloud_size'], len(h2o.nodes), 'inconsistent cloud size')

    def test_B_RF_iris2(self):
        # FIX! will check some results with RFview
        RFview = h2o_cmd.runRF( trees = 6, timeoutSecs = 10,
                csvPathname = h2o.find_file('smalldata/iris/iris2.csv'))

    def test_C_RF_poker100(self):
        h2o_cmd.runRF( trees = 6, timeoutSecs = 10,
                csvPathname = h2o.find_file('smalldata/poker/poker100'))

    def test_D_GenParity1(self):
        global SYNDATASETS_DIR
        global SYNSCRIPTS_DIR

        SYNDATASETS_DIR = './syn_datasets'
        if os.path.exists(SYNDATASETS_DIR):
            shutil.rmtree(SYNDATASETS_DIR)
        os.mkdir(SYNDATASETS_DIR)

        SYNSCRIPTS_DIR = './syn_scripts'

        # always match the run below!
        for x in xrange (11,100,10):
            # Have to split the string out to list for pipe
            shCmdString = "perl " + SYNSCRIPTS_DIR + "/parity.pl 128 4 "+ str(x) + " quad"
            # FIX! as long as we're doing a couple, you'd think we wouldn't have to 
            # wait for the last one to be gen'ed here before we start the first below.
            h2o.spawn_cmd_and_wait('parity.pl', shCmdString.split(),timeout=3)
            # the algorithm for creating the path and filename is hardwired in parity.pl..i.e
            csvFilename = "parity_128_4_" + str(x) + "_quad.data"  

        trees = 6
        timeoutSecs = 20
        # always match the gen above!
        # kbn was failing for 46/56 trees (race)
        # reduce to get intermittent failures to lessen, for now
        for x in xrange (11,60,10):
            sys.stdout.write('.')
            sys.stdout.flush()
            csvFilename = "parity_128_4_" + str(x) + "_quad.data"  
            csvPathname = SYNDATASETS_DIR + '/' + csvFilename
            # FIX! TBD do we always have to kick off the run from node 0?
            # what if we do another node?
            # FIX! do we need or want a random delay here?
            h2o_cmd.runRF( trees=trees, timeoutSecs=timeoutSecs,
                    csvPathname=csvPathname)
            trees += 10
            ### timeoutSecs += 2


if __name__ == '__main__':
    h2o.unit_main()
