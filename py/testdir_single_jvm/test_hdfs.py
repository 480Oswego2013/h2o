
import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_hosts
import h2o_browse as h2b
import h2o_import as h2i
import time, random

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # assume we're at 0xdata with it's hdfs namenode
        h2o.build_cloud(1,use_hdfs=True, hdfs_name_node="192.168.1.151")

    @classmethod
    def tearDownClass(cls):
        # hang for many hour, so you can play with the browser
        # FIX!, should be able to do something that waits till browser is quit?
        if not h2o.browse_disable:
            time.sleep(500000)

        h2o.tear_down_cloud()

    def test_B_hdfs_files(self):
        print "\nLoad a list of files from HDFS, parse and do 1 RF tree"
        print "CAVEAT: apparently there is no H2O output if you don't have user/group permission"
        print "to inspect the HDFS root specified. This will look like a 'Key not found' fail." 
        print "You can tell because no keys will have been preloaded from hdfs in the sandbox/*stdout*"
        print "The username used will be the one that executed this test"
        print "testdir_hosts/test_from_hdfs_hosts.py will use a config json-specified username (0xdiag)"
        print "\nClicking on the popup browser on the Node page will show no preloaded hdfs keys"
        print "\nYou can try running as hduser/hduser if fail"
        # larger set in my local dir
        # fails because classes aren't integers
        #    "allstate_claim_prediction_train_set.zip",
        csvFilenameAll = [
            "TEST-poker1000.csv",
            "leads.csv",
            "and-testing.data",
            "arcene2_train.both",
            "arcene_train.both",
            "bestbuy_test.csv",
            "bestbuy_train.csv",
            "covtype.data",
            "covtype.4x.shuffle.data",
            "covtype4x.shuffle.data",
            "covtype.13x.data",
            "covtype.13x.shuffle.data",
            "covtype.169x.data",
            "prostate_2g.csv",
            "prostate_long.csv.gz",
            "prostate_long_1G.csv",
            "hhp.unbalanced.012.1x11.data.gz",
            "hhp.unbalanced.012.data.gz",
            "hhp.unbalanced.data.gz",
            "hhp2.os.noisy.0_1.data",
            "hhp2.os.noisy.9_4.data",
            "hhp_9_14_12.data",
            "poker_c1s1_testing_refresh.csv",
            "3G_poker_shuffle",
            "billion_rows.csv.gz",
            "poker-hand.1244M.shuffled311M.full.txt",
        ]

        # pick 8 randomly!
        if (1==0):
            csvFilenameList = random.sample(csvFilenameAll,8)
        # Alternatively: do the list in order! Note the order is easy to hard
        else:
            csvFilenameList = csvFilenameAll

        # pop open a browser on the cloud
        h2b.browseTheCloud()

        timeoutSecs = 200
        # save the first, for all comparisions, to avoid slow drift with each iteration
        firstglm = {}
        for csvFilename in csvFilenameList:
            # creates csvFilename.hex from file in hdfs dir 
            print "Loading", csvFilename, 'from HDFS'
            parseKey = h2i.parseHdfsFile(csvFilename=csvFilename, timeoutSecs=1000)
            print csvFilename, 'parse time:', parseKey['response']['time']
            print "parse result:", parseKey['destination_key']
            # I use this if i want the larger set in my localdir

            print "\n" + csvFilename
            start = time.time()
            RFview = h2o_cmd.runRFOnly(trees=1,parseKey=parseKey,timeoutSecs=2000)
            h2b.browseJsonHistoryAsUrlLastMatch("RFView")
            # wait in case it recomputes it
            time.sleep(10)

            sys.stdout.write('.')
            sys.stdout.flush() 



if __name__ == '__main__':
    h2o.unit_main()
