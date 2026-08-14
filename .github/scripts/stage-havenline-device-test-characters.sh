#!/usr/bin/env bash
set -euo pipefail

if [ "${BUILD_MODE:-device_test}" != "device_test" ]; then
  echo 'Verified release does not use device-test character staging.'
  exit 0
fi

: "${GITHUB_TOKEN:?GITHUB_TOKEN is required to download pinned review artifacts}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"

stage_root="HAVENLINE_UNITY/Assets/Havenline/Art/Characters/Production"
tmp="${RUNNER_TEMP:-/tmp}/havenline-device-crew"
rm -rf "$tmp"
mkdir -p "$tmp" "$stage_root"

characters=(Character1 Character2 Character3 Character4)
artifact_ids=(8975286960 8975298326 8975329072 8975346289)
fbx_sha=(
  1a94029a0367ead8623296942de8bd516061de39e077ba2dd237bd238bbb5c1b
  803b91c60f94cae7e4c9871f20e5345a5d9d366cb9d28fae9515cdfa8a17b95f
  2a29e90a12cf0ae0905596cd01401443f85705fc115e4cd6d4cbe1f2539000e7
  22e8574c3f2c2ec92353871d271786c29e90d39d15b6dbac50ed22115a4bdabb
)
portrait_sha=(
  e5fd5853e1bbdf58173a4de552c898bdd13b6c489dbd9fba3347f5eb1f9ebbc6
  0bd0f6eb56b87e057230bce7cdbe242ce2b8e67da488429feb51045a49f88f6f
  e3e7fe2ba62e4a199e22b432144c8787463c7b8e52143c0d70ee8aa95cbd179e
  a9076971ea36d3c8a0f5fb019870e3a5531d85d5b1609b01bb0c73de8dec6222
)

for i in "${!characters[@]}"; do
  character="${characters[$i]}"
  artifact="${artifact_ids[$i]}"
  archive="$tmp/${character}.zip"
  extracted="$tmp/${character}"
  mkdir -p "$extracted" "$stage_root/$character"

  echo "Downloading pinned $character review artifact $artifact"
  curl --fail --location --retry 4 --retry-delay 2 \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    "https://api.github.com/repos/$GITHUB_REPOSITORY/actions/artifacts/$artifact/zip" \
    --output "$archive"

  unzip -q "$archive" -d "$extracted"
  cp "$extracted/${character}_production.fbx" "$stage_root/$character/${character}_production.fbx"
  cp "$extracted/proof_front.png" "$stage_root/$character/${character}_portrait.png"

  echo "${fbx_sha[$i]}  $stage_root/$character/${character}_production.fbx" | sha256sum --check --strict
  echo "${portrait_sha[$i]}  $stage_root/$character/${character}_portrait.png" | sha256sum --check --strict
 done

cat > "$stage_root/HAVENLINE_DEVICE_TEST_CHARACTER_STAGE.json" <<'JSON'
{
  "schemaVersion": 1,
  "deviceTestOnly": true,
  "sourceRunId": 31127195233,
  "characters": [
    {
      "character": "Character1",
      "artifactId": 8975286960,
      "fbxSha256": "1a94029a0367ead8623296942de8bd516061de39e077ba2dd237bd238bbb5c1b",
      "portraitSha256": "e5fd5853e1bbdf58173a4de552c898bdd13b6c489dbd9fba3347f5eb1f9ebbc6"
    },
    {
      "character": "Character2",
      "artifactId": 8975298326,
      "fbxSha256": "803b91c60f94cae7e4c9871f20e5345a5d9d366cb9d28fae9515cdfa8a17b95f",
      "portraitSha256": "0bd0f6eb56b87e057230bce7cdbe242ce2b8e67da488429feb51045a49f88f6f"
    },
    {
      "character": "Character3",
      "artifactId": 8975329072,
      "fbxSha256": "2a29e90a12cf0ae0905596cd01401443f85705fc115e4cd6d4cbe1f2539000e7",
      "portraitSha256": "e3e7fe2ba62e4a199e22b432144c8787463c7b8e52143c0d70ee8aa95cbd179e"
    },
    {
      "character": "Character4",
      "artifactId": 8975346289,
      "fbxSha256": "22e8574c3f2c2ec92353871d271786c29e90d39d15b6dbac50ed22115a4bdabb",
      "portraitSha256": "a9076971ea36d3c8a0f5fb019870e3a5531d85d5b1609b01bb0c73de8dec6222"
    }
  ]
}
JSON

printf 'Staged checksum-pinned C1-C4 review assets for non-promotable device-test build.\n'
