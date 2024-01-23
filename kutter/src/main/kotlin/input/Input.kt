package input

sealed class Input

data object ContrastSensorHigh : Input()

data object ContrastSensorLow : Input()

data object CutterEndDetected : Input()
data object CutterStartDetected : Input()

data object StartEntered: Input()

data object StopEntered: Input()
data object CalibrationEntered : Input()
