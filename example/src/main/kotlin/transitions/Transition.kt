package transitions

import input.*
import output.states.State

/**
 * startState - if null, will match any
 * */
enum class Transition(
    val startState: State?,
    val input: Input,
    val destinationState: State,
    val onTransitionAction: (() -> Unit)? = null,
) {

    BEGIN(
        State.STOP,
        StartEntered,
        State.FORWARD,
    ),
    STOP(
        null,
        StopEntered,
        State.STOP,
    ),
    FirstCutMarkStart(
        State.FORWARD,
        ContrastSensorHigh,
        State.FORWARD_FIRST_LINE_STARTED,
    ) {
        //TODO timeout
    },
    FirstCutMarkEnd(
        State.FORWARD_FIRST_LINE_STARTED,
        ContrastSensorLow,
        State.FORWARD_FIRST_LINE_ENDED,
    ) {
        //TODO timeout
    },
    SecondCutMarkStart(
        State.FORWARD_FIRST_LINE_ENDED,
        ContrastSensorHigh,
        State.FORWARD_SECOND_LINE_STARTED,
    ) {
        //TODO timeout
    },
    CutToEnd(
        State.FORWARD_SECOND_LINE_STARTED,
        ContrastSensorLow,
        State.CUT_TOWARDS_END,
    ) {
        //TODO timeout just to be safe
    },
    PauseBeforeCuttingToStart(
        State.CUT_TOWARDS_END,
        CutterEndDetected,
        State.PAUSE_BEFORE_CUTS,
    ) {
        //TODO timeout to start cutting to start, trigger PauseBeforeStartToCutToStartHasFinished
    },
    CutToStart(
        State.PAUSE_BEFORE_CUTS,
        PauseBeforeStartToCutToStartHasFinished,
        State.CUT_TOWARDS_START,
    ) {
        //TODO timeout just to be safe
    },
    MoveForwardAfterCuttingFinished(
        State.CUT_TOWARDS_START,
        CutterStartDetected,
        State.FORWARD,
    ) {
        //TODO timeout just to be safe
    },
}