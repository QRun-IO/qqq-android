# Gradle wrapper provenance

Verified October 31, 2026 against Gradle's [published checksums](https://gradle.org/release-checksums/).

| Artifact | SHA-256 |
| --- | --- |
| Gradle 8.7 wrapper JAR | `cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8` |
| Gradle 8.7 binary distribution | `544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d` |

The bundled wrapper now matches the [official 8.7 source artifact](https://github.com/gradle/gradle/blob/v8.7.0/gradle/wrapper/gradle-wrapper.jar).
The distribution URL and its enforced checksum are unchanged. The previous JAR
hash, `e996d452d2645e70c01c11143ca2d3742734a28da2bf61f25c82bdc288c9e637`,
matches Gradle's published 6.9/7.0 wrapper family; it was an older bootstrap rather
than evidence of a modified JAR.

This establishes the bundled JAR's upstream identity. It is not an independent
audit of every Gradle class or current dependency vulnerability clearance.
