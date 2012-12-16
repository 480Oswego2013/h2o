import sys
sys.path.extend(['.','..','py'])

import unittest, h2o, h2o_cmd

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_parity_128_4_2_quad(self):
        h2o_cmd.runRF(None, h2o.find_file('smalldata/parity_128_4_2_quad.data'), trees=6, 
            timeoutSecs=5)

if __name__ == '__main__':
    h2o.unit_main()
