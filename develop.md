# Development Guide


## [Javadocs](https://gaucho-matrero.github.io/altoclef/)


## Get it Running (IDE)

### James Green's setup guide!

[![James Green's Intellij Setup video on YouTube](https://img.youtube.com/vi/zZ1upxZ43Sg/0.jpg)](https://www.youtube.com/watch?v=zZ1upxZ43Sg)

### Text tutorial

Clone project and import. I'd suggest using JetBrain's IntelliJ to import the project. Make sure you're using jdk 21 in your Project & Gradle Settings!
1) Open Intellij
2) Click `File > New > Project from Version Control...` ![image](https://user-images.githubusercontent.com/13367955/146222866-42fa307b-016e-40a6-98bc-6e2428cde2dc.png)
3) Copy + Paste the Vibrato clone URL and Clone (find here: )![image](https://user-images.githubusercontent.com/13367955/146223264-0cc436c0-4c08-4adc-b948-0ca3da4fbd6f.png)
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

Vibrato uses [Baritone](https://github.com/cabaletta/baritone) for pathfinding. It is vendored as the `baritone/`
git submodule (branch `1.21.1`, kept pristine) and Vibrato's hooks are carried as patches in `patches/baritone/`:

* `baritone.vibrato.VibratoSettings` - break/place avoidance, forced walkable/non-walkable blocks, tool overrides,
  protected items, global heuristics, an interaction pause, flowing-water and lava passes
* `baritone.vibrato.RotationSmoother` - lets Vibrato's human camera model decide every rotation Baritone applies
* `RayTraceUtils.fluidHandling`

You do not build Baritone by hand. `gradle/baritone.gradle` runs on every Gradle configure and, when needed,
initializes the submodule, applies the patches, builds Baritone's fabric jar into `baritone/dist` and records a stamp
of what it was built from. The first build is slow (it downloads and remaps Minecraft); later ones are instant.

```
git clone --recurse-submodules <repo>     # or: git submodule update --init baritone
./gradlew build
```

To change a hook, edit files inside `baritone/`, then regenerate the patch:

```
git -C baritone add -N .
git -C baritone diff > patches/baritone/0001-vibrato-hooks.patch
git -C baritone reset -q
```

then reset the submodule (`git -C baritone checkout -- . && git -C baritone clean -fd`) so the next Gradle run applies
the new patch to a pristine tree. Pass `-PbaritoneDist=/path/to/dist` to use a prebuilt jar and skip all of this.

## Task Development Guides

### Task Programming Tutorial Stream
[![More Recent AltoClef Task Programming VOD](https://img.youtube.com/vi/uROEqwyzn3o/0.jpg)](https://www.youtube.com/watch?v=uROEqwyzn3o)

### Old (Post Stream) Tutorial VOD
[![Rough AltoClef Tutorial VOD](https://img.youtube.com/vi/giBjHDZ7HvY/0.jpg)](https://www.youtube.com/watch?v=giBjHDZ7HvY)


