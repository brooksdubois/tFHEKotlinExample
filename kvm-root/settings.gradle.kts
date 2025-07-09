rootProject.name = "kvm"

include(":kvm-blockchain")
include(":verifier")

project(":kvm-blockchain").projectDir = file("kvm-blockchain")
project(":verifier").projectDir = file("verifier")
