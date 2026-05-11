# Kotlin BB Vote

Kotlin BB Vote is an experimental encrypted voting demo built around Kotlin, Ktor, Rust, JNI, and Zama's TFHE libraries. The backend records votes in a small Kotlin blockchain-style ledger, encrypts each ballot as a one-hot `u16` vector, and supports an MPC-style tally flow where encrypted totals can be masked, decrypted, and publicly unmasked into final counts.

The idea is that anyone can see that their vote is there with their reciept, however, they can actually decrypt and validate that who they voted for is who they voted for... then, by combining a few keys together, anyone can _tally_ all of the votes themselves. This is leveraging tFHE to accomplish the tallying by utilizing public key, private key, cloud key encryption. 

### Vote Reciept Verification GUI:

<img width="1151" height="729" alt="Screenshot 2026-05-11 at 4 20 05 PM" src="https://github.com/user-attachments/assets/6f4ae30f-2c46-4adb-b079-d69bc64e18cf" />

### Tally GUI:

<img width="1161" height="678" alt="Screenshot 2026-05-11 at 4 18 38 PM" src="https://github.com/user-attachments/assets/0cae2ada-83d3-4a85-bd74-07203c3d5527" />

The project is split into two main parts:

- `kvm-root`: the Gradle backend workspace, including the Ktor API, verifier CLI, Kotlin domain logic, and Rust TFHE bridge.
- `voting-ui-2`: the SolidStart frontend, which talks to the Ktor backend through a tRPC server layer.

The current app is best understood as a portfolio/demo project for encrypted computation and cross-language integration. It is not a production voting system.

## Tech Stack

- Kotlin JVM 2.0.21
- Gradle 8.12 wrapper
- Ktor 2.3.x
- Rust `cdylib` exposed to Kotlin through JNI
- Zama `tfhe` Rust crate
- SolidStart, Vinxi, tRPC, Tailwind, Bun

## Quick Start

Run these commands from the workspace root unless noted otherwise.

### 1. Build the Rust TFHE Bridge

Gradle expects the native library to already exist under the Rust crate's release output directory.

```bash
cd kvm-root/backend/crypto/tfhe-bridge
cargo build --release
```

On macOS this produces:

```text
kvm-root/backend/crypto/tfhe-bridge/target/release/libtfhe_bridge.dylib
```

On Linux or Windows, the library extension will differ, but the Kotlin loader checks the same release directory.

### 2. Start the Ktor Backend

Install jdk 17 with [SDKman!]("https://sdkman.io/")

```bash
sdk install java 17.0.12-graal
sdk use java 17.0.12-graal
```

Run the backend project

```bash
cd kvm-root
./gradlew :backend:ktor-app:run
```

The backend runs at:

```text
http://localhost:8080
```

Useful smoke check:

```bash
curl http://localhost:8080/
```

### 3. Start the Frontend

In another terminal:

```bash
cd voting-ui-2
mv env.example .env
bun install
bun run dev
```

The SolidStart dev server usually runs at:

```text
http://localhost:3000
```

The frontend defaults to `KTOR_HOST=http://localhost:8080`.

## Backend Layout

The Gradle project lives in `kvm-root` and includes these modules:

```text
:backend:core      Kotlin blockchain, voting models, MPC models
:backend:crypto    Kotlin/JNI wrappers around the Rust TFHE bridge
:backend:ktor-app  Ktor HTTP API
:backend:cli       Local verifier and MPC utility CLI
```

The backend run task is:

```bash
cd kvm-root
./gradlew :backend:ktor-app:run
```

The CLI run task is:

```bash
cd kvm-root
./gradlew :backend:cli:run --args="<command>"
```

## Rust Integration

The Rust crate is here:

```text
kvm-root/backend/crypto/tfhe-bridge
```

It builds a dynamic library with:

```toml
crate-type = ["cdylib"]
```

Kotlin loads that library through JNI. Both the Ktor app and CLI set:

```text
-Djava.library.path=backend/crypto/tfhe-bridge/target/release
```

If native loading fails, rebuild the Rust crate:

```bash
cd kvm-root/backend/crypto/tfhe-bridge
cargo build --release
```

You can also override the native library path explicitly:

```bash
cd kvm-root
TFHE_BRIDGE_PATH=/absolute/path/to/libtfhe_bridge.dylib ./gradlew :backend:ktor-app:run
```

## Main API Endpoints

When the Ktor backend is running on port `8080`:

```text
GET  /                         health check
GET  /server-key                fetch public server key material for homomorphic ops
GET  /blocks                    inspect ledger blocks
POST /vote                      cast a vote
POST /vote/raw                  cast a pre-encrypted vote payload
GET  /user-votes                fetch encrypted one-hot ballots
GET  /receipt/{id}              fetch receipt data for a voter id

POST /mpc/sessions/start        start an MPC tally session
POST /mpc/sessions/{id}/mask:server
POST /mpc/sessions/{id}/decrypt
POST /mpc/sessions/{id}/reveal
GET  /mpc/sessions/{id}         inspect session status
GET  /mpc/sessions/{id}/zip     download session artifacts
GET  /mpc/artifacts/{path...}   fetch a specific artifact
```

