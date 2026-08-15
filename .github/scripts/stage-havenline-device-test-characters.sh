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
glb_sha=(
  027a1bd6965f923bf93a67f4a0619b685bc43c2fd0d958afaea9faa404ef6f17
  b4acdbeacc663b36aa9ffab89a91c2d8f2735108ac55799974491e5af1090b1c
  3021cbcc00ab8f260094255c615892e56152334c47222868bdebafedd58880e9
  8820b018e77dcc36dc8a73aa400decc8c08a870fccb8a83aeacec175ae2b6e0d
)
portrait_sha=(
  e5fd5853e1bbdf58173a4de552c898bdd13b6c489dbd9fba3347f5eb1f9ebbc6
  0bd0f6eb56b87e057230bce7cdbe242ce2b8e67da488429feb51045a49f88f6f
  e3e7fe2ba62e4a199e22b432144c8787463c7b8e52143c0d70ee8aa95cbd179e
  a9076971ea36d3c8a0f5fb019870e3a5531d85d5b1609b01bb0c73de8dec6222
)
image0_ext=(png png jpg jpg)
image0_sha=(
  89035df1fd679e527d69ecc7e256ee8bf51c42ba4c67df403a14598b35a33b33
  56652de047b351cd94e714d697c8557cfb0dd13e650838920b53dfb7326eea5c
  e7e6105e3d8f4cde988091bf5b4b2b9f47db87c45c777b86e6fe3e6e40837a4b
  dbed475a89640fa4652f44c9c33a293940dc1f365e5c6060137f03bca4c065c5
)
image1_ext=("" "" png png)
image1_sha=(
  ""
  ""
  d66b9f4137c8102a3ab26d674a9c4a8c0701286bb39bf9f277fa2dcfd961cbb3
  65865afdbd9235060efbfdd83de3d336a30a2537dd5e7e4dff6397a404cd03f5
)

extract_glb_images() {
  local glb="$1"
  local output_dir="$2"
  local character="$3"
  python3 - "$glb" "$output_dir" "$character" <<'PY'
import json
import pathlib
import struct
import sys

path = pathlib.Path(sys.argv[1])
out_dir = pathlib.Path(sys.argv[2])
character = sys.argv[3]
data = path.read_bytes()
if len(data) < 20 or data[:4] != b'glTF':
    raise SystemExit(f"Invalid GLB: {path}")
magic, version, declared_length = struct.unpack_from('<III', data, 0)
if version != 2 or declared_length != len(data):
    raise SystemExit(f"Unexpected GLB header for {path}: version={version}, declared={declared_length}, actual={len(data)}")

offset = 12
json_bytes = None
bin_bytes = None
while offset + 8 <= len(data):
    chunk_length, chunk_type = struct.unpack_from('<II', data, offset)
    offset += 8
    chunk = data[offset:offset + chunk_length]
    offset += chunk_length
    if chunk_type == 0x4E4F534A:
        json_bytes = chunk
    elif chunk_type == 0x004E4942:
        bin_bytes = chunk

if json_bytes is None or bin_bytes is None:
    raise SystemExit(f"GLB lacks JSON/BIN chunks: {path}")

doc = json.loads(json_bytes.rstrip(b' \t\r\n\0').decode('utf-8'))
images = doc.get('images') or []
views = doc.get('bufferViews') or []
if not images:
    raise SystemExit(f"GLB contains no embedded images: {path}")

out_dir.mkdir(parents=True, exist_ok=True)
for index, image in enumerate(images):
    if 'bufferView' not in image:
        raise SystemExit(f"Image {index} is not embedded via bufferView in {path}")
    view = views[image['bufferView']]
    start = int(view.get('byteOffset', 0))
    length = int(view['byteLength'])
    mime = image.get('mimeType', '')
    if mime == 'image/png':
        ext = 'png'
    elif mime in ('image/jpeg', 'image/jpg'):
        ext = 'jpg'
    else:
        raise SystemExit(f"Unsupported image MIME {mime!r} in {path}")
    payload = bin_bytes[start:start + length]
    if len(payload) != length:
        raise SystemExit(f"Embedded image {index} is truncated in {path}")
    output = out_dir / f"{character}_glb_image_{index}.{ext}"
    output.write_bytes(payload)
    print(f"Extracted {output} ({length} bytes)")
PY
}

