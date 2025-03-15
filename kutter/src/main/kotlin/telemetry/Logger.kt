package telemetry

import input.Input
import output.states.State

object Logger {

    private const val logMarkerDetection = true
    private const val logInputHandling = true
    private const val logStateChanges = true

    fun handleInputForNonCalibrationMode(input: Input) {
        if (logInputHandling) {
            println("handleInputForNonCalibrationMode: $input")
        }
    }

    fun onLowDetectedWhileWorking(logMessage: String) {
        if (logMarkerDetection) {
            println("onLowDetectedWhileWorking: $logMessage")
        }
    }

    fun logStateChange(state: State) {
        if (logStateChanges) {
            println("stateChange: $state")
        }
    }

    fun onStartReadingSettingFromFile() {
        if (logStateChanges) {
            println("start reading cutter_settings.blob")
        }
    }

    fun onErrorReadingSettingFromFile() {
        if (logStateChanges) {
            println("couldn't read cutter_settings.blob")
        }
    }

    fun onSettingFromFileRead(values: Triple<Double, Double, Double>) {
        if (logStateChanges) {
            println("cutter_settings.blob read; values:")
            values.toList().forEachIndexed { index, value ->
                println("$index: $value")
            }
        }
    }

}