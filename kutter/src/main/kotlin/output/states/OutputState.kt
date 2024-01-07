package output.states

enum class OutputState(
    val mainMotorState: MainMotorState,
    val cutterMotorState: CutterMotorState,
) {

    FULL_STOP(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.NONE
    ),

    /** Starting to cut */
    CUTTING_TOWARDS_END(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.LEFT
    ),

    /** Going back with the cutting knife */
    CUTTING_TOWARDS_START(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.RIGHT
    ),
    MOVING_FORWARD(
        mainMotorState = MainMotorState.FORWARD,
        cutterMotorState = CutterMotorState.NONE
    ),
    MOVING_BACKWARD(
        mainMotorState = MainMotorState.BACKWARDS,
        cutterMotorState = CutterMotorState.NONE
    ),
}