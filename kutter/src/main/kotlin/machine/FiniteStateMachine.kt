package machine

import input.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import output.states.State
import java.time.Instant

class FiniteStateMachine {
    private val _currentState = MutableStateFlow(State.STOP)
    val currentState = _currentState.asStateFlow()
    var actionJob: Job? = null

    companion object {
        //TODO: calibrate these values or make them editable by the user
        private const val DEFAULT_PRINT_MARK_SCAN_DELAY = 2000L
        private const val SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION = 100L
        private const val LONG_CUTTING_DELAY = 10000L
    }

    val previousSensorReads = mutableListOf<Pair<Instant, Boolean>>() //time, sensor state
    var previousStateSensorHigh = false
    var previousStateSensorLow = false
    suspend fun transition(input: Input) {
        when (input) {
            ContrastSensorHigh -> {
                if (previousStateSensorLow) {
                    previousSensorReads.add(
                        Instant.now() to true
                    )
                }
            }

            ContrastSensorLow -> {
                if (previousStateSensorHigh || !previousStateSensorLow) previousSensorReads.add(Instant.now() to false)
                onLowDetected()
            }

            CutterEndDetected -> {
                actionJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION)
                    _currentState.value = State.CUT_TOWARDS_START
                    delay(LONG_CUTTING_DELAY)
                    if (_currentState.value == State.CUT_TOWARDS_START) {
                        _currentState.value = State.STOP
                    }
                }
            }

            CutterStartDetected -> {
                _currentState.value = State.FORWARD
            }

            StartEntered -> _currentState.value = State.FORWARD
            StopEntered -> {
                _currentState.value = State.STOP
                previousSensorReads.clear()
                previousStateSensorLow = false
                previousStateSensorHigh = false
            }
        }
        previousStateSensorHigh = input == ContrastSensorHigh
        previousStateSensorLow = input == ContrastSensorLow
    }

    private fun onLowDetected() {

        //h         ---------        --------
        //
        //l   *-----         --------        ---------
        //    t0    t1       t2     t3      t4     t5
        val lowerBoundForTimeBetweenSwitches = 0
        val upperBoundForTimeBetweenSwitches = DEFAULT_PRINT_MARK_SCAN_DELAY
        if (previousSensorReads.count() < 5) {
            return
        }
        if (previousSensorReads.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            return
        }
        val shouldStartCutting = previousSensorReads.takeLast(5)
            .drop(1)
            .windowed(2, 1, false)
            .map { it[0] to it[1] }
            .map { (t1, t2) -> t2.first.toEpochMilli() - t1.first.toEpochMilli() }
            .all { timeBetween -> timeBetween in lowerBoundForTimeBetweenSwitches..upperBoundForTimeBetweenSwitches }
        if (!shouldStartCutting) {
            previousSensorReads.removeAt(0)
            previousSensorReads.removeAt(1)
        } else {
            previousSensorReads.clear()
            _currentState.value = State.CUT_TOWARDS_END
            actionJob?.cancel()
            actionJob = CoroutineScope(Dispatchers.Main).launch {
                delay(LONG_CUTTING_DELAY)
                if (_currentState.value == State.CUT_TOWARDS_END) {
                    _currentState.value = State.STOP
                }
            }
        }
    }
}