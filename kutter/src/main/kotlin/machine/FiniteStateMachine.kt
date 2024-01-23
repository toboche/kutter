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
    private var actionJob: Job? = null
    private val previousSensorReads = mutableListOf<Pair<Instant, Boolean>>() //time, sensor state
    private var previousStateSensorHigh = false
    private var previousStateSensorLow = false
    private var calibrationMode = false
    private var averageTimeBetweenContrastStateTransitions = DEFAULT_PRINT_MARK_SCAN_DELAY
    private var accuracy = 0.2
    suspend fun transition(input: Input) {
        if (calibrationMode) {
            handleInputForCalibrationMode(input)

        } else {
            handleInputForNonCalibrationMode(input)
        }
        previousStateSensorHigh = input == ContrastSensorHigh
        previousStateSensorLow = input == ContrastSensorLow
    }

    private fun handleInputForCalibrationMode(input: Input) {
        when (input) {
            CalibrationEntered -> {}
            ContrastSensorHigh -> {
                if (previousStateSensorLow) {
                    previousSensorReads.add(
                        Instant.now() to true
                    )
                }
            }

            ContrastSensorLow -> {
                if (previousStateSensorHigh || !previousStateSensorLow) previousSensorReads.add(Instant.now() to false)
                onLowDetectedInCalibrationMode()
            }

            CutterEndDetected -> {}
            CutterStartDetected -> {}
            StartEntered -> {}
            StopEntered -> {
                _currentState.value = State.STOP
            }
        }
    }

    private fun handleInputForNonCalibrationMode(input: Input) {
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

            CalibrationEntered -> {
                calibrationMode = true
                _currentState.value = State.FORWARD
                actionJob?.cancel()
                actionJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(CALIBRATION_DELAY)
                    if (_currentState.value == State.FORWARD && calibrationMode) {
                        _currentState.value = State.STOP
                    }
                }

            }
        }
    }

    private fun onLowDetected() {

        //h         ---------        --------
        //
        //l   *-----         --------        ---------
        //    t0    t1       t2     t3      t4     t5
        val lowerBoundForTimeBetweenSwitches = averageTimeBetweenContrastStateTransitions * (1 - accuracy).toLong()
        val upperBoundForTimeBetweenSwitches = averageTimeBetweenContrastStateTransitions * (1 + accuracy).toLong()
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

    private fun onLowDetectedInCalibrationMode() {

        //h         ---------        --------
        //
        //l   *-----         --------        ---------
        //    t0    t1       t2     t3      t4     t5
        if (previousSensorReads.count() < 5) {
            return
        }
        if (previousSensorReads.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            return
        }
        averageTimeBetweenContrastStateTransitions = previousSensorReads.takeLast(5)
            .drop(1)
            .windowed(2, 1, false)
            .map { it[0] to it[1] }
            .map { (t1, t2) -> t2.first.toEpochMilli() - t1.first.toEpochMilli() }
            .average().toLong()
        previousSensorReads.clear()
        _currentState.value = State.STOP
        calibrationMode = false
        actionJob?.cancel()
    }


    companion object {
        //TODO: calibrate these values or make them editable by the user
        private const val DEFAULT_PRINT_MARK_SCAN_DELAY = 2000L
        private const val SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION = 100L
        private const val LONG_CUTTING_DELAY = 10000L
        private const val CALIBRATION_DELAY = 10000L
    }
}