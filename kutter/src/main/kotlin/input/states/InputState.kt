package input.states

sealed class InputState(
    val contrastSensorState: Boolean,
    val endDetectionSensorState: Boolean,
    val previousState: InputState?,
)

data object InitialState : InputState(
    contrastSensorState = false,
    endDetectionSensorState = false,
    previousState = null,
)

data object FirstLineStart : InputState(
    contrastSensorState = true,
    endDetectionSensorState = false,
    previousState = null
)

data object FirstLineEnd : InputState(
    contrastSensorState = false,
    endDetectionSensorState = false,
    previousState = null
)
