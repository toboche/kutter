This is a XY Plotter/Cutter with automatic cut mark detection 

it uses:
* Kotlin
* Jetpack Compose 
* Raspberry Pi 4
* Datalogic S8-PR Contrast sensor for print mark detection
* TODO: insert more on hardware

Setup on Raspberry PI 4
* Install Raspberry PI OS FULL 64BIT BOOKWORM
* Install java 17 if not already available (mine had it)
* `export MESA_EXTENSION_OVERRIDE="-GL_ARB_invalidate_subdata"` - source: https://github.com/JetBrains/skiko/issues/649

building
`gradlew :example:shadowJar`

running
* locally: `scp build/libs/example-0.2-all.jar <username>@<RPiIP>://example-0.2-all.jar`
* on rpi: `sudo -E java -jar example-0.2-all.jar` 
  * will not work via ssh for now
  * `sudo` needed because of pi4j

