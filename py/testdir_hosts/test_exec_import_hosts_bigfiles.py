import unittest
import random, sys, time
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_hosts, h2o_browse as h2b, h2o_import as h2i, h2o_exec as h2e

zeroList = [
        'Result0 = 0',
]

exprList = [
        'Result<n> = log(<keyX>[<col1>])',
        'Result<n> = randomBitVector(19,0) + Result<n-1>',
        'Result<n> = randomFilter(<keyX>[<col1>],<row>)',
        'Result<n> = factor(<keyX>[col1])',
        'Result<n> = slice(<keyX>[<col1>,<row>)',
        'Result<n> = colSwap(<keyX>,<col1>,(<keyX>[2]==0 ? 54321 : 54321))',
        'Result<n> = <keyX>[<col1>]',
        'Result<n> = min(<keyX>[<col1>])',
        'Result<n> = max(<keyX>[<col1>]) + Result<n-1>',
        'Result<n> = mean(<keyX>[<col1>]) + Result<n-1>',
        'Result<n> = sum(<keyX>[<col1>]) + Result.hex',
    ]

def exec_list_like_other_tests(exprList, lenNodes, csvFilename, key2):
        h2e.exec_zero_list(zeroList)
        # start with trial = 1 because trial-1 is used to point to Result0 which must be initted
        trial = 1
        while (trial < 100):
            for exprTemplate in exprList:
                # do each expression at a random node, to facilate key movement
                nodeX = random.randint(0,lenNodes-1)
                colX = random.randint(1,54)
                # FIX! should tune this for covtype20x vs 200x vs covtype.data..but for now
                row = str(random.randint(1,400000))

                execExpr = h2e.fill_in_expr_template(exprTemplate, colX, trial, row, key2)
                execResultInspect = h2e.exec_expr(h2o.nodes[nodeX], execExpr, 
                    resultKey="Result"+str(trial))
                ### print "\nexecResult:", execResultInspect

                columns = execResultInspect["cols"]
                columnsDict = columns.pop()
                min = columnsDict["min"]
                h2o.verboseprint("min: ", min, "trial:", trial)
                ### self.assertEqual(float(min), float(trial),"what can we check here")

                ### h2b.browseJsonHistoryAsUrlLastMatch("Inspect")
                # slows things down to check every iteration, but good for isolation
                if (h2o.check_sandbox_for_errors()):
                    raise Exception(
                        "Found errors in sandbox stdout or stderr, on trial #%s." % trial)

                print "Trial #", trial, "completed\n"
                trial += 1

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

    def test_exec_import_hosts(self):
        # just do the import folder once
        # importFolderPath = "/home/hduser/hdfs_datasets"
        importFolderPath = "/home/0xdiag/datasets"
        h2i.setupImportFolder(None, importFolderPath)
        timeoutSecs = 4000

        #    "covtype169x.data",
        #    "covtype.13x.shuffle.data",
        #    "3G_poker_shuffle"
        # Update: need unique key names apparently. can't overwrite prior parse output key?
        # replicating lines means they'll get reparsed. good! (but give new key names)

        csvFilenameList = [
            ("covtype.data", "c"),
            ("covtype20x.data", "c20"),
            ("covtype200x.data", "c200"),
            ("billion_rows.csv.gz", "b"),
            ]

        h2b.browseTheCloud()
        lenNodes = len(h2o.nodes)
        for (csvFilename, key2) in csvFilenameList:
            # creates csvFilename.hex from file in importFolder dir 
            parseKey = h2i.parseImportFolderFile(None, 
                csvFilename, importFolderPath, key2=key2, timeoutSecs=2000)
            print csvFilename, 'parse time:', parseKey['response']['time']
            print "Parse result['destination_key']:", parseKey['destination_key']
            inspect = h2o_cmd.runInspect(None, parseKey['destination_key'])
            print "\n" + csvFilename
            exec_list_like_other_tests(exprList, lenNodes, csvFilename, key2)


if __name__ == '__main__':
    h2o.unit_main()
