package output.states

enum class OutputState(
    val mainMotorState: MainMotorState,
    val cutterMotorState: CutterMotorState,
    val electromagnetSate: HoldingMotorState,
) {

    FULL_STOP(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.NONE,
    ),
    FULL_STOP_WITH_ELECTROMAGNET_DOWN(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.DOWN,
    ),

    /** Starting to cut */
    CUTTING_TOWARDS_END(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.LEFT,
        electromagnetSate = HoldingMotorState.NONE,
    ),

    /** Going back with the cutting knife */
    CUTTING_TOWARDS_START(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.RIGHT,
        electromagnetSate = HoldingMotorState.NONE,
    ),
    PAPER_MOVING_FORWARD(
        mainMotorState = MainMotorState.FORWARD,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.NONE,
    ),
    PAPER_MOVING_BACKWARD(
        mainMotorState = MainMotorState.BACKWARDS,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.NONE,
    ),
    MOVING_BACKWARD_WITH_ELECTROMAGNET_DOWN(
        mainMotorState = MainMotorState.BACKWARDS,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.NONE,
    ),
    MOVING_HOLDING_MOTOR_DOWN(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.DOWN,
    ),
    MOVING_HOLDING_MOTOR_UP(
        mainMotorState = MainMotorState.NONE,
        cutterMotorState = CutterMotorState.NONE,
        electromagnetSate = HoldingMotorState.UP,
    ),
}