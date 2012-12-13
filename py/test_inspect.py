import os, json, unittest, time, shutil, sys
import h2o, h2o_cmd
import itertools

def file_to_put():
    return 'smalldata/poker/poker1000'

def crange(start, end):
    for c in xrange(ord(start), ord(end)):
        yield chr(c)

# Dummy wc -l
def wcl(filename):
        lines = 0
        f = open(filename)
        for line in f:
            lines += 1
        
        f.close()
        return lines

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=4)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_A_inspect_poker1000(self):
        cvsfile = h2o.find_file("smalldata/poker/poker1000")
        node    = h2o.nodes[0]
        
        res  = self.putfile_and_parse(node, cvsfile)
        ary  = node.inspect(res['Key'])
        # count lines in input file - there is no header for poker 1000
        rows = wcl(cvsfile)

        self.assertEqual(rows, ary['rows'])
        self.assertEqual(11, ary['cols'])

    def test_B_inspect_column_names_multi_space_sep(self):
        self.inspect_columns("smalldata/test/test_26cols_multi_space_sep.csv")

    def test_C_inspect_column_names_single_space_sep(self):
        self.inspect_columns("smalldata/test/test_26cols_single_space_sep.csv")

    def test_D_inspect_column_names_comma_sep(self):
        self.inspect_columns("smalldata/test/test_26cols_comma_sep.csv")

    def test_E_inspect_column_names_comma_sep(self):
        self.inspect_columns("smalldata/test/test_26cols_single_space_sep_2.csv")

    # FIX! are we never going to support this?
    def nottest_F_more_than_65535_unique_names_in_column(self):
        self.inspect_columns("smalldata/test/test_more_than_65535_unique_names.csv", rows=66001, cols=3, columnNames=['X','Y','Z'],columnTypes=['float','int','float'])

    def test_FF_less_than_65535_unique_names_in_column(self):
        self.inspect_columns("smalldata/test/test_less_than_65535_unique_names.csv", rows=65533, cols=3, columnNames=['X','Y','Z'],columnTypes=['enum','int','float'])

    def test_G_all_raw_top10rows(self):
        self.inspect_columns("smalldata/test/test_all_raw_top10rows.csv", rows=12, cols=89, 
            columnNames=['Randm','Month','applicationid','zip','sex','Day_Week','TimeofDay','WebApp','entereddate','age','AnsweredSurvey','Srvy_Plan2DD','Srvy_bythngs_online','Has_bnk_AC','PlasticTypeID','FeePlanID','clientkey','PlasticType','PlanType','Activated','OnDD','Verified','RegisteredOnline','Channel','Appid','Population','HouseholdsPerZipCode','WhitePopulation','BlackPopulation','HispanicPopulation','AsianPopulation','HawaiianPopulation','IndianPopulation','OtherPopulation','MalePopulation','FemalePopulation','PersonsPerHousehold','AverageHouseValue','IncomePerHousehold','MedianAge','MedianAgeMale','MedianAgeFemale','Elevation','CityType','TimeZone','DayLightSaving','MSA','PMSA','CSA','CBSA','CBSA_Div','NumberOfBusinesses','NumberOfEmployees','BusinessFirstQuarterPayroll','BusinessAnnualPayroll','GrowthRank','GrowthHousingUnits2003','GrowthHousingUnits2004','GrowthIncreaseNumber','GrowthIncreasePercentage','CBSAPop2003','CBSADivPop2003','DeliveryResidential','DeliveryBusiness','DeliveryTotal','PopulationEstimate','LandArea','WaterArea','id','Experian_pass','Innovis_pass','TU_pass','Choicepoint_pass','LN_pass','Experian_Cx','Innovis_Cx','TU_Cx','Choicepoint_Cx','LN_Cx','checkpointscore','levelonedecisioncode','grade','white_percent','black_percent','hispanic_percent','male_percent','female_percent','region','division' ])

    
    def test_H_domains_and_column_names(self):
        cinsp = self.inspect_columns("smalldata/test/test_domains_and_column_names.csv", rows=4, cols=3, columnNames=['A1', 'A2', 'A3'], columnTypes=['int','int','enum'])
        # check domain of 3rd column - it should contains values 'one,two,three,four' 
        # but it must not contain the name of 3rd column ('A3')
        # H2O can return the lists in any order. This should check that there are 4 in the matching set?
        intersect = set.intersection( 
            set(cinsp['columns'][2]['enumdomain']),  
            set(['one', 'two', 'three', 'four']))
        self.assertEqual(4,len(intersect))

    # Shared test implementation for smalldata/test/test_26cols_*.csv
    def inspect_columns(self, filename, rows=1, cols=26, columnNames=crange('A', 'Z'), columnTypes=None):
        cvsfile = h2o.find_file(filename)
        node    = h2o.nodes[0]
        
        res  = self.putfile_and_parse(node, cvsfile)
        ary  = node.inspect(res['Key'])

        self.assertEqual(rows, ary['rows'])
        self.assertEqual(cols, ary['cols'])

        # check column names
        if not columnNames is None:
            for (col, expName) in zip(ary['columns'], columnNames):
                self.assertEqual(expName, col['name'])

        # check column types
        if not columnTypes is None:
            for (col, expType) in zip(ary['columns'], columnTypes):
                self.assertEqual(expType, col['type'])

        return ary

    def putfile_and_parse(self, node, f):
        result  = node.put_file(f)
        key     = result['key']
        return node.parse(key);

    
if __name__ == '__main__':
    h2o.unit_main()
