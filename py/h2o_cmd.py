import os, json, unittest, time, shutil, sys
import h2o
import h2o_browse as h2b, h2o_rf as h2f

def parseFile(node=None, csvPathname=None, key=None, key2=None, timeoutSecs=20, **kwargs):
    if not csvPathname: raise Exception('No file name specified')
    if not node: node = h2o.nodes[0]
    put = node.put_file(csvPathname, key=key, timeoutSecs=timeoutSecs)
    if key2 is None:
        # don't rely on h2o default key name
        myKey2 = put['key'] + '.hex'
    else:
        myKey2 = key2
    return node.parse(put['key'], myKey2, timeoutSecs=timeoutSecs, **kwargs)

def runInspect(node=None,key=None,timeoutSecs=5,**kwargs):
    if not key: raise Exception('No key for Inspect specified')
    if not node: node = h2o.nodes[0]
    # FIX! currently there is no such thing as a timeout on node.inspect
    return node.inspect(key, **kwargs)

# Not working in H2O yet, but support the test
def runStore2HDFS(node=None,key=None,timeoutSecs=5,**kwargs):
    if not key: raise Exception('No key for Inspect specified')
    if not node: node = h2o.nodes[0]
    # FIX! currently there is no such thing as a timeout on node.inspect
    return node.store2HDFS(key, **kwargs)

# since we'll be doing lots of execs on a parsed file, not useful to have parse+exec
# retryDelaySecs isn't used, 
def runExecOnly(node=None,timeoutSecs=20,**kwargs):
    if not node: node = h2o.nodes[0]
    # no such thing as GLMView..don't use retryDelaySecs
    return node.exec_query(timeoutSecs, **kwargs)

def runGLM(node=None,csvPathname=None,key=None,
        timeoutSecs=20,retryDelaySecs=2,**kwargs):
    # use 1/5th the GLM timeoutSecs for allowed parse time.
    pto = max(timeoutSecs/5,10)
    parseKey = parseFile(node, csvPathname, key, timeoutSecs=pto)
    glm = runGLMOnly(node, parseKey, timeoutSecs, retryDelaySecs,**kwargs)
    return glm

def runGLMOnly(node=None,parseKey=None,
        timeoutSecs=20,retryDelaySecs=2,**kwargs):
    if not parseKey: raise Exception('No parsed key for GLM specified')
    if not node: node = h2o.nodes[0]
    # no such thing as GLMView..don't use retryDelaySecs
    return node.GLM(parseKey['destination_key'], timeoutSecs, **kwargs)

def runGLMGrid(node=None,csvPathname=None,key=None,
        timeoutSecs=60,retryDelaySecs=2,**kwargs):
    # use 1/5th the GLM timeoutSecs for allowed parse time.
    pto = max(timeoutSecs/5,10)
    parseKey = parseFile(node, csvPathname, key, timeoutSecs=pto)
    glm = runGLMGridOnly(node, parseKey, timeoutSecs, retryDelaySecs,**kwargs)
    return glm

def runGLMGridOnly(node=None,parseKey=None,
        timeoutSecs=20,retryDelaySecs=2,**kwargs):
    if not parseKey: raise Exception('No parsed key for GLMGrid specified')
    if not node: node = h2o.nodes[0]
    # no such thing as GLMGridView..don't use retryDelaySecs
    return node.GLMGrid(parseKey['destination_key'], timeoutSecs, **kwargs)

def runLR(node=None, csvPathname=None,key=None,
        timeoutSecs=20, **kwargs):
    # use 1/5th the GLM timeoutSecs for allowed parse time.
    pto = max(timeoutSecs/5,10)
    parseKey = parseFile(node, csvPathname, key, timeoutSecs=pto)
    return runLROnly(node, parseKey, timeoutSecs, **kwargs)

def runLROnly(node=None, parseKey=None, timeoutSecs=20, **kwargs):
    if not parseKey: raise Exception('No parsed key for LR specified')
    if not node: node = h2o.nodes[0]
    # FIX! add something like stabilize in RF to check results, and also retry/timeout
    return node.linear_reg(parseKey['destination_key'], timeoutSecs, **kwargs)

