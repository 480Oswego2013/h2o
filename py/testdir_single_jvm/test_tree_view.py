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

    def test_tree_view(self):
        csvFilename = "poker1000"
        csvPathname = h2o.find_file('smalldata/poker/' + csvFilename)
        # tree view failed with poker1000, passed with iris
        h2o_cmd.runRF(trees=50, csvPathname=csvPathname, key=csvFilename, 
            model_key="model0", timeoutSecs=10)

        for n in range(1):
            # the default model_key  is "model". and we know the dataKey from parseFile will be poker1000.hex
            a = h2o_cmd.runRFTreeView(n=n, 
                dataKey=csvFilename + ".hex", model_key="model0", timeoutSecs=10)
            print (h2o.dump_json(a))

if __name__ == '__main__':
    h2o.unit_main()
