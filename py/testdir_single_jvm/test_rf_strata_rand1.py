import unittest
import random, sys
sys.path.extend(['.','..','py'])
import h2o, h2o_cmd

# make a dict of lists, with some legal choices for each. None means no value.
# assume poker1000 datset

# we can pass ntree thru kwargs if we don't use the "trees" parameter in runRF
# only classes 0-8 in the last col of poker1000

paramDict = {
    'stratify': [None,0,1,1,1,1,1,1,1,1,1],
    'strata': [
        None,
        "0:10",
        "1:5",
        "0:7,2:3", 
        "0:1,1:1,2:1,3:1,4:1,5:1,6:1,7:1,8:1,9:1", 
        "0:100,1:100,2:100,3:100,4:100,5:100,6:100,7:100,8:100,9:100", 
        "0:0,1:0,2:0,3:0,4:0,5:0,6:0,7:0,8:0,9:0",
        ]
    }

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_loop_random_param_poker1000(self):
        csvPathname = h2o.find_file('smalldata/poker/poker1000')

        for trial in range(20):
            # for determinism, I guess we should spit out the seed?
            # random.seed(SEED)
            SEED = random.randint(0, sys.maxint)
            # if you have to force to redo a test
            # SEED = 
            random.seed(SEED)
            print "\nUsing random seed:", SEED
            # form random selections of RF parameters
            kwargs = {}
            randomGroupSize = random.randint(1,len(paramDict))
            print randomGroupSize
            for i in range(randomGroupSize):
                randomKey = random.choice(paramDict.keys())
                randomV = paramDict[randomKey]
                randomValue = random.choice(randomV)
                kwargs[randomKey] = randomValue

            print kwargs
            
            h2o_cmd.runRF(timeoutSecs=20, csvPathname=csvPathname, **kwargs)

            print "Trial #", trial, "completed"

if __name__ == '__main__':
    h2o.unit_main()
