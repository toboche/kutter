package transitions

import input.*
import kotlinx.coroutines.delay
import machine.FiniteStateMachine
import output.states.State

//TODO: calibrate these values or make them editable by the user
private const val DEFAULT_PRINT_MARK_SCAN_DELAY = 2000L
private const val SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION = 100L
private const val LONG_CUTTING_DELAY = 10000L

/**
 * startState - if null, will match any
 * */
enum class Transition(
    val startState: State?,
    val input: Input,
    val destinationState: State,
    val onTransitionAction: (suspend (finiteStateMachine: FiniteStateMachine) -> Unit)? = null,
) {

    Begin(
        State.STOP,
        StartEntered,
        State.FORWARD,
    ),

    /** Can be used to "just continue moving forward" if e.g. print mark scan was not successful. */
    ForceMovingForward(
        null,
        StartEntered,
        State.FORWARD,
    ),
    Stop(
        null,
        StopEntered,
        State.STOP,
    ),
    FirstCutMarkStart(
        State.FORWARD,
        ContrastSensorHigh,
        State.FORWARD_FIRST_LINE_STARTED,
        onTransitionAction = goBackToMovingForwardWithPrintMarkDelayAction
    ),
    FirstCutMarkEnd(
        State.FORWARD_FIRST_LINE_STARTED,
        ContrastSensorLow,
        State.FORWARD_FIRST_LINE_ENDED,
        onTransitionAction = goBackToMovingForwardWithPrintMarkDelayAction

    ),
    SecondCutMarkStart(
        State.FORWARD_FIRST_LINE_ENDED,
        ContrastSensorHigh,
        State.FORWARD_SECOND_LINE_STARTED,
        onTransitionAction = goBackToMovingForwardWithPrintMarkDelayAction

    ),
    CutToEnd(
        State.FORWARD_SECOND_LINE_STARTED,
        ContrastSensorLow,
        State.CUT_TOWARDS_END,
        onTransitionAction = { finiteStateMachine ->
            //something has gone wrong, cutting is taking too much time
            delay(LONG_CUTTING_DELAY)
            finiteStateMachine.transition(StopEntered)
        }
    ),
    PauseBeforeCuttingToStart(
        State.CUT_TOWARDS_END,
        CutterEndDetected,
        State.PAUSE_BEFORE_CUTS,
        onTransitionAction = { finiteStateMachine ->
            delay(SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION)
            finiteStateMachine.transition(PauseBeforeStartToCutToStartHasFinished)
        }
    ),
    CutToStart(
        State.PAUSE_BEFORE_CUTS,
        PauseBeforeStartToCutToStartHasFinished,
        State.CUT_TOWARDS_START,
        onTransitionAction = { finiteStateMachine ->
            //something has gone wrong, cutting is taking too much time
            delay(LONG_CUTTING_DELAY)
            finiteStateMachine.transition(StopEntered)
        }

    ),
    MoveForwardAfterCuttingFinished(
        State.CUT_TOWARDS_START,
        CutterStartDetected,
        State.FORWARD,
    ), ;

}

private val goBackToMovingForwardWithPrintMarkDelayAction: suspend (finiteStateMachine: FiniteStateMachine) -> Unit =
    { finiteStateMachine ->
        delay(DEFAULT_PRINT_MARK_SCAN_DELAY)
        finiteStateMachine.transition(StartEntered)
    }
