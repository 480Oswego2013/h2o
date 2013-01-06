import unittest, sys
sys.path.extend(['.','..','py'])

import h2o, h2o_cmd

class TestPoll(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        h2o.build_cloud(node_count=1)

    @classmethod
    def tearDownClass(cls):
        h2o.tear_down_cloud()

    def test_redirect_poll_loop(self):
        n = h2o.nodes[0]
        for i in range(3):
            redir = n.test_redirect()['response']
            self.assertEqual(redir['status'], 'redirect')
            self.assertEqual(redir['redirect_request'], 'TestPoll')
            args = redir['redirect_request_args']
            status = 'poll'
            i = 0
            while status == 'poll':
                status = n.test_poll(args)['response']['status']
                i += 1
                if i > 100: self.fail('polling took too many iterations')
            self.assertEqual(status, 'done')
            


if __name__ == '__main__':
    h2o.unit_main()
