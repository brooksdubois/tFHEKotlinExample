# End-to-end flow (per election)

0. **Registration (system layer, optional here)**
   Issue an eligibility credential + a **nullifier** to prevent double-voting. Not part of TFHE itself.

1. **Key ceremony (DKG, one-time per election)**
   The committee (n members) runs **Distributed Key Generation** → outputs:

* **PublicKey (PK)**: for voters to encrypt ballots.
* **Server/Evaluation key (EK)**: for the backend to add ciphertexts.
* **n Secret key shares**: one per committee member. *(There is no “client key” for voters in this model; “ClientKey” in TFHE docs is a secret key and here it’s split into shares.)*
  Publish a small manifest: params + key fingerprints + DKG transcript hash.

2. **Key distribution**

* Publish PK (frontend) and deploy EK (backend).
* Secret shares **never leave** their owners (store in HSM/air-gapped media). No emailing secrets.

3. **Voting**

* Voter fetches PK, builds a **one-hot** vector (1 for chosen candidate, 0 elsewhere), encrypts locally with PK.
* Submit encrypted ballot (+ minimal metadata & a **commitment/tracker**).
* Backend enforces one-vote-per-nullifier; rejects duplicates.

4. **Public display / ledger**

* Every encrypted ballot is visible (ciphertext + tracker/commitment).
* Backend maintains an **encrypted histogram** via homomorphic adds *or* recomputes it at close.
* On close, publish a **tally snapshot** (the ciphertext vector) + its hash/id.

5. **Recorded-as-cast verification (per voter)**

* Each voter locates their tracker/commitment on the ledger and checks its ciphertext hash matches their receipt. (Optionally provide a Merkle proof of inclusion.)

6. **Threshold decryption of the snapshot (aggregate only)**

* Any **t** committee members produce **partial decryption shares** for each tally bin, sign them, and publish.
* A combiner verifies shares and combines → outputs the **plaintext counts**.

7. **Independent verification (anyone)**

* Recompute the encrypted tally from the posted ballots and confirm it matches the published snapshot.
* Verify decryption-share signatures (and proofs, if available) and recompute the **combine** step locally to get the same counts.

8. **Conclusion & archive**

* Publish final results + an **audit bundle**: params, key fingerprints, DKG hash, snapshot ciphertext + hash, all decryption shares/signatures, final plaintext counts, Merkle root of ballots.
* Rotate/retire keys; wipe EK if policy requires.


# In simpler terms

1. **Set up referees:** A small group of independent referees split a special “unlock key” so no one person can open results alone (they also publish a public “lock”). ([GitHub][5])
2. **Post the lock:** The public lock (encryption key) is shared so anyone can seal a vote; the “calculator key” for adding sealed votes lives on the server. ([Zama Documentation][4])
3. **People vote:** Each voter seals a simple one-choice ballot with the public lock and gets a private receipt code.
4. **Public board:** All sealed ballots (still unreadable) are posted on a public board so the world can see what was received.
5. **Check your receipt:** Voters look up their code to confirm their sealed vote is included exactly once. (Optionally via a Merkle/inclusion proof, but no secrets needed.)
6. **Close & add up:** The system sums the sealed ballots into one sealed tally—still unreadable to anyone. (Homomorphic tallying.) ([heliosvoting.org][1])
7. **Team-unlock the totals:** Any **t** referees publish their **partial unlock pieces**; together these reveal **only the final totals**, never any individual vote. ([GitHub][3])
8. **Anyone can verify:** Anyone can re-add the sealed ballots to match the sealed tally, combine the published pieces, and get the same totals—end-to-end verifiable math. ([electionguard.vote][2])
