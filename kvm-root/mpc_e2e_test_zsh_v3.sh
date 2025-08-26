#!/usr/bin/env zsh
# mpc_e2e_test_zsh_v3.sh — End-to-end smoke test for voting + MPC tally (DEV flow)
# Shell: zsh
# Fixes: avoid jq hanging by using -n in compare step; write a single final_totals.json
set -e
set -u
set -o pipefail

HOST="http://localhost:8080"
GRADLE="${GRADLE:-./gradlew}"
CLI_TASK="${CLI_TASK:-:backend:cli:run}"
CANDIDATES="${CANDIDATES:-4}"
ALLOW_DIRTY_CHAIN="${ALLOW_DIRTY_CHAIN:-0}"
WORKDIR="${WORKDIR:-verifier}"

# Test votes (0-based candidate indices)
typeset -a VOTER_IDS VOTER_NAMES VOTER_ADDRS VOTER_AGES VOTES
VOTER_IDS=("u100" "u101" "u102")
VOTER_NAMES=("Alice Example" "Bob Example" "Carol Example")
VOTER_ADDRS=("123 Main St" "45 Side St" "9 Elm Rd")
VOTER_AGES=(34 41 29)
VOTES=(2 0 3)   # for 4 candidates → expected [1,0,1,1]

mkdir -p "$WORKDIR"

log() { printf "\n\033[1;36m▶ %s\033[0m\n" "$*"; }
die() { printf "\n\033[1;31m✖ %s\033[0m\n" "$*" >&2; exit 1; }
json() { jq -c .; }
need() { command -v "$1" >/dev/null 2>&1 || die "Missing dependency: $1"; }

wait_for_server() {
  log "Waiting for Ktor at $HOST …"
  typeset i=0
  while [ $i -lt 60 ]; do
    if curl -fsS "$HOST/server-key" >/dev/null 2>&1; then
      log "Server is up."
      return
    fi
    sleep 1
    i=$((i+1))
  done
  die "Server not responding at $HOST/server-key"
}

assert_clean_chain() {
  if [ "$ALLOW_DIRTY_CHAIN" = "1" ]; then
    log "Skipping clean-chain check (ALLOW_DIRTY_CHAIN=1)"
    return
  fi
  log "Ensuring chain has zero records …"
  local total
  total=$(curl -fsS "$HOST/blocks" | jq '[.[].records | length] | add // 0')
  if [ "${total:-0}" -ne 0 ]; then
    die "Chain already has $total records — restart server or set ALLOW_DIRTY_CHAIN=1"
  fi
  log "Chain is clean."
}

cast_vote() {
  local id="$1" name="$2" addr="$3" age="$4" cand="$5"
  log "Casting vote: id=$id candidate=$cand"
  curl -fsS "$HOST/vote" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --arg id "$id" --arg name "$name" --arg addr "$addr" --argjson age "$age" --argjson cand "$cand" \
          '{id:$id,name:$name,address:$addr,age:$age,candidate:$cand}')" \
    | json > "$WORKDIR/cast_${id}.json"
}

fold_to_totals() {
  log "Downloading encrypted ballots → $WORKDIR/encrypted_user_votes.json"
  curl -fsS "$HOST/user-votes" | json > "$WORKDIR/encrypted_user_votes.json"

  log "Folding to per-candidate ciphertext totals via Gradle CLI"
  "$GRADLE" "$CLI_TASK" --args="fold \
    --server-key=public/u16_server_key.bin \
    --votes=$WORKDIR/encrypted_user_votes.json \
    --out=$WORKDIR/u16_tally_ciphertexts.json" >/dev/null

  jq -e 'type=="array" and length>0' "$WORKDIR/u16_tally_ciphertexts.json" >/dev/null \
    || die "fold did not produce a non-empty JSON array at $WORKDIR/u16_tally_ciphertexts.json"
}

start_mpc_session() {
  log "Starting MPC session from folded totals"
  local cts
  cts=$(cat "$WORKDIR/u16_tally_ciphertexts.json")
  curl -fsS "$HOST/mpc/sessions/start" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --argjson C "$CANDIDATES" --argjson cts "$cts" '{source:"upload", candidates:$C, ctsB64:$cts}')" \
    | json > "$WORKDIR/mpc_start.json"

  SID=$(jq -r '.session.id' "$WORKDIR/mpc_start.json")
  [ -n "$SID" -a "$SID" != "null" ] || die "Failed to obtain session id"
  SESSION_DIR="verifier/sessions/$SID"
  log "Session started: id=$SID (artifacts at $SESSION_DIR)"
}

