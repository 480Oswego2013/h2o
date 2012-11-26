import os, json, unittest, time, shutil, sys
import h2o

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        print "1 node"
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test1(self):
        for x in xrange (1,2000,1):
            if ((x % 100) == 0):
                sys.stdout.write('.')
                sys.stdout.flush()

            trialString = "Trial" + str(x)
            trialStringXYZ = "Trial" + str(x) + "XYZ"
            put = h2o.nodes[0].put_value(trialString, key=trialStringXYZ, repl=None)

if __name__ == '__main__':
    h2o.unit_main()
