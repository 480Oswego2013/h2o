import unittest, time, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd, h2o_glm

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_GLM_umass(self):
        # filename, y, timeoutSecs
        # this hangs during parse for some reason
        csvFilenameList = [
            ('clslowbwt.dat', 7, 5),
            ]

        trial = 0
        for (csvFilename, y, timeoutSecs) in csvFilenameList:
            csvPathname = h2o.find_file("smalldata/logreg/umass_statdata/" + csvFilename)
            print "\n" + csvPathname
            kwargs = {'xval': 0, 'case': 'NaN', 'family': 'binomial', 'link': 'familyDefault', 'y': y}
            start = time.time()
            glm = h2o_cmd.runGLM(csvPathname=csvPathname, key=csvFilename, timeoutSecs=timeoutSecs, **kwargs)
            h2o_glm.simpleCheckGLM(self, glm, None, **kwargs)
            print "glm end (w/check) on ", csvPathname, 'took', time.time() - start, 'seconds'
            trial += 1
            print "\nTrial #", trial

if __name__ == '__main__':
    h2o.unit_main()
