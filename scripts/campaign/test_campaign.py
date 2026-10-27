import unittest
from supervisor import Evidence, classify
from redact import redact

class CampaignTest(unittest.TestCase):
    def test_precedence_never_hides_death_or_crash(self):
        self.assertEqual('fail:death', classify(death=True, overloaded=True))
        self.assertEqual('fail:crash', classify(crash=True, overloaded=True))
        self.assertEqual('invalid:overloaded', classify(timeout=True, overloaded=True))
        self.assertEqual('fail:timeout', classify(timeout=True))
        self.assertEqual('invalid:incomplete', classify())
        self.assertEqual('pass', classify(pass_goal=True))

    def test_stall_progress_and_tps_window(self):
        e = Evidence(0)
        e.heartbeat({'position':[0, 64, 0], 'leaf':'<Mine>', 'tps':20}, 1)
        e.heartbeat({'position':[0.1, 64, 0], 'leaf':'<Mine>', 'tps':20}, 2)
        self.assertEqual(e.last_progress, 1)
        e.heartbeat({'position':[0.3, 64, 0], 'leaf':'<Mine>', 'tps':20}, 3)
        self.assertEqual(e.last_progress, 3)
        e.tps = [17.0] * 10
        self.assertTrue(e.overloaded())
        e.tps = [20.0] * 9
        self.assertFalse(e.overloaded())

    def test_churn_uses_observed_leaf_not_parent(self):
        e = Evidence(0)
        e.heartbeat({'position':[0, 64, 0], 'leaf':'<Leaf> active', 'leafClass':'LeafTask', 'tps':20}, 1)
        e.lines(['Task START: <Parent>'] * 9, 2)
        self.assertFalse(e.churn)
        e.lines(['Task START: <Leaf>'] * 9, 3)
        self.assertTrue(e.churn)

    def test_alternating_starts_before_debug_state_is_sampled(self):
        e = Evidence(0)
        for n in range(9):
            e.lines(['Task START: <Place target A>', 'Task START: <Go target B>'], n)
        self.assertFalse(e.churn)
        e.heartbeat({'position':[1, 64, 0], 'leaf':'<Place target A> Placing',
                     'leafClass':'PlaceTask', 'tps':20, 'serverTps':19}, 9)
        self.assertTrue(e.churn)
        self.assertEqual(16, e.restart_count)
        self.assertEqual([19], e.server_tps)

    def test_missing_class_does_not_guess_leaf(self):
        e = Evidence(0)
        e.lines(['Task START: <Leaf>'] * 9, 1)
        e.heartbeat({'position':[0,64,0], 'leaf':'<Leaf> Running'}, 2)
        self.assertFalse(e.churn)

    def test_overload_in_either_tick_stream(self):
        e = Evidence(0)
        e.tps = [20] * 10
        e.server_tps = [17] * 10
        self.assertTrue(e.overloaded())

    def test_cumulative_death_survives_alive_respawn_heartbeat(self):
        e = Evidence(0)
        e.heartbeat({'position':[0,64,0], 'health':20, 'dead':False, 'deaths':1}, 1)
        self.assertEqual(1, e.deaths)
        e.heartbeat({'position':[0,64,0], 'health':20, 'dead':False, 'deaths':1, 'tps':20}, 2)
        self.assertEqual(1, e.deaths)

    def test_redaction(self):
        line = 'Player123 /home/alice/private 192.168.1.20 a@b.com token=secret 01234567-89ab-cdef-0123-456789abcdef'
        clean = redact(line)
        for secret in ['Player123', '/home', '192.168.1.20', 'a@b.com', 'secret', '01234567']:
            self.assertNotIn(secret, clean)

if __name__ == '__main__': unittest.main()
