import unittest, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_hex_443(self):
        csvPathname = h2o.find_file('smalldata/hex-443.parsetmp_1_0_0_0.data')
        h2o_cmd.runRF(trees=1, timeoutSecs=5, csvPathname=csvPathname)

if __name__ == '__main__':
    h2o.unit_main()