# there are more RF parameters in **kwargs. see h2o.py
def runRF(node=None, csvPathname=None, trees=5, key=None, 
        timeoutSecs=20, retryDelaySecs=2, **kwargs):
    # use 1/10th the GLM timeoutSecs for allowed parse time.
    pto = max(timeoutSecs/10,10)
    parseKey = parseFile(node, csvPathname, key, timeoutSecs=pto)
    return runRFOnly(node, parseKey, trees, timeoutSecs, retryDelaySecs, **kwargs)

def runRFTreeView(node=None, n=None, data_key=None, model_key=None, timeoutSecs=20, **kwargs):
    if not node: node = h2o.nodes[0]
    return node.random_forest_treeview(n, data_key, model_key, timeoutSecs, **kwargs)

# there are more RF parameters in **kwargs. see h2o.py
def runRFOnly(node=None, parseKey=None, trees=5,
        timeoutSecs=20, retryDelaySecs=2, **kwargs):
    if not parseKey: raise Exception('No parsed key for RF specified')
    if not node: node = h2o.nodes[0]
    #! FIX! what else is in parseKey that we should check?
    h2o.verboseprint("runRFOnly parseKey:", parseKey)
    Key = parseKey['destination_key']
    rf = node.random_forest(Key, trees, timeoutSecs, **kwargs)

    # {u'Error': u'Only integer or enum columns can be classes!'}
    # ..this should be covered now by all the error/Error variants in the json result checking?

    # FIX! check all of these somehow?
    # if we model_key was given to rf via **kwargs, remove it, since we're passing 
    # model_key from rf. can't pass it in two places. (ok if it doesn't exist in kwargs)
    data_key  = rf['data_key']
    kwargs.pop('model_key',None)
    kwargs.pop('model_key',None)
    model_key = rf['model_key']
    rfCloud = rf['response']['h2o']

    # same thing. if we use random param generation and have ntree in kwargs, get rid of it.
    kwargs.pop('ntree',None)

    # this is important. it's the only accurate value for how many trees RF was asked for.
    ntree    = rf['ntree']

    # /ip:port of cloud (can't use h2o name)
    rfClass= rf['class']

    def test(n):
        rfView = n.random_forest_view(data_key, model_key, timeoutSecs, **kwargs)
        status = rfView['response']['status']
        numberBuilt = rfView['trees']['number_built']

        if status == 'done': 
            if numberBuilt!=ntree: 
                raise Exception("RFview done but number_built!=ntree: %s %s", 
                    numberBuilt, ntree)
            return True
        if status != 'poll': raise Exception('Unexpected status: ' + status)

        progress = rfView['response']['progress']
        progressTotal = rfView['response']['progress_total']

        # want to double check all this because it's new
        # and we had problems with races/doneness before
        errorInResponse = \
            numberBuilt<0 or ntree<0 or numberBuilt>ntree or \
            progress<0 or progressTotal<0 or progress>progressTotal or \
            progressTotal!=(ntree+1) or \
            ntree!=rfView['ntree']
            # rfView better always agree with what RF ntree was

        if errorInResponse:
            raise Exception("\nBad values in response during RFView polling.\n" + 
                "progress: %s, progressTotal: %s, ntree: %s, numberBuilt: %s, status: %s" % \
                (progress, progressTotal, ntree, numberBuilt, status))

        # don't print the useless first poll. ma
        if (status!='done'):
            if numberBuilt==0:
                print "."
            else:
                print "\nRFView polling. Status: %s. %s trees done of %s desired" % \
                    (status, numberBuilt, ntree)

        return (status=='done')

    node.stabilize(
            test,
            'random forest reporting %d trees' % ntree,
            timeoutSecs=timeoutSecs, retryDelaySecs=retryDelaySecs)

    # kind of wasteful re-read, but maybe good for testing
    rfView = node.random_forest_view(data_key, model_key, timeoutSecs, **kwargs)
    h2f.simpleCheckRFView(node, rfView)

    return rfView
