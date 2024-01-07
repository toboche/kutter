This is a XY Plotter/Cutter with automatic cut mark detection

it uses:

* Kotlin
* Jetpack Compose
* Raspberry Pi 4
* Datalogic S8-PR Contrast sensor for print mark detection
* Is based on a disassembled printer

Setup on Raspberry PI 4

# Software set up

## Basic OS setup
* Install Raspberry PI OS FULL 64BIT BOOKWORM
* Install java 17 if not already available (mine had it)
* `export MESA_EXTENSION_OVERRIDE="-GL_ARB_invalidate_subdata"` - source: https://github.com/JetBrains/skiko/issues/649

## Building

`gradlew :kutter:shadowJar`

## Running

* locally: `scp build/libs/kutter-0.2-all.jar <username>@<RPiIP>://kutter-0.2-all.jar`
* on rpi: `sudo -E java -jar kutter-0.2-all.jar`
    * will not work via ssh for now as it requires an X Window to be available
    * `sudo` needed because of pi4j

# Hardware set up

Raspberry Pi 4 is connected to:

* A contrast sensor with NPN/PNP output, operating on 12V source - the output cannot be directly plugged to Raspberry
  Pi, not to burn the input pin
    * A voltage divider is needed as in [here](https://forums.raspberrypi.com/viewtopic.php?t=241127)
* DC motor that unrolls/rolls the paper
    * Connected via L298N
    * Can move either up or down. The software uses `forward` and `backwards` in these cases, where `forward` means
      unrolling the paper.
* DC motor that controls the carriage that cuts the paper
    * Connected via L298N
    * The carriage can move left/right.
    * The carriage has it's "home position". Home is assumed to be at the rightmost position. From the home's
      perspective, the carriage can move either towards `end` or `start`
* Home and end switches
    * As of now, these are mechanical switches that detect if the `end` or `start` position was reached by the cutting
      carriage.
