import getpass, json, h2o
import random, os
# UPDATE: all multi-machine testing will pass list of IP and base port addresses to H2O
# means we won't realy on h2o self-discovery of cluster

def find_config(base):
    f = base
    if not os.path.exists(f): f = 'testdir_hosts/' + base
    if not os.path.exists(f): f = 'py/testdir_hosts/' + base
    if not os.path.exists(f):
        raise Exception("unable to find config %s" % base)
    return f

# None means the json will specify, or the default for json below
# only these two args override for now. can add more.
def build_cloud_with_hosts(node_count=None, use_flatfile=None, 
    use_hdfs=None, hdfs_name_node="192.168.1.151", 
    java_heap_GB=None, **kwargs):

    # For seeing example of what we want in the json, if we add things
    #   import h2o_config

    # allow user to specify the config json at the command line. config_json is a global.
    # shouldn't need this??
    if h2o.config_json:
        configFilename = find_config(h2o.config_json)
    else:
        # configs may be in the testdir_hosts
        configFilename = find_config('pytest_config-%s.json' %getpass.getuser())

    h2o.verboseprint("Loading host config from", configFilename)
    with open(configFilename, 'rb') as fp:
         hostDict = json.load(fp)

    slow_connection = hostDict.setdefault('slow_connection', False)
    hostList = hostDict.setdefault('ip','127.0.0.1')

    h2oPerHost = hostDict.setdefault('h2o_per_host', 2)
    # default should avoid colliding with sri's demo cloud ports: 54321
    # we get some problems with sticky ports, during back to back tests in regressions
    # to avoid waiting, randomize the port to make it less likely?
    # at least for the hosts case
    offset = random.randint(0,31)
    basePort = hostDict.setdefault('base_port', 55300 + offset)
    username = hostDict.setdefault('username','0xdiag')
    # stupid but here for clarity
    password = hostDict.setdefault('password', None)
    sigar = hostDict.setdefault('sigar', False)

    useFlatfile = hostDict.setdefault('use_flatfile', False)

    useHdfs = hostDict.setdefault('use_hdfs', False)
    hdfs_name_node = hostDict.setdefault('hdfs_name_node', '192.168.1.151')

    # default to none, which means the arg isn't used and java decides for us
    # useful for small dram systems, and for testing that
    javaHeapGB = hostDict.setdefault('java_heap_GB', None)

    use_home_for_ice = hostDict.setdefault('use_home_for_ice', False)

    # can override the json with a caller's argument
    # FIX! and we support passing othe kwargs from above? but they don't override
    # json, ...so have to fix here if that's desired
    if node_count is not None:
        h2oPerHost = node_count

    if use_flatfile is not None:
        useFlatfile = use_flatfile

    if use_hdfs is not None:
        useHdfs = use_hdfs

    if hdfs_name_node is not None:
        hdfsNameNode = hdfs_name_node

    if java_heap_GB is not None:
        javaHeapGB = java_heap_GB

    h2o.verboseprint("host config: ", username, password, 
        h2oPerHost, basePort, sigar, useFlatfile, 
        useHdfs, hdfsNameNode, javaHeapGB, use_home_for_ice,
        hostList, **kwargs)

    #********************
    global hosts
    # Update: special case hostList = ["127.0.0.1"] and use the normal build_cloud
    # this allows all the tests in testdir_host to be run with a special config that points to 127.0.0.1
    # hosts should be None for everyone if normal build_cloud is desired
    if hostList == ["127.0.0.1"]:
        hosts = None
    else:
        h2o.verboseprint("About to RemoteHost, likely bad ip if hangs")
        hosts = []
        for h in hostList:
            h2o.verboseprint("Connecting to:", h)
            hosts.append(h2o.RemoteHost(h, username, password))
   
    # handles hosts=None correctly
    h2o.write_flatfile(node_count=h2oPerHost, base_port=basePort, hosts=hosts)

    if hosts is not None:
        h2o.upload_jar_to_remote_hosts(hosts, slow_connection=slow_connection)
        # timeout wants to be larger for large numbers of hosts * h2oPerHost
        # use 60 sec min, 2 sec per node.
        timeoutSecs = max(60, 2*(len(hosts) * h2oPerHost))
    else: # for 127.0.0.1 case
        timeoutSecs = 60

    # sandbox gets cleaned in build_cloud
    h2o.build_cloud(h2oPerHost,
            base_port=basePort, hosts=hosts, timeoutSecs=timeoutSecs, sigar=sigar, 
            use_flatfile=useFlatfile,
            use_hdfs=useHdfs, hdfs_name_node=hdfsNameNode,
            java_heap_GB=javaHeapGB, use_home_for_ice=use_home_for_ice,
            **kwargs)
