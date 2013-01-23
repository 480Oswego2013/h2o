import unittest
import re, os, shutil, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd

# test some random csv data, and some lineend combinations
class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)
        global SYNDATASETS_DIR
        SYNDATASETS_DIR = h2o.make_syn_dir()

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_A_randomdata2(self):
        print "Using smalldata/datagen1.csv as is"
        csvPathname = h2o.find_file('smalldata/datagen1.csv')
        h2o_cmd.runRF(trees=1, response_variable=2, timeoutSecs=10, csvPathname=csvPathname)

    def test_B_randomdata2_1_lineend(self):
        print "Using smalldata/datagen1.csv to create", SYNDATASETS_DIR, "/datagen1.csv with \r instead" 
        # change lineend, case 1
        csvPathname1 = h2o.find_file('smalldata/datagen1.csv')
        csvPathname2 = SYNDATASETS_DIR + '/datagen1_crlf.csv'
        infile = open(csvPathname1, 'r') 
        outfile = open(csvPathname2,'w') # existing file gets erased

        # assume all the test files are unix lineend. 
        # I guess there shouldn't be any "in-between" ones
        # okay if they change I guess.
        for line in infile.readlines():
            outfile.write(line.strip("\n") + "\r")
        infile.close()
        outfile.close()

        h2o_cmd.runRF(trees=1, response_variable=2, timeoutSecs=10, csvPathname=csvPathname2)


if __name__ == '__main__':
    h2o.unit_main()