mask_dev() {
  log "DEV mask — participant A"
  curl -fsS "$HOST/mpc/sessions/$SID/mask:server" \
    -H 'Content-Type: application/json' \
    -d '{"who":"A","seed":1234}' | json > "$WORKDIR/mask_A.json"

  log "DEV mask — participant B"
  curl -fsS "$HOST/mpc/sessions/$SID/mask:server" \
    -H 'Content-Type: application/json' \
    -d '{"who":"B","seed":5678}' | json > "$WORKDIR/mask_B.json"
}

decrypt_dev() {
  log "DEV decrypt (server auto-loads public/u16_client_key.bin)"
  curl -fsS "$HOST/mpc/sessions/$SID/decrypt" \
    -H 'Content-Type: application/json' \
    -d '{}' | json > "$WORKDIR/decrypt.json"
  jq -r '.maskedPlainCount' "$WORKDIR/decrypt.json" >/dev/null || die "decrypt step failed"
}

wait_for_totals() {
  log "Waiting for totals to appear …"
  typeset i=0
  while [ $i -lt 80 ]; do
    local resp totals sess_state
    resp=$(curl -fsS "$HOST/mpc/sessions/$SID")
    totals=$(echo "$resp" | jq -c '.totals // empty')
    sess_state=$(echo "$resp"  | jq -r '.status // ""')
    if [ -n "$totals" -a "$totals" != "null" ]; then
      printf "%s" "$totals" > "$WORKDIR/final_totals.json"
      log "Totals ready (status=$sess_state): $totals"
      return
    fi
    sleep 0.25
    i=$((i+1))
  done
  die "Totals not available after reveals"
}

reveal_and_collect() {
  log "Reveal masks from server-side artifacts"
  [ -f "$SESSION_DIR/masks_A.json" ] || die "Missing $SESSION_DIR/masks_A.json"
  [ -f "$SESSION_DIR/masks_B.json" ] || die "Missing $SESSION_DIR/masks_B.json"
  local a b
  a=$(cat "$SESSION_DIR/masks_A.json")
  b=$(cat "$SESSION_DIR/masks_B.json")

  curl -fsS "$HOST/mpc/sessions/$SID/reveal" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --argjson m "$a" '{who:"A",masks:$m}')" | json > "$WORKDIR/reveal_A.json"

  curl -fsS "$HOST/mpc/sessions/$SID/reveal" \
    -H 'Content-Type: application/json' \
    -d "$(jq -n --argjson m "$b" '{who:"B",masks:$m}')" | json > "$WORKDIR/reveal_B.json"

  wait_for_totals
}

compute_expected() {
  local votes_json exp
  votes_json=$(printf '%s\n' "${VOTES[@]}" | jq -cs '.')
  exp=$(jq -n --argjson C "$CANDIDATES" --argjson vs "$votes_json" '
    ([range(0;$C)] | map(0)) as $z
    | reduce $vs[] as $v ($z; .[$v]+=1)
  ')
  echo "$exp"
}

compare_totals() {
  local expected actual
  expected=$(compute_expected)
  actual=$(cat "$WORKDIR/final_totals.json")
  if jq -n -e --argjson e "$expected" --argjson a "$actual" '$e==$a' >/dev/null; then
    log "✅ Totals match expected: $actual"
  else
    printf "\nExpected: %s\nActual:   %s\n" "$expected" "$actual"
    die "Totals do NOT match expected"
  fi
}

main() {
  need curl; need jq
  wait_for_server
  assert_clean_chain

  log "Casting votes …"
  local N i id name addr age cand
  N=${#VOTER_IDS[@]}
  i=1
  while [ $i -le $N ]; do
    id="${VOTER_IDS[$i]}"
    name="${VOTER_NAMES[$i]}"
    addr="${VOTER_ADDRS[$i]}"
    age="${VOTER_AGES[$i]}"
    cand="${VOTES[$i]}"
    cast_vote "$id" "$name" "$addr" "$age" "$cand"
    i=$((i+1))
  done

  fold_to_totals
  start_mpc_session
  mask_dev
  decrypt_dev
  reveal_and_collect
  compare_totals

  log "Done. Artifacts in $WORKDIR and $SESSION_DIR"
}

main "$@"