for i in "${!characters[@]}"; do
  character="${characters[$i]}"
  artifact="${artifact_ids[$i]}"
  archive="$tmp/${character}.zip"
  extracted="$tmp/${character}"
  staged="$stage_root/$character"
  mkdir -p "$extracted" "$staged"

  echo "Downloading pinned $character review artifact $artifact"
  curl --fail --location --retry 4 --retry-delay 2 \
    -H "Authorization: Bearer $GITHUB_TOKEN" \
    -H 'Accept: application/vnd.github+json' \
    -H 'X-GitHub-Api-Version: 2022-11-28' \
    "https://api.github.com/repos/$GITHUB_REPOSITORY/actions/artifacts/$artifact/zip" \
    --output "$archive"

  unzip -q "$archive" -d "$extracted"
  cp "$extracted/${character}_production.fbx" "$staged/${character}_production.fbx"
  cp "$extracted/proof_front.png" "$staged/${character}_portrait.png"

  echo "${fbx_sha[$i]}  $staged/${character}_production.fbx" | sha256sum --check --strict
  echo "${glb_sha[$i]}  $extracted/${character}_production.glb" | sha256sum --check --strict
  echo "${portrait_sha[$i]}  $staged/${character}_portrait.png" | sha256sum --check --strict

  extract_glb_images "$extracted/${character}_production.glb" "$staged" "$character"
  image0="$staged/${character}_glb_image_0.${image0_ext[$i]}"
  echo "${image0_sha[$i]}  $image0" | sha256sum --check --strict
  if [ -n "${image1_sha[$i]}" ]; then
    image1="$staged/${character}_glb_image_1.${image1_ext[$i]}"
    echo "${image1_sha[$i]}  $image1" | sha256sum --check --strict
  fi
done

cat > "$stage_root/HAVENLINE_DEVICE_TEST_CHARACTER_STAGE.json" <<'JSON'
{
  "schemaVersion": 2,
  "deviceTestOnly": true,
  "sourceRunId": 31127195233,
  "characters": [
    {
      "character": "Character1",
      "artifactId": 8975286960,
      "fbxSha256": "1a94029a0367ead8623296942de8bd516061de39e077ba2dd237bd238bbb5c1b",
      "glbSha256": "027a1bd6965f923bf93a67f4a0619b685bc43c2fd0d958afaea9faa404ef6f17",
      "portraitSha256": "e5fd5853e1bbdf58173a4de552c898bdd13b6c489dbd9fba3347f5eb1f9ebbc6",
      "textureSha256": ["89035df1fd679e527d69ecc7e256ee8bf51c42ba4c67df403a14598b35a33b33"]
    },
    {
      "character": "Character2",
      "artifactId": 8975298326,
      "fbxSha256": "803b91c60f94cae7e4c9871f20e5345a5d9d366cb9d28fae9515cdfa8a17b95f",
      "glbSha256": "b4acdbeacc663b36aa9ffab89a91c2d8f2735108ac55799974491e5af1090b1c",
      "portraitSha256": "0bd0f6eb56b87e057230bce7cdbe242ce2b8e67da488429feb51045a49f88f6f",
      "textureSha256": ["56652de047b351cd94e714d697c8557cfb0dd13e650838920b53dfb7326eea5c"]
    },
    {
      "character": "Character3",
      "artifactId": 8975329072,
      "fbxSha256": "2a29e90a12cf0ae0905596cd01401443f85705fc115e4cd6d4cbe1f2539000e7",
      "glbSha256": "3021cbcc00ab8f260094255c615892e56152334c47222868bdebafedd58880e9",
      "portraitSha256": "e3e7fe2ba62e4a199e22b432144c8787463c7b8e52143c0d70ee8aa95cbd179e",
      "textureSha256": ["e7e6105e3d8f4cde988091bf5b4b2b9f47db87c45c777b86e6fe3e6e40837a4b", "d66b9f4137c8102a3ab26d674a9c4a8c0701286bb39bf9f277fa2dcfd961cbb3"]
    },
    {
      "character": "Character4",
      "artifactId": 8975346289,
      "fbxSha256": "22e8574c3f2c2ec92353871d271786c29e90d39d15b6dbac50ed22115a4bdabb",
      "glbSha256": "8820b018e77dcc36dc8a73aa400decc8c08a870fccb8a83aeacec175ae2b6e0d",
      "portraitSha256": "a9076971ea36d3c8a0f5fb019870e3a5531d85d5b1609b01bb0c73de8dec6222",
      "textureSha256": ["dbed475a89640fa4652f44c9c33a293940dc1f365e5c6060137f03bca4c065c5", "65865afdbd9235060efbfdd83de3d336a30a2537dd5e7e4dff6397a404cd03f5"]
    }
  ]
}
JSON

printf 'Staged checksum-pinned C1-C4 FBX rigs, portraits and exact recovered GLB textures for non-promotable device-test build.\n'