## In-Depth Usage

### Cast a Vote

From the backend directory:

```bash
cd kvm-root
```

```bash
curl -sSf http://localhost:8080/vote \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "u100",
    "name": "Alice Example",
    "address": "123 Main St",
    "age": 34,
    "candidate": 2
  }'
```

Inspect the ledger:

```bash
curl -sS http://localhost:8080/blocks | jq .
```

Fetch encrypted ballots:

```bash
mkdir -p verifier
curl -sSf http://localhost:8080/user-votes \
  -o verifier/encrypted_user_votes.json
```

### Fold Ballots Into Encrypted Totals

The CLI can fold all encrypted one-hot ballots into one encrypted total per candidate:

```bash
./gradlew :backend:cli:run --args="fold \
  --server-key=public/u16_server_key.bin \
  --votes=verifier/encrypted_user_votes.json \
  --out=verifier/u16_tally_ciphertexts.json"
```

This writes a JSON array of Base64 ciphertexts.

### Run the Local MPC Flow With the CLI

Mask the encrypted totals:

```bash
./gradlew :backend:cli:run --args="mpc-mask \
  --in=verifier/u16_tally_ciphertexts.json \
  --out=verifier/masked_A.json \
  --save-masks=verifier/masks_A.json \
  --server-key=public/u16_server_key.bin \
  --who=A \
  --seed=1234"
```

Decrypt the masked totals:

```bash
./gradlew :backend:cli:run --args="mpc-decrypt \
  --in=verifier/masked_A.json \
  --client-key=public/u16_client_key.bin \
  --out=verifier/masked_plain.json"
```

Reveal masks and compute final totals:

```bash
./gradlew :backend:cli:run --args="mpc-unmask \
  --in=verifier/masked_plain.json \
  --masks=verifier/masks_A.json \
  --out=verifier/totals.json"
```

### Run the MPC Flow Through the API

Start a session from the current live ledger:

```bash
curl -sSf http://localhost:8080/mpc/sessions/start \
  -H 'Content-Type: application/json' \
  -d '{"source":"live"}' | jq .
```

The response shape is:

```json
{
  "id": "mpc-...",
  "candidateCount": 4,
  "artifacts": ["..."]
}
```

Use the returned `id` for the remaining calls:

```bash
SID="mpc-..."

curl -sSf "http://localhost:8080/mpc/sessions/$SID/mask:server" \
  -H 'Content-Type: application/json' \
  -d '{"who":"A","seed":1234}' | jq .

curl -sSf "http://localhost:8080/mpc/sessions/$SID/decrypt" \
  -H 'Content-Type: application/json' \
  -d '{}' | jq .

curl -sSf "http://localhost:8080/mpc/sessions/$SID/zip" \
  -o "verifier/session-$SID.zip"
```

The `/mask:server` step writes mask artifacts into the session directory. Reveal those masks to finalize the tally:

```bash
curl -sSf "http://localhost:8080/mpc/sessions/$SID/reveal" \
  -H 'Content-Type: application/json' \
  -d '{"who":"A","masks":[/* paste masks_A.json array here */]}' | jq .
```

For the easiest path, use the GUI at `/mpc`; it starts a live session, masks, decrypts, fetches the mask artifact from the ZIP, reveals, and displays totals.

## Frontend Notes

The frontend lives in `voting-ui-2`.

```bash
cd voting-ui-2
bun install
bun run dev
```

The tRPC backend adapter reads:

```text
KTOR_HOST=http://localhost:8080
```

If the Ktor server is on a different port:

```bash
KTOR_HOST=http://localhost:9090 bun run dev
```

The frontend routes of interest are:

```text
/          ledger/tally overview
/ballot    cast a ballot
/lookup    lookup a tracker id
/verify    receipt-oriented verification UI
/mpc       start and run the MPC tally flow
```

## Common Problems

### Native Library Not Found

Rebuild the Rust bridge:

```bash
cd kvm-root/backend/crypto/tfhe-bridge
cargo build --release
```

Then restart Ktor:

```bash
cd kvm-root
./gradlew :backend:ktor-app:run
```

### Wrong Gradle Command

The current backend module is:

```bash
./gradlew :backend:ktor-app:run
```

Older notes may reference `:backend:run`, `:kvm-blockchain:run`, or `:verifier:run`; those are stale.

### Frontend Cannot Reach Backend

Make sure Ktor is running on `8080`, or set `KTOR_HOST` when starting the frontend:

```bash
cd voting-ui-2
KTOR_HOST=http://localhost:8080 bun run dev
```

## Verification Commands

Useful project checks:

```bash
cd kvm-root
./gradlew projects
./gradlew :backend:ktor-app:tasks --group application
./gradlew :backend:cli:tasks --group application
```

Build the backend:

```bash
cd kvm-root
./gradlew build
```

Build the frontend:

```bash
cd voting-ui-2
bun run build
```
