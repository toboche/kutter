package input

sealed class Input

data object ContrastSensorHigh : Input()

data object ContrastSensorLow : Input()

data object CutterEndDetected : Input()
data object CutterStartDetected : Input()

data object StartEntered: Input()

data object StopEntered: Input()
data object CalibrationEntered : Input()
data object MoveForwardEntered : Input()
data object MoveBackwardsEntered : Input()
data object MoveTowardsStartEntered : Input()
data object MoveTowardsEndEntered : Input()
