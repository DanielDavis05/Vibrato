#!/usr/bin/env python3
"""Isolated, offline singleplayer campaign supervisor. Never manages foreign processes."""
from __future__ import annotations
import argparse, collections, datetime, hashlib, json, math, os, re, signal, subprocess, time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_HOME = Path.home() / '.vibrato-tests'
TASK = re.compile(r'Task START: (.*)')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def classify(*, death=False, crash=False, failure=False, stuck=False, churn=False,
             stall=False, timeout=False, pass_goal=False, overloaded=False):
    # Hardware overload invalidates timing-based results, never hides a crash or death.
    if death: return 'fail:death'
    if crash: return 'fail:crash'
    if overloaded: return 'invalid:overloaded'
    if stuck: return 'fail:stuck-abandon'
    if failure: return 'fail:task-failure'
    if churn: return 'fail:churn'
    if stall: return 'fail:stall'
    if pass_goal: return 'pass'
    if timeout: return 'fail:timeout'
    return 'invalid:incomplete'


class Evidence:
    def __init__(self, now):
        self.last_progress = now
        self.last_pos = None
        self.task_starts = collections.deque(maxlen=50)
        self.task_start_count = 0
        self.recent = collections.deque()
        self.churn = False
        self.failure = False
        self.stuck = False
        self.tps = []
        self.last_heartbeat = None
        self.start = None

    def lines(self, lines, now):
        for line in lines:
            match = TASK.search(line)
            if match:
                name = match.group(1).strip()
                self.task_starts.append(name)
                self.task_start_count += 1
                self.last_progress = now
                # Count only starts corroborated as the current leaf. Parent tasks
                # can restart normally during higher-priority interruptions.
                if self.last_heartbeat and self.last_heartbeat.get('leaf') == name:
                    self.recent.append((now, name))
                    while self.recent and now - self.recent[0][0] > 10: self.recent.popleft()
                    if sum(1 for _, task in self.recent if task == name) > 8: self.churn = True
            if 'Task failed:' in line: self.failure = True
            if 'ABANDON_TASK' in line: self.stuck = True

    def heartbeat(self, data, now):
        if data == self.last_heartbeat: return
        self.last_heartbeat = data
        if self.start is None:
            self.start = now
            self.last_progress = now
        pos = data.get('position')
        if isinstance(pos, list) and len(pos) == 3:
            if self.last_pos is None or math.dist(pos, self.last_pos) >= 0.25:
                self.last_pos = pos
                self.last_progress = now
        tps = data.get('tps')
        if isinstance(tps, (int, float)) and math.isfinite(tps): self.tps.append(tps)

    def overloaded(self):
        return any(sum(self.tps[i:i + 10]) / 10 < 18 for i in range(len(self.tps) - 9))


def terminate(proc):
    if proc.poll() is not None: return
    try: os.killpg(proc.pid, signal.SIGTERM)
    except ProcessLookupError: return
    try: proc.wait(timeout=10)
    except subprocess.TimeoutExpired:
        try: os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError: pass
        proc.wait(timeout=10)


def read_json(path):
    try: return json.loads(path.read_text())
    except (FileNotFoundError, json.JSONDecodeError): return None


def proc_stats(pid):
    try:
        stat = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
        rss = int(Path(f'/proc/{pid}/statm').read_text().split()[1]) * os.sysconf('SC_PAGE_SIZE')
        return (int(stat[11]) + int(stat[12])) / os.sysconf('SC_CLK_TCK'), rss
    except (OSError, ValueError, IndexError): return None


