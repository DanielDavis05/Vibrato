# Development Guide


## [Javadocs](https://gaucho-matrero.github.io/altoclef/)


## Get it Running (IDE)

### James Green's setup guide!

[![James Green's Intellij Setup video on YouTube](https://img.youtube.com/vi/zZ1upxZ43Sg/0.jpg)](https://www.youtube.com/watch?v=zZ1upxZ43Sg)

### Text tutorial

Clone project and import. I'd suggest using JetBrain's IntelliJ to import the project. Make sure you're using jdk 21 in your Project & Gradle Settings!
1) Open Intellij
2) Click `File > New > Project from Version Control...` ![image](https://user-images.githubusercontent.com/13367955/146222866-42fa307b-016e-40a6-98bc-6e2428cde2dc.png)
3) Copy + Paste the altoclef clone URL and Clone (find here: )![image](https://user-images.githubusercontent.com/13367955/146223264-0cc436c0-4c08-4adc-b948-0ca3da4fbd6f.png)
4) Go to `File > Settings`, search `Gradle` and make sure your Gradle JVM is set to a JDK that's version 21 (IntelliJ lets you download open source JDKs, any of those should be fine) ![image](https://user-images.githubusercontent.com/13367955/146223463-2cfe8671-5504-430f-93d4-bb5312b2b540.png)
5) Go to `File > Project Structure`, then under `Project Settings/Project` make sure "Project SDK" is set to version 21 ![image](https://user-images.githubusercontent.com/13367955/146223634-dc4d9eb3-293a-4e70-b5fa-29f44145e02c.png)
7) On the right side of the screen open the gradle tab and navigate to `Tasks/fabric/runClient`. Click `runClient` ![image](https://user-images.githubusercontent.com/13367955/146223786-243c63e9-790f-48d7-b627-4e9191a84f22.png)
 
If the gradle tab doesn't exist, try `View > Tool Windows > Gradle`

## Get it Running (Command line, Linux & macOS)

1) Git Clone project
2) `cd` into cloned local repo
3) `sudo / doas chmod +x gradlew`
4) `./gradlew build` or `./gradlew runClient`

## Baritone

Alto Clef uses a patched baritone (the `baritone.altoclef.AltoClefSettings` hooks, originally from
[the gaucho-matrero fork](https://github.com/gaucho-matrero/baritone)) that gives you more control over how baritone works.
For Minecraft 1.21.1 that patch lives on the `altoclef-1.21.1` branch of a baritone checkout, based on upstream baritone's `1.21.1` branch.
The build expects it next to `altoclef` and is bundled into the Alto Clef jar:

1) Clone baritone into the same directory containing `altoclef` and check out the `altoclef-1.21.1` branch.
    For example, if you cloned `altoclef` into your desktop, `baritone` should be in your desktop as well.
2) Within `baritone`, run `./gradlew :fabric:build -Pavailable_loaders=fabric`.
3) There should now be a `baritone-unoptimized-fabric-<version>.jar` in `baritone/dist`.
   Make sure `baritone_version` in Alto Clef's `gradle.properties` matches `<version>`
   (the branch is tagged `v1.11.3-altoclef`, which produces `1.11.3-altoclef`).
4) Build Alto Clef as normal. Rebuild baritone whenever you change it.



## Task Development Guides

### Task Programming Tutorial Stream
[![More Recent AltoClef Task Programming VOD](https://img.youtube.com/vi/uROEqwyzn3o/0.jpg)](https://www.youtube.com/watch?v=uROEqwyzn3o)

### Old (Post Stream) Tutorial VOD
[![Rough AltoClef Tutorial VOD](https://img.youtube.com/vi/giBjHDZ7HvY/0.jpg)](https://www.youtube.com/watch?v=giBjHDZ7HvY)


