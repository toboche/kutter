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
    private var _actionJob = MutableStateFlow<Job?>(null)
    val actionJob = _actionJob.asStateFlow()
    private val _previousSensorReads = MutableStateFlow(listOf<Pair<Instant, Boolean>>()) //time, sensor state
    val previousSensorReads = _previousSensorReads.asStateFlow()
    private var _previousStateSensor = MutableStateFlow(false)
    val previousStateSensor = _previousStateSensor.asStateFlow()
    private var _calibrationMode = MutableStateFlow(false)
    val calibrationMode = _calibrationMode.asStateFlow()
    private var _averageTimeBetweenContrastStateTransitions = MutableStateFlow(DEFAULT_PRINT_MARK_SCAN_DELAY)
    val averageTimeBetweenContrastStateTransitions = _averageTimeBetweenContrastStateTransitions.asStateFlow()
    private var _accuracy = MutableStateFlow(0.9)
    val accuracy = _accuracy.asStateFlow()
    suspend fun transition(input: Input) {
        if (_calibrationMode.value) {
            handleInputForCalibrationMode(input)

        } else {
            handleInputForNonCalibrationMode(input)
        }
        if (input == ContrastSensorLow) {
            _previousStateSensor.value = true
        }
        if (input == ContrastSensorHigh) {
            _previousStateSensor.value = false
        }
    }

    private fun handleInputForCalibrationMode(input: Input) {
        when (input) {
            CalibrationEntered -> {
                startCalibrationMode()
            }

            ContrastSensorHigh -> {
                _previousSensorReads.value = _previousSensorReads.value + (Instant.now() to true)
            }

            ContrastSensorLow -> {
                _previousSensorReads.value = _previousSensorReads.value + (Instant.now() to false)
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
                if (_previousStateSensor.value) {
                    _previousSensorReads.value = _previousSensorReads.value + (Instant.now() to true)
                }
            }

            ContrastSensorLow -> {
                _previousSensorReads.value = _previousSensorReads.value + (Instant.now() to false)
                onLowDetected()
            }

            CutterEndDetected -> {
                if (_currentState.value != State.CUT_TOWARDS_END) {
                    _currentState.value = State.STOP
                    return
                }
                _currentState.value = State.PAUSE_BEFORE_CUTS
                _actionJob.value = CoroutineScope(Dispatchers.Main).launch {
                    delay(SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION)
                    _currentState.value = State.CUT_TOWARDS_START
                    delay(LONG_CUTTING_DELAY.toLong())
                    if (_currentState.value == State.CUT_TOWARDS_START) {
                        _currentState.value = State.STOP
                    }
                }
            }

            CutterStartDetected -> {
                if (_currentState.value != State.CUT_TOWARDS_START) {
                    _currentState.value = State.STOP
                    return
                }
                moveForwardReadingCurrentSensorState()
            }

            StartEntered -> {
                moveForwardReadingCurrentSensorState()
            }

            StopEntered -> {
                _currentState.value = State.STOP
                _previousSensorReads.value = emptyList()
            }

            CalibrationEntered -> {
                startCalibrationMode()
            }
        }
    }

    private fun startCalibrationMode() {
        _calibrationMode.value = true
        _previousSensorReads.value = emptyList()
        moveForwardReadingCurrentSensorState()
        _actionJob.value = CoroutineScope(Dispatchers.Main).launch {
            delay(CALIBRATION_DELAY.toLong())
            if (_currentState.value == State.FORWARD && _calibrationMode.value) {
                _currentState.value = State.STOP
            }
        }
    }

    private fun moveForwardReadingCurrentSensorState() {
        _currentState.value = State.FORWARD
        _previousSensorReads.value = if (_previousStateSensor.value) listOf(Instant.now() to true)
        else if (!_previousStateSensor.value) listOf(Instant.now() to false)
        else emptyList()
        _actionJob.value?.cancel()
    }

    private fun onLowDetected() {

        //h         ---------        --------
        //
        //l   *-----         --------        ---------
        //    t0    t1       t2     t3      t4     t5
        val lowerBoundForTimeBetweenSwitches =
            _averageTimeBetweenContrastStateTransitions.value * (1 - _accuracy.value)
        val upperBoundForTimeBetweenSwitches =
            _averageTimeBetweenContrastStateTransitions.value * (1 + _accuracy.value)
        if (_previousSensorReads.value.count() < 5) {
            return
        }
        if (_currentState.value == State.CUT_TOWARDS_END || _currentState.value == State.CUT_TOWARDS_END || _currentState.value == State.PAUSE_BEFORE_CUTS) {
            return
        }
        if (_previousSensorReads.value.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            return
        }
        val shouldStartCutting = _previousSensorReads.value.takeLast(5)
            .drop(1)
            .windowed(2, 1, false)
            .map { it[0] to it[1] }
            .map { (t1, t2) -> t2.first.toEpochMilli() - t1.first.toEpochMilli() }
            .all { timeBetween -> timeBetween.toDouble() in lowerBoundForTimeBetweenSwitches..upperBoundForTimeBetweenSwitches }
        if (!shouldStartCutting) {
            _previousSensorReads.value = _previousSensorReads.value.drop(2)
        } else {
            _previousSensorReads.value = emptyList()
            _currentState.value = State.CUT_TOWARDS_END
            _actionJob.value?.cancel()
            _actionJob.value = CoroutineScope(Dispatchers.Main).launch {
                delay(LONG_CUTTING_DELAY.toLong())
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
        if (_previousSensorReads.value.count() < 5) {
            return
        }
        if (_previousSensorReads.value.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            return
        }
        _averageTimeBetweenContrastStateTransitions.value = (_previousSensorReads.value.takeLast(5)
            .drop(1)
            .windowed(2, 1, false)
            .map { it[0] to it[1] }
            .map { (t1, t2) -> t2.first.toEpochMilli() - t1.first.toEpochMilli() }
            .average() / 4.toDouble())
        _previousSensorReads.value = emptyList()
        _currentState.value = State.STOP
        _calibrationMode.value = false
        _actionJob.value?.cancel()
    }


    companion object {
        //TODO: calibrate these values or make them editable by the user
        private const val DEFAULT_PRINT_MARK_SCAN_DELAY = 2000.toDouble()
        private const val SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION = 100L
        private const val LONG_CUTTING_DELAY = 10000.toDouble()
        private const val CALIBRATION_DELAY = 10000.toDouble()
    }
}