// Tests for scripts/build-all.sh and scripts/deploy.sh. Each test copies the script into a temp
// project and puts fake java/node/npm/aws binaries first on PATH, so nothing real is built or deployed.
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { chmodSync, copyFileSync, mkdirSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { describe, it } from 'node:test';

const SCRIPTS = resolve(import.meta.dirname, '../../scripts');

function fake(binDir: string, name: string, body: string): void {
  const file = join(binDir, name);
  writeFileSync(file, `#!/bin/bash\n${body}\n`);
  chmodSync(file, 0o755);
}

/** Temp project with scripts/<script> copied in and an empty fake bin dir. */
function project(script: string): { root: string; bin: string; log: string } {
  const root = mkdtempSync(join(tmpdir(), 'lunch2-scripts-'));
  mkdirSync(join(root, 'scripts'));
  copyFileSync(join(SCRIPTS, script), join(root, 'scripts', script));
  const bin = join(root, 'fakebin');
  mkdirSync(bin);
  return { root, bin, log: join(root, 'calls.log') };
}

function run(root: string, script: string, bin: string, env: Record<string, string> = {}) {
  const result = spawnSync('/bin/bash', [join(root, 'scripts', script)], {
    cwd: root,
    encoding: 'utf8',
    env: { PATH: `${bin}:/usr/bin:/bin`, HOME: root, ...env },
  });
  return { code: result.status, out: `${result.stdout}${result.stderr}` };
}

function fakeJava(bin: string, version: string): void {
  fake(bin, 'java', `echo 'openjdk version "${version}" 2025-01-21' >&2`);
}

function fakeNode(bin: string, major: number): void {
  fake(bin, 'node', `[ "$1" = "-v" ] && echo v${major}.0.0 || echo ${major}`);
}

describe('build-all.sh', () => {
  it('stops with an install hint when no JDK 21+ exists', () => {
    const { root, bin } = project('build-all.sh');
    fakeJava(bin, '17.0.15');
    fakeNode(bin, 20);
    fake(bin, 'java_home', 'exit 1');
    const r = run(root, 'build-all.sh', bin, { JAVA_HOME_TOOL: join(bin, 'java_home') });
    assert.equal(r.code, 1);
    assert.match(r.out, /JDK 21\+ required \(found: openjdk version "17\.0\.15"/);
    assert.match(r.out, /brew install --cask temurin@21/);
  });

  it('switches to a JDK 21+ from java_home when PATH has JDK 17', () => {
    const { root, bin } = project('build-all.sh');
    fakeJava(bin, '17.0.15');
    fakeNode(bin, 20);
    const jdk24 = join(root, 'jdk24');
    mkdirSync(join(jdk24, 'bin'), { recursive: true });
    fakeJava(join(jdk24, 'bin'), '24');
    fake(bin, 'java_home', `[ "$1 $2" = "-v 21+" ] && echo ${jdk24}`);
    mkdirSync(join(root, 'frontend'));
    fake(bin, 'npm', 'exit 7'); // stop right after the checks
    const r = run(root, 'build-all.sh', bin, { JAVA_HOME_TOOL: join(bin, 'java_home') });
    assert.match(r.out, /==> Using openjdk version "24"/);
    assert.equal(r.code, 7);
  });

  it('rejects Node older than 20', () => {
    const { root, bin } = project('build-all.sh');
    fakeJava(bin, '21.0.6');
    fakeNode(bin, 18);
    const r = run(root, 'build-all.sh', bin);
    assert.equal(r.code, 1);
    assert.match(r.out, /Node 20\+ required \(found v18\.0\.0\)/);
  });

  it('does a clean Maven build after the frontend build', () => {
    const { root, bin, log } = project('build-all.sh');
    fakeJava(bin, '21.0.6');
    fakeNode(bin, 20);
    fake(bin, 'npm', `echo "npm $*" >> ${log}; [ "$1" = run ] && mkdir -p dist && echo '<html>' > dist/index.html; exit 0`);
    mkdirSync(join(root, 'frontend'));
    mkdirSync(join(root, 'backend'));
    fake(join(root, 'backend'), 'mvnw', `echo "mvnw $*" >> ${log}`);
    const r = run(root, 'build-all.sh', bin);
    assert.equal(r.code, 0, r.out);
    const calls = readFileSync(log, 'utf8');
    assert.match(calls, /npm ci --silent\nnpm run build\nmvnw -B -q clean package -DskipTests/);
    const spa = readFileSync(join(root, 'backend/src/main/resources/META-INF/resources/index.html'), 'utf8');
    assert.equal(spa.trim(), '<html>');
  });
});

describe('deploy.sh', () => {
  function deployProject(dirty: boolean) {
    const p = project('deploy.sh');
    fake(p.bin, 'aws', `[ "$1 $2" = "sts get-caller-identity" ] && echo 123456789012 || exit 0`);
    fake(join(p.root, 'scripts'), 'build-all.sh', `echo BUILD_CALLED; exit 9`);
    const git = (...args: string[]) => spawnSync('git', args, { cwd: p.root });
    git('init', '-q');
    writeFileSync(join(p.root, '.gitignore'), 'fakebin/\ncalls.log\n');
    git('add', '-A');
    git('-c', 'user.email=t@t', '-c', 'user.name=t', 'commit', '-q', '-m', 'init');
    if (dirty) writeFileSync(join(p.root, 'half-done.ts'), 'wip');
    return p;
  }

  it('fails clearly when the AWS CLI is missing', () => {
    const { root, bin } = project('deploy.sh');
    const r = run(root, 'deploy.sh', bin);
    assert.equal(r.code, 1);
    assert.match(r.out, /AWS CLI missing: brew install awscli/);
  });

  it('refuses to deploy uncommitted changes', () => {
    const { root, bin } = deployProject(true);
    const r = run(root, 'deploy.sh', bin);
    assert.equal(r.code, 1);
    assert.match(r.out, /Uncommitted changes .* would be deployed/);
    assert.match(r.out, /half-done\.ts/);
    assert.doesNotMatch(r.out, /BUILD_CALLED/);
  });

  it('deploys uncommitted changes only with ALLOW_DIRTY=1', () => {
    const { root, bin } = deployProject(true);
    const r = run(root, 'deploy.sh', bin, { ALLOW_DIRTY: '1' });
    assert.match(r.out, /BUILD_CALLED/);
  });

  it('builds a clean tree and defaults to eu-central-1', () => {
    const { root, bin } = deployProject(false);
    const r = run(root, 'deploy.sh', bin);
    assert.match(r.out, /==> Account 123456789012, region eu-central-1/);
    assert.match(r.out, /BUILD_CALLED/);
    assert.equal(r.code, 9);
  });

  it('honours AWS_REGION', () => {
    const { root, bin } = deployProject(false);
    const r = run(root, 'deploy.sh', bin, { AWS_REGION: 'eu-west-1' });
    assert.match(r.out, /region eu-west-1/);
  });
});