def run(args):
    import minecraft_launcher_lib as mll
    fleet = Path(args.install).resolve()
    mc, mods = fleet / 'minecraft', fleet / 'mods'
    jars = list(mods.glob('vibrato-*.jar'))
    if len(jars) != 1: raise SystemExit('Need exactly one packaged Vibrato jar in install mods')
    jar = jars[0]
    jar_hash = digest(jar)
    sha = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip():
        raise SystemExit('Refusing to run from a dirty worktree: commit harness first')
    base = (DEFAULT_HOME / args.campaign).resolve()
    base.mkdir(parents=True, exist_ok=True)
    seeds = json.loads((ROOT / 'scripts/campaign/seeds.json').read_text())['seeds']
    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text().splitlines()
                      if '=' in line and not line.lstrip().startswith('#'))
    version = f"fabric-loader-{properties['loader_version'].strip()}-{properties['minecraft_version'].strip()}"
    running = []
    try:
        for i in range(args.count):
            run_id = f'{args.label}-{i+1:02d}'
            path = base / run_id
            path.mkdir(exist_ok=False)
            (path / 'mods').symlink_to(mods, target_is_directory=True)
            (path / 'options.txt').write_text('onboardAccessibility:false\nrenderDistance:5\nsimulationDistance:5\nmaxFps:30\nenableVsync:false\nsoundCategory_master:0.0\npauseOnLostFocus:false\n')
            script = path / 'campaign-script.txt'
            script.write_text('0 @gamer\n0 wait-idle ' + str(args.budget) + '\n0 exit\n')
            seed = seeds[i % len(seeds)]
            bot = f'Campaign{i+1:02d}'
            jvm = ['-Xmx1536M', f'-Dvibrato.botId={bot}', f'-Dvibrato.seed={seed}',
                   f'-Dvibrato.world=world-{i+1:02d}', f'-Dvibrato.worldSeed={seed}',
                   '-Dvibrato.autostart=false', '-Dvibrato.perf=low', '-Dvibrato.campaignTelemetry=true', f'-Dvibrato.script={script}']
            options = {'username': bot, 'uuid': hashlib.md5(('OfflinePlayer:' + bot).encode()).hexdigest(),
                       'token':'0', 'gameDirectory':str(path), 'executablePath':'java', 'jvmArguments':jvm,
                       'customResolution':True, 'resolutionWidth':'640', 'resolutionHeight':'360'}
            cmd = mll.command.get_minecraft_command(version, str(mc), options)
            output = (path / 'client.log').open('w')
            env = {**os.environ, 'DISPLAY':args.display, 'LIBGL_ALWAYS_SOFTWARE':'1', 'ALSOFT_DRIVERS':'null'}
            process = subprocess.Popen(['nice', '-n', '10', *cmd], cwd=path, env=env,
                                       stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
            metadata = {'schema_version':1, 'run_id':run_id, 'git_sha':sha, 'jar_sha256':jar_hash,
                        'world_seed':seed, 'profile_seed':seed, 'bot_id':bot, 'stage':'full-to-nether',
                        'budget_seconds':args.budget, 'display':args.display, 'pid':process.pid,
                        'start_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
                        'goal':'enter minecraft:the_nether', 'control':'default'}
            (path / 'metadata.json').write_text(json.dumps(metadata, indent=2) + '\n')
            running.append({'proc':process, 'dir':path, 'out':output, 'metadata':metadata,
                            'evidence':Evidence(time.monotonic()), 'offset':0, 'started':time.monotonic(),
                            'peak_rss':0, 'cpu':None, 'cause':None})
            print(f'{run_id} started pid={process.pid}', flush=True)
            if i + 1 < args.count: time.sleep(args.stagger)
        while running:
            now = time.monotonic()
            for r in list(running):
                proc, path, evidence = r['proc'], r['dir'], r['evidence']
                with (path / 'client.log').open(errors='replace') as log:
                    log.seek(r['offset']); lines = log.readlines(); r['offset'] = log.tell()
                evidence.lines(lines, now)
                hb = read_json(path / 'campaign-heartbeat.json')
                if hb: evidence.heartbeat(hb, now)
                stats = proc_stats(proc.pid)
                if stats:
                    r['cpu'], rss = stats
                    r['peak_rss'] = max(r['peak_rss'], rss)
                goal = bool(hb and hb.get('dimension') == 'minecraft:the_nether')
                death = bool(hb and (hb.get('dead') or hb.get('health', 1) <= 0))
                stall = evidence.start is not None and now - evidence.last_progress >= 60
                timeout = (evidence.start is not None and now - evidence.start >= args.budget) or now - r['started'] >= args.startup_timeout + args.budget
                cause = ('death' if death else 'pass' if goal else 'stuck' if evidence.stuck else
                         'failure' if evidence.failure else 'churn' if evidence.churn else 'stall' if stall else
                         'timeout' if timeout else None)
                exited = proc.poll() is not None
                if cause or exited:
                    if cause and not exited: terminate(proc)
                    r['out'].close()
                    result = read_json(path / 'script-result.json')
                    crash = exited and proc.returncode != 0
                    status = classify(death=death, crash=crash, failure=evidence.failure, stuck=evidence.stuck,
                                      churn=evidence.churn, stall=stall, timeout=timeout, pass_goal=goal,
                                      overloaded=evidence.overloaded())
                    if len(evidence.tps) < 10 and not crash and not death: status = 'invalid:insufficient-tps'
                    summary = {**r['metadata'], 'classification':status, 'watchdog_cause':cause,
                               'elapsed_seconds':now-r['started'], 'exit_code':proc.returncode,
                               'last_heartbeat':hb, 'last_50_task_starts':list(evidence.task_starts),
                               'task_starts_observed':evidence.task_start_count, 'task_failure_observed':evidence.failure,
                               'stuck_abandon_observed':evidence.stuck, 'churn_observed':evidence.churn,
                               'client_tps_mean':sum(evidence.tps)/len(evidence.tps) if evidence.tps else None,
                               'client_tps_samples':len(evidence.tps), 'peak_rss_bytes':r['peak_rss'],
                               'cpu_seconds':r['cpu'], 'cpu_percent_one_core': (r['cpu'] * 100 / (now-r['started'])) if r['cpu'] is not None else None, 'script_result':result,
                               'jar_unchanged':digest(jar) == jar_hash}
                    (path / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
                    print(f'{r["metadata"]["run_id"]} {status}', flush=True)
                    running.remove(r)
            if running: time.sleep(1)
    finally:
        for r in running:
            terminate(r['proc']); r['out'].close()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--install', required=True, help='fleet install root with minecraft/ and mods/')
    p.add_argument('--campaign', required=True, choices=['round0'])
    p.add_argument('--label', required=True, choices=['single', 'single-verified', 'four', 'six'])
    p.add_argument('--count', type=int, required=True, choices=[1, 4, 6])
    p.add_argument('--budget', type=int, default=60)
    p.add_argument('--startup-timeout', type=int, default=180)
    p.add_argument('--stagger', type=float, default=5)
    p.add_argument('--display', default=':100')
    args = p.parse_args()
    if args.budget < 10 or args.stagger < 0: p.error('budget must be >=10 and stagger >=0')
    def stop_signal(_signal, _frame):
        raise KeyboardInterrupt
    signal.signal(signal.SIGTERM, stop_signal)
    run(args)

if __name__ == '__main__': main()
