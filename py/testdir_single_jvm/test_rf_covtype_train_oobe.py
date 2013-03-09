import unittest
import random, sys, time
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_rf as h2f

# we can pass ntree thru kwargs if we don't use the "trees" parameter in runRF
# only classes 1-7 in the 55th col
# rng DETERMINISTIC is default
paramDict = {
    # FIX! if there's a header, can you specify column number or column header
    'response_variable': 54,
    'class_weight': None,
    'ntree': 30,
    # 'ntree': 200,
    'model_key': 'model_keyA',
    'out_of_bag_error_estimate': 1,
    # 'gini': 0,
    'gini': 0,
    'depth': 2147483647, 
    # 'bin_limit': 10000,
    'bin_limit': 10000,
    'parallel': 1,
    # FIX! column numbers not supported
    'ignore': "1,2,6,7,8",
    # 'ignore': "A2,A3,A7,A8,A9",
    'sample': 80,
    ## 'seed': 3,
    ## 'features': 30,
    'exclusive_split_limit': 0,
    }

def info_from_inspect(inspect, csvPathname):
    # need more info about this dataset for debug
    cols = inspect['cols']
    # look for nonzero num_missing_values count in each col
    for i, colDict in enumerate(cols):
        num_missing_values = colDict['num_missing_values']
        if num_missing_values != 0:
            print "%s: col: %d, num_missing_values: %d" % (csvPathname, i, num_missing_values)
            pass

    num_cols = inspect['num_cols']
    num_rows = inspect['num_rows']
    row_size = inspect['row_size']
    ptype = inspect['type']
    value_size_bytes = inspect['value_size_bytes']
    response = inspect['response']
    ptime = response['time']

    print "num_cols: %s, num_rows: %s, row_size: %s, ptype: %s, \
           value_size_bytes: %s, time: %s" % \
           (num_cols, num_rows, row_size, ptype, value_size_bytes, ptime)


class Basic(unittest.TestCase):
    def tearDown(self):
        h2o.check_sandbox_for_errors()

    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1, java_heap_GB=14)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_rf_covtype_train_oobe(self):
        if (1==0):
            csvFilename = 'train.csv'
            csvPathname = h2o.find_dataset('bench/covtype/h2o/' + csvFilename)
            print "\nUsing header=1 even though I shouldn't have to. Otherwise I get NA in first row and RF bad\n"
            parseKey = h2o_cmd.parseFile(csvPathname=csvPathname, key2=csvFilename + ".hex", header=1, 
                timeoutSecs=180)
            # FIX! maybe try specifying column header with column name
            ### kwargs['response_variable'] = A55
        else:
            csvFilename = 'covtype.data'
            print "\nUsing header=0 on the normal covtype.data"
            csvPathname = h2o.find_dataset('UCI/UCI-large/covtype/covtype.data')
            parseKey = h2o_cmd.parseFile(csvPathname=csvPathname, key2=csvFilename + ".hex", header=0, 
                timeoutSecs=180)


        inspect = h2o_cmd.runInspect(None, parseKey['destination_key'])
        info_from_inspect(inspect, csvPathname)

        for trial in range(1):
            # params is mutable. This is default.
            kwargs = paramDict
            # adjust timeoutSecs with the number of trees
            # seems ec2 can be really slow
            timeoutSecs = 30 + kwargs['ntree'] * 10
            start = time.time()
            rfView = h2o_cmd.runRFOnly(parseKey=parseKey, timeoutSecs=timeoutSecs, **kwargs)
            elapsed = time.time() - start
            print "RF end on ", csvPathname, 'took', elapsed, 'seconds.', \
                "%d pct. of timeout" % ((elapsed/timeoutSecs) * 100)

            classification_error = rfView['confusion_matrix']['classification_error']
            self.assertGreater(classification_error, 0.01, 
                "train.csv should have out of bag error estimate greater than 0.01")

            print "Trial #", trial, "completed"

if __name__ == '__main__':
    h2o.unit_main()
