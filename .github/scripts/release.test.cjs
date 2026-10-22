const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { createHash } = require('node:crypto');
const { releaseVersion, publishRelease } = require('./release.cjs');

test('versions use run number, not attempt, and enforce Android limits', () => {
  assert.deepEqual(releaseVersion(1), { version: '0.1.1', tag: 'v0.1.1', versionCode: 2 });
  assert.deepEqual(releaseVersion(12), { version: '0.1.12', tag: 'v0.1.12', versionCode: 13 });
  assert.deepEqual(releaseVersion(12), releaseVersion(12));
  assert.equal(releaseVersion(2_099_999_999).versionCode, 2_100_000_000);
  for (const invalid of [0, -1, 1.5, '01', '', 'x', 2_100_000_000]) {
    assert.throws(() => releaseVersion(invalid), /Invalid workflow run number/);
  }
});

function fixture(t, options = {}) {
  const artifactDir = fs.mkdtempSync(path.join(os.tmpdir(), 'bareshell-release-test-'));
  t.after(() => fs.rmSync(artifactDir, { recursive: true }));
  const apkName = 'BareShell-0.1.1.apk';
  const apk = Buffer.from('fixture APK');
  fs.writeFileSync(path.join(artifactDir, apkName), apk);
  fs.writeFileSync(path.join(artifactDir, `${apkName}.sha256`),
    `${createHash('sha256').update(apk).digest('hex')}  ${apkName}\n`);
  const calls = [];
  let ref = options.ref;
  let release = options.release;
  const missing = () => { throw Object.assign(new Error('Not found'), { status: 404 }); };
  const github = {
    paginate: async () => release ? [release] : [],
    rest: {
      git: {
        getRef: async () => {
          if (options.lookupError) throw options.lookupError;
          return ref ? { data: ref } : missing();
        },
        createRef: async args => {
          calls.push(['createRef', args]);
          ref = { object: { type: 'commit', sha: args.sha } };
          return { data: ref };
        },
        getTag: async () => ({ data: { object: { type: 'commit', sha: 'commit-1' } } }),
      },
      repos: {
        listReleases: () => {},
        createRelease: async args => {
          calls.push(['createRelease', args]);
          release = { ...args, id: 42, assets: [] };
          return { data: release };
        },
        deleteReleaseAsset: async args => {
          calls.push(['deleteAsset', args]);
          release.assets = release.assets.filter(asset => asset.id !== args.asset_id);
        },
        uploadReleaseAsset: async args => {
          calls.push(['uploadAsset', args]);
          if (options.failUpload === args.name) throw new Error('Upload failed');
          const asset = {
            id: release.assets.length + 1, name: args.name,
            state: options.incompleteUpload ? 'open' : 'uploaded', size: args.data.length,
          };
          release.assets.push(asset);
          return { data: asset };
        },
        updateRelease: async args => {
          calls.push(['updateRelease', args]);
          Object.assign(release, args);
        },
      },
    },
  };
  const args = {
    github, artifactDir,
    context: { runNumber: 1, sha: 'commit-1', repo: { owner: 'test', repo: 'test' } },
    core: { info() {} },
  };
  return { args, calls, apkName };
}

test('creates exact-commit tag, stages assets, then publishes using semantic latest selection', async t => {
  const { args, calls } = fixture(t);
  await publishRelease(args);
  assert.deepEqual(calls.map(([name]) => name),
    ['createRef', 'createRelease', 'uploadAsset', 'uploadAsset', 'updateRelease']);
  assert.equal(calls[0][1].sha, 'commit-1');
  assert.equal(calls[1][1].draft, true);
  assert.equal(calls[1][1].generate_release_notes, true);
  assert.equal(calls.at(-1)[1].draft, false);
  assert.equal(calls.at(-1)[1].make_latest, 'legacy');
  await publishRelease(args);
  assert.equal(calls.length, 5, 'published rerun must not modify assets or latest');
});

test('resumes a draft and replaces partially uploaded assets', async t => {
  const { args, calls } = fixture(t, {
    ref: { object: { type: 'commit', sha: 'commit-1' } },
    release: {
      id: 42, tag_name: 'v0.1.1', draft: true,
      assets: [{ id: 9, name: 'BareShell-0.1.1.apk', state: 'open', size: 0 }],
    },
  });
  await publishRelease(args);
  assert.deepEqual(calls.map(([name]) => name),
    ['deleteAsset', 'uploadAsset', 'uploadAsset', 'updateRelease']);
});

test('failed second upload leaves a recoverable draft', async t => {
  const { args, calls } = fixture(t, { failUpload: 'BareShell-0.1.1.apk.sha256' });
  await assert.rejects(publishRelease(args), /Upload failed/);
  assert.equal(calls.some(([name]) => name === 'updateRelease'), false);
});

test('incomplete asset responses never publish a draft', async t => {
  const { args, calls } = fixture(t, { incompleteUpload: true });
  await assert.rejects(publishRelease(args), /upload is incomplete/);
  assert.equal(calls.some(([name]) => name === 'updateRelease'), false);
});

test('refuses a conflicting tag', async t => {
  const { args, calls } = fixture(t, { ref: { object: { type: 'commit', sha: 'other' } } });
  await assert.rejects(publishRelease(args), /does not point to the triggering commit/);
  assert.deepEqual(calls, []);
});

test('resolves annotated tags before checking commit identity', async t => {
  const { args } = fixture(t, { ref: { object: { type: 'tag', sha: 'annotation' } } });
  await publishRelease(args);
});

test('does not hide API permission or network errors', async t => {
  const { args, calls } = fixture(t, { lookupError: Object.assign(new Error('Forbidden'), { status: 403 }) });
  await assert.rejects(publishRelease(args), /Forbidden/);
  assert.deepEqual(calls, []);
});

test('rejects an incomplete published release rather than silently succeeding', async t => {
  const { args } = fixture(t, {
    ref: { object: { type: 'commit', sha: 'commit-1' } },
    release: { tag_name: 'v0.1.1', draft: false, assets: [] },
  });
  await assert.rejects(publishRelease(args), /missing required assets/);
});

test('rejects missing assets and incorrect checksums before any API mutation', async t => {
  const { args, calls, apkName } = fixture(t);
  fs.writeFileSync(path.join(args.artifactDir, `${apkName}.sha256`), 'incorrect');
  await assert.rejects(publishRelease(args), /checksum does not match/);
  fs.unlinkSync(path.join(args.artifactDir, apkName));
  await assert.rejects(publishRelease(args), /ENOENT/);
  assert.deepEqual(calls, []);
});
