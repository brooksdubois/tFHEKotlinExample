
# tFHE With Zama in Kotlin

This is a multi-project gradle build with a few moving pieces I will attempt to describe here.

The core tenets of this project are that:
1. Anyone can count all the votes with their cloud key
2. Any voter can see that there vote is accurately there and who they voted for is who they voted for
3. Voters cannot decrypt other people's votes.

This is meant to be an exploration of tFHE with Zama and a demo of using Rust with Kotlin Native.

## The Blockchain

There is a rudimentary blockchain written in Kotlin that sits inside of the kvm-blockchain directory.

To build it, you will first need to compile the Zama library for your system. In my case, it was pretty easy. These commands should work for any m2 macbook air.

```bash
cd ./kvm-blockchain/tfhe-bridge
cargo clean && cargo build --release
cp ./target/release/libtfhe_bridge.dylib ./libs/libtfhe_bridge.dylib 
```

Since mac requires the `.dylib` file for Rust to work with JNI, this worked on my machine. Your compilation may require tweaking depending on your OS.

These steps are required if you modify the `tfhe-bridge/lib.rs` file.

To run the blockchain scripts, in the root directory use gradle like this:

`./gradlew :kvm-blockchain:run`

This should spit out a few files in the verifier directory. You should see a few keys and an encrypted_user_votes.json file.

## Running the Verifier script

Once those you are running correctly you'll see an output as such:

> Candidate 0 tally: 🔒 [[B@1a407d53, [B@3d8c7aca, [B@5ebec15, [B@21bcffb5, [B@380fb434, [B@668bc3d5, [B@3cda1055, [B@7a5d012c]
Candidate 1 tally: 🔒 [[B@3fb6a447, [B@79b4d0f, [B@6b2fad11, [B@79698539, [B@73f792cf, [B@2ed94a8b, [B@38082d64, [B@dfd3711]
Candidate 2 tally: 🔒 [[B@42d3bd8b, [B@26ba2a48, [B@5f2050f6, [B@3b81a1bc, [B@64616ca2, [B@13fee20c, [B@4e04a765, [B@783e6358]
Candidate 3 tally: 🔒 [[B@17550481, [B@735f7ae5, [B@180bc464, [B@1324409e, [B@2c6a3f77, [B@246ae04d, [B@62043840, [B@5315b42e]

Now, you should also notice a few files pop into the verifier directory.

To run the verifier use the command:

`./gradlew :verifier:run --args="ghi789"`

This will take in an arg, depending on what id's you've given users. The verifier script will decrypt your voter's vote (only theirs) and produce a histogram of all votes.

> 🔓 Your vote: Candidate 1
.... (other extraneous computation logs)
📊 Public tally (computed from individual encrypted votes):
Candidate 0: 0
Candidate 1: 1
Candidate 2: 0
Candidate 3: 2

### Caveats
There was a branch dedicated to streamlining with the Zama Integer API, which did work however generated keys that were 2Gb+ in size and unusable in practice. This will be remedied as soon as Zama has a compressed integer key available (which should be soon), then the code from that branch will be restored and the Encrypted Boolean class will be unnecessary. For now, we are xor'ing together alot of booleans to effectively represent addition of integers. 

***NOTE:*** This is purely for educational purposes and isn't intended to be used in any production scenario for real voting.

