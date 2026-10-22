const fs = require('node:fs');
const path = require('node:path');
const { createHash } = require('node:crypto');

function releaseVersion(runNumber) {
  const run = Number(runNumber);
  if (!/^[1-9]\d*$/.test(String(runNumber)) || !Number.isSafeInteger(run) ||
      run >= 2_100_000_000) {
    throw new Error('Invalid workflow run number for Android versionCode');
  }
  return { version: `0.1.${run}`, tag: `v0.1.${run}`, versionCode: run + 1 };
}

async function optional(get) {
  try {
    return (await get()).data;
  } catch (error) {
    if (error.status === 404) return null;
    throw error;
  }
}

async function publishRelease({ github, context, core, artifactDir }) {
  const { version, tag } = releaseVersion(context.runNumber);
  const repo = context.repo;
  const apkName = `BareShell-${version}.apk`;
  const apk = fs.readFileSync(path.join(artifactDir, apkName));
  const checksumName = `${apkName}.sha256`;
  const checksum = fs.readFileSync(path.join(artifactDir, checksumName));
  const digest = createHash('sha256').update(apk).digest('hex');
  if (apk.length === 0 || checksum.toString() !== `${digest}  ${apkName}\n`) {
    throw new Error('Release APK is empty or its SHA-256 checksum does not match');
  }

  let ref = await optional(() => github.rest.git.getRef({ ...repo, ref: `tags/${tag}` }));
  if (!ref) {
    ref = (await github.rest.git.createRef({
      ...repo, ref: `refs/tags/${tag}`, sha: context.sha,
    })).data;
  }
  let object = ref.object;
  while (object.type === 'tag') {
    object = (await github.rest.git.getTag({ ...repo, tag_sha: object.sha })).data.object;
  }
  if (object.type !== 'commit' || object.sha !== context.sha) {
    throw new Error(`${tag} does not point to the triggering commit`);
  }

  // List releases to include incomplete drafts, which can be absent from tag lookups.
  const releases = await github.paginate(github.rest.repos.listReleases, {
    ...repo, per_page: 100,
  });
  let release = releases.find(item => item.tag_name === tag);
  if (release && !release.draft) {
    const complete = [apkName, checksumName].every(name =>
      release.assets.some(asset => asset.name === name && asset.state === 'uploaded' &&
        asset.size > 0));
    if (!complete) throw new Error(`Published ${tag} is missing required assets`);
    core.info(`${tag} is already published; leaving it unchanged`);
    return;
  }
  if (!release) {
    release = (await github.rest.repos.createRelease({
      ...repo, tag_name: tag, target_commitish: context.sha,
      name: `BareShell ${version}`, draft: true, prerelease: false,
      generate_release_notes: true,
    })).data;
  }

  for (const [name, data, contentType] of [
    [apkName, apk, 'application/vnd.android.package-archive'],
    [checksumName, checksum, 'text/plain'],
  ]) {
    const existing = release.assets.find(asset => asset.name === name);
    if (existing) {
      await github.rest.repos.deleteReleaseAsset({ ...repo, asset_id: existing.id });
    }
    const uploaded = (await github.rest.repos.uploadReleaseAsset({
      ...repo, release_id: release.id, name, data,
      headers: { 'content-type': contentType, 'content-length': data.length },
    })).data;
    if (uploaded.name !== name || uploaded.state !== 'uploaded' || uploaded.size !== data.length) {
      throw new Error(`Release asset upload is incomplete: ${name}`);
    }
  }
  // Let GitHub select latest by semantic version, not this job's completion order.
  await github.rest.repos.updateRelease({
    ...repo, release_id: release.id, draft: false, make_latest: 'legacy',
  });
  core.info(`Published ${tag}`);
}

module.exports = { releaseVersion, publishRelease };
