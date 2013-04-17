
import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_hosts
import h2o_browse as h2b
import h2o_import as h2i
import time, random

class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        # assume we're at 0xdata with it's hdfs namenode
        global localhost
        localhost = h2o.decide_if_localhost()
        if (localhost):
            h2o.build_cloud(1)
        else:
            # all hdfs info is done thru the hdfs_config michal's ec2 config sets up?
            h2o_hosts.build_cloud_with_hosts(1, 
                # this is for our amazon ec hdfs
                # see https://github.com/0xdata/h2o/wiki/H2O-and-s3n
                hdfs_name_node='10.78.14.235:9000',
                hdfs_version='0.20.2')

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parse_syn_s3n_thru_hdfs(self):
        # I put these file copies on s3 with unique suffixes
        # under this s3n "path"
        csvFilename = "syn_datasets/*_10000x200*"

        trialMax = 1
        timeoutSecs = 500
        URI = "s3n://home-0xdiag-datasets"
        s3nKey = URI + "/" + csvFilename

        for trial in range(trialMax):
            # since we delete the key, we have to re-import every iteration
            # s3n URI thru HDFS is not typical.
            importHDFSResult = h2o.nodes[0].import_hdfs(URI)
            s3nFullList = importHDFSResult['succeeded']
            ### print "s3nFullList:", h2o.dump_json(s3nFullList)
            self.assertGreater(len(s3nFullList),8,"Didn't see more than 8 files in s3n?")

            key2 = "syn_datasets_" + str(trial) + ".hex"
            print "Loading s3n key: ", s3nKey, 'thru HDFS'
            start = time.time()
            parseKey = h2o.nodes[0].parse(s3nKey, key2,
                timeoutSecs=500, retryDelaySecs=10, pollTimeoutSecs=60)
            elapsed = time.time() - start

            print s3nKey, 'parse time:', parseKey['response']['time']
            print "parse result:", parseKey['destination_key']
            print "Trial #", trial, "completed in", elapsed, "seconds.", \
                "%d pct. of timeout" % ((elapsed*100)/timeoutSecs)

            inspect = h2o_cmd.runInspect(None, parseKey['destination_key'])
            print "\n" + key2 + \
                "    num_rows:", "{:,}".format(inspect['num_rows']), \
                "    num_cols:", "{:,}".format(inspect['num_cols'])

            print "Deleting key in H2O so we get it from s3n (if ec2) or nfs again.", \
                  "Otherwise it would just parse the cached key."
            storeView = h2o.nodes[0].store_view()
            ### print "storeView:", h2o.dump_json(storeView)
            print "BROKE: we can't delete keys with a pattern match yet..this fails"
            print "So we only do 1 trial and don't delete"
            # print "Removing", s3nKey
            # removeKeyResult = h2o.nodes[0].remove_key(key=s3nKey)


if __name__ == '__main__':
    h2o.unit_main()
