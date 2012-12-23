import unittest
import random, sys, time
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_hosts, h2o_browse as h2b, h2o_import as h2i

# the shared exec expression creator and executor
import h2o_exec

zeroList = [
        ['Result0 = 0'],
]

# the first column should use this
exprList = [
        ['Result','<n>',' = sum(','<keyX>','[', '<col1>', '])'],
    ]

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # random.seed(SEED)
        SEED = random.randint(0, sys.maxint)
        # if you have to force to redo a test
        # SEED = 
        random.seed(SEED)
        print "\nUsing random seed:", SEED
        h2o_hosts.build_cloud_with_hosts()

    @classmethod
    def tearDownClass(cls):
        # wait while I inspect things
        # time.sleep(1500)
        h2o.tear_down_cloud()

    def test_sum_import_hosts(self):
        # just do the import folder once
        # importFolderPath = "/home/hduser/hdfs_datasets"
        importFolderPath = "/home/0xdiag/datasets"
        h2i.setupImportFolder(None, importFolderPath)

        # make the timeout variable per dataset. it can be 10 secs for covtype 20x (col key creation)
        # so probably 10x that for covtype200
        if 1==0:
            csvFilenameAll = [
                ("covtype.data", "cA", 5,  1),
                ("covtype.data", "cB", 5,  1),
                ("covtype.data", "cC", 5,  1),
            ]
        else:
            csvFilenameAll = [
                ("covtype.data", "cA", 5,  1),
                ("covtype20x.data", "cD", 50, 20),
                ("covtype200x.data", "cE", 50, 200),
            ]

        ### csvFilenameList = random.sample(csvFilenameAll,1)
        csvFilenameList = csvFilenameAll
        h2b.browseTheCloud()
        lenNodes = len(h2o.nodes)

        firstDone = False
        for (csvFilename, key2, timeoutSecs, resultMult) in csvFilenameList:
            # creates csvFilename.hex from file in importFolder dir 
            parseKey = h2i.parseImportFolderFile(None, csvFilename, importFolderPath, 
                key2=key2, timeoutSecs=2000)
            print csvFilename, 'parse TimeMS:', parseKey['TimeMS']
            print "Parse result['Key']:", parseKey['Key']

            # We should be able to see the parse result?
            inspect = h2o_cmd.runInspect(None, parseKey['Key'])

            print "\n" + csvFilename
            h2o_exec.exec_zero_list(zeroList)
            colResultList = h2o_exec.exec_expr_list_across_cols(lenNodes, exprList, key2, maxCol=54, 
                timeoutSecs=timeoutSecs)
            print "\ncolResultList", colResultList

            if not firstDone:
                colResultList0 = list(colResultList)
                good = [float(x) for x in colResultList0] 
                firstDone = True
            else:
                print "\n", colResultList0, "\n", colResultList
                # create the expected answer...i.e. N * first
                compare = [float(x)/resultMult for x in colResultList] 
                print "\n", good, "\n", compare
                self.assertEqual(good, compare, 'compare is not equal to good (first try * resultMult)')
        

if __name__ == '__main__':
    h2o.unit_main()
