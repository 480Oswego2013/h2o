import unittest
import re, os, shutil
import h2o, h2o_cmd, h2o_hosts

class Basic(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1) 

    @classmethod 
    def tearDownClass(cls): 
        h2o.tear_down_cloud()

    def test_many_parse1(self):
        rows = genrows1()
        set = 1
        trial = tryThemAll(set,0,rows)

    def test_many_parse2(self):
        rows = genrows2()
        set = 2
        trial = tryThemAll(set,0,rows)

    # this one has problems with blank lines
    def test_many_parse3(self):
        rows = genrows3()
        set = 3
        trial = tryThemAll(set,0,rows)

if __name__ == '__main__':
    def genrows1():
        # comment has to have # in first column? (no leading whitespace)
        # FIX! what about blank fields and spaces as sep
        # FIX! temporary need more lines to avoid sample error in H2O
        rows = [
        "# 'comment, is okay",
        '# "this comment, is okay too',
        "# 'this' comment, is okay too",
        "FirstName|MiddleInitials|LastName|DateofBirth",
        "0|0.5|1|2",
        "3|NaN|4|5",
        "6||8|9",
        "6|7|8|9",
        "6|7|8|9",
        "6|7|8|9",
        "6|7|8|9",
        "6|7|8|9",
        "6|7|8|9",
        "6|7|8|9",
        "6|7|8|9"
        ]
        return rows
    
    #     "# comment here is okay",
    #     "# comment here is okay too",
    # FIX! needed an extra line to avoid bug on default 67+ sample?
    def genrows2():
        rows = [
        "FirstName|MiddleInitials|LastName|DateofBirth",
        "Kalyn|A.|Dalton|1967-04-01",
        "Gwendolyn|B.|Burton|1947-10-26",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31"
        ]
        return rows
    
    # update spec
    # intermixing blank lines in the first two lines breaks things
    # blank lines cause all columns except the first to get NA (red)
    # first may get a blank string? (not ignored)
    def genrows3():
        rows = [
        "# comment here is okay",
        "# comment here is okay too",
        "FirstName|MiddleInitials|LastName|DateofBirth",
        "Kalyn|A.|Dalton|1967-04-01",
        "",
        "Gwendolyn||Burton|1947-10-26",
        "",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        "Elodia|G.|Ali|1983-10-31",
        ]
        return rows

    # The 3 supported line-ends
    # FIX! should test them within quoted tokens
    eolDict = {
        0:"\n",
        1:"\r\n",
        2:"\r"
        }
    
    # tab here will cause problems too?
    #    5:['"\t','\t"'],
    #    8:["'\t","\t'"]
    tokenChangeDict = {
        0:['',''],
        1:['\t','\t'],
        2:[' ',' '],
        3:['"','"'],
        4:['" ',' "'],
        5:["'","'"],
        6:["' "," '"],
        }
    
    def changeTokens(rows,tokenCase):
        [cOpen,cClose] = tokenChangeDict[tokenCase]
        newRows = []
        for r in rows:
            # don't quote lines that start with #
            # can quote lines start with some spaces or tabs? maybe
            comment = re.match(r'^[ \t]*#', r)
            empty = re.match(r'^$',r)
            if not (comment or empty):
                r = re.sub('^',cOpen,r)
                r = re.sub('\|',cClose + '|' + cOpen,r)
                r = re.sub('$',cClose,r)
            h2o.verboseprint(r)
            newRows.append(r)
        return newRows
    
    
    def writeRows(csvPathname,rows,eol):
        f = open(csvPathname, 'w')
        for r in rows:
            f.write(r + eol)
        # what about case of missing eoll at end of file?
    
    sepChangeDict = {
        0:",",
        1:" ",
        2:"\t"
        }
    
    def changeSep(rows,sepCase):
        # do a trial replace, to see if we get a <tab><sp> problem
        # comments at the beginning..get a good row
        r = rows[-1]
        tabseptab = re.search(r'\t|\t', r)
        spsepsp  = re.search(r' | ', r)

        if tabseptab or spsepsp:
            # use comma instead. always works
            # print "Avoided"
            newSep = ","
        else:
            newSep = sepChangeDict[sepCase]

        newRows = [r.replace('|',newSep) for r in rows]
        return newRows
    
    
    SYNDATASETS_DIR = './syn_datasets'
    
    def tryThemAll(set,trial,rows):
        for eolCase in range(len(eolDict)):
            eol = eolDict[eolCase]
            # change tokens must be first
            for tokenCase in range(len(tokenChangeDict)):
                newRows1 = changeTokens(rows,tokenCase)
                for sepCase in range(len(sepChangeDict)):
                    newRows2 = changeSep(newRows1,sepCase)
                    csvPathname = SYNDATASETS_DIR + '/parsetmp_' + \
                        str(set) + "_" + \
                        str(eolCase) + "_" + \
                        str(tokenCase) + "_" + \
                        str(sepCase) + \
                        '.data'
                    writeRows(csvPathname,newRows2,eol)
                    h2o_cmd.runRF(trees=1, timeoutSecs=10, csvPathname=csvPathname)
    
                    trial += 1
                    print "Set", set, "Trial #", trial
        return trial
    
    if os.path.exists(SYNDATASETS_DIR):
        shutil.rmtree(SYNDATASETS_DIR)
    os.mkdir(SYNDATASETS_DIR)

    h2o.unit_main()