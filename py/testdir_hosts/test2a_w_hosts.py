import os, json, unittest, time, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_hosts

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o_hosts.build_cloud_with_hosts()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_RFhhp(self):
        csvPathnamegz = h2o.find_file('smalldata/hhp_107_01.data.gz')
        print "RF start on ", csvPathnamegz, "this will probably take a minute.."
        start = time.time()
        h2o_cmd.runRF(csvPathname=csvPathnamegz, trees=23,
                timeoutSecs=120, retryDelaySecs=10)
        print "RF end on ", csvPathnamegz, 'took', time.time() - start, 'seconds'

if __name__ == '__main__':
    h2o.unit_main()
