package machine

import input.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import output.states.State
import java.time.Instant
import java.time.temporal.ChronoUnit

class FiniteStateMachine {
    private val _currentState = MutableStateFlow(State.STOP)
    val currentState = _currentState.asStateFlow()
    private var _actionJob = MutableStateFlow<Job?>(null)
    val actionJob = _actionJob.asStateFlow()
    private val _previousSensorReads = MutableStateFlow(listOf<Pair<Instant, Boolean>>()) //time, sensor state
    val previousSensorReads = _previousSensorReads.asStateFlow()
    private var _currentStateSensor = MutableStateFlow(false)
    val previousStateSensor = _currentStateSensor.asStateFlow()
    private var _calibrationMode = MutableStateFlow(false)
    val calibrationMode = _calibrationMode.asStateFlow()
    private var _averageTimeBetweenContrastStateTransitions = MutableStateFlow(
        Triple(
            DEFAULT_PRINT_MARK_SCAN_DELAY,
            DEFAULT_PRINT_MARK_SCAN_DELAY,
            DEFAULT_PRINT_MARK_SCAN_DELAY
        )
    )
    val averageTimeBetweenContrastStateTransitions = _averageTimeBetweenContrastStateTransitions.asStateFlow()
    private var _accuracy = MutableStateFlow(Triple(1.0, 1.0, 1.0))
    val accuracy = _accuracy.asStateFlow()
    private val _manualOverride = MutableStateFlow(false)
    val manualOverride = _manualOverride.asStateFlow()
    var _cuttingState = MutableStateFlow(CuttingState.None)
    enum class CuttingState {
        None,
        Cutting,
    }
    fun transition(input: Input) {
        if (input == ContrastSensorLow) {
            _currentStateSensor.value = false
        }
        if (input == ContrastSensorHigh) {
            _currentStateSensor.value = true
        }
        if (_calibrationMode.value) {
            handleInputForCalibrationMode(input)
        } else if (_manualOverride.value) {
            handleInputForManualOverrideMode(input)
        } else {
            handleInputForNonCalibrationMode(input)
        }

    }

    private fun handleInputForManualOverrideMode(input: Input) {
        when (input) {
            MoveBackwardsEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.BACKWARD)
            }

            MoveForwardEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.FORWARD)
            }

            MoveTowardsStartEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_START)
            }

            MoveTowardsEndEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_END)
            }

            StopEntered -> {
                _currentState.value = State.STOP
                _manualOverride.value = false
            }

            CutterEndDetected -> {
                _currentState.value = State.STOP
                _manualOverride.value = false
            }

            CutterStartDetected -> {
                _currentState.value = State.STOP
                _manualOverride.value = false
            }

            else -> {}
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

            MoveBackwardsEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.BACKWARD)
            }

            MoveForwardEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.FORWARD)
            }

            MoveTowardsStartEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_START)
            }

            MoveTowardsEndEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_END)
            }
        }
    }

    private fun updateStateWithDelay(state: State) {
        _currentState.value = State.STOP
        CoroutineScope(Dispatchers.Main).launch {
            delay(SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION)
            _currentState.value = state
        }
    }

    private fun handleInputForNonCalibrationMode(input: Input) {
        when (input) {
            ContrastSensorHigh -> {
                _previousSensorReads.value = _previousSensorReads.value + (Instant.now() to true)
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
                val averageTimeBetweenContrastTransitions =
                    _averageTimeBetweenContrastStateTransitions.value.toList().average()
                val timeToGoDownBeforeCut = averageTimeBetweenContrastTransitions * 7
                _currentState.value = State.STOP
                _actionJob.value = CoroutineScope(Dispatchers.Main).launch {
                    delay(SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION)
                    _currentState.value = State.FORWARD
                    delay((timeToGoDownBeforeCut / 1000).toLong())
                    _currentState.value = State.CUT_TOWARDS_START
                    _cuttingState.value = CuttingState.None
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
                _cuttingState.value = CuttingState.None
                _previousSensorReads.value = emptyList()
                moveForwardReadingCurrentSensorState()
            }

            StopEntered -> {
                _currentState.value = State.STOP
                _previousSensorReads.value = emptyList()
            }

            CalibrationEntered -> {
                startCalibrationMode()
            }

            MoveBackwardsEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.BACKWARD)
            }

            MoveForwardEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.FORWARD)
            }

            MoveTowardsStartEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_START)
            }

            MoveTowardsEndEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_END)
            }
        }
    }

    private fun startCalibrationMode() {
        _calibrationMode.value = true
        _previousSensorReads.value = listOf(Instant.now() to _currentStateSensor.value)
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
        _previousSensorReads.value = listOf(Instant.now() to _currentStateSensor.value)
        _actionJob.value?.cancel()
    }

    private fun onLowDetected() {

        //h         ---------        --------
        //
        //l   *-----         --------        ---------
        //    t0    t1       t2     t3      t4     t5

        if (_cuttingState.value == CuttingState.Cutting) {
            return
        }
        if (_previousSensorReads.value.count() < 5) {
            return
        }
        if (_currentState.value == State.CUT_TOWARDS_END || _currentState.value == State.CUT_TOWARDS_START || _currentState.value == State.PAUSE_BEFORE_CUTS) {
            return
        }
        if (_previousSensorReads.value.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            return
        }
        val collectedValues = (_previousSensorReads.value.takeLast(5)
            .drop(1)
            .windowed(2, 1, false)
            .map { it[0] to it[1] }
            .map { (t1, t2) -> ChronoUnit.MICROS.between(t1.first, t2.first).toDouble() }
            .take(3))
        val collectedValuesToCheck = Triple(collectedValues[0], collectedValues[1], collectedValues[2])
        val shouldStartCutting =
            collectedValuesToCheck.toList().withIndex().all { (index, measurement) ->
                val lowerBoundForTimeBetweenSwitches =
                    _averageTimeBetweenContrastStateTransitions.value.toList()[index] * (1 - _accuracy.value.toList()[index])
                val upperBoundForTimeBetweenSwitches =
                    _averageTimeBetweenContrastStateTransitions.value.toList()[index] * (1 + _accuracy.value.toList()[index])
                measurement in lowerBoundForTimeBetweenSwitches..upperBoundForTimeBetweenSwitches
            }

        if (!shouldStartCutting) {
            _previousSensorReads.value = _previousSensorReads.value.drop(2)
            println("----------------rejecting-----------------")
            println(collectedValuesToCheck.toList().map { "\n $it" })
            println("----------------ended rejecting-----------------")
            return
        }

        _previousSensorReads.value = emptyList()
        _actionJob.value?.cancel()

        if (_cuttingState.value == CuttingState.None) {

            _cuttingState.value = CuttingState.Cutting
            val averageTimeBetweenContrastTransitions =
                _averageTimeBetweenContrastStateTransitions.value.toList().average()
            val timeToGoDownBeforeCut = -(averageTimeBetweenContrastTransitions * 5) + (DISTANCE_BETWEEN_CONTRAST_SENSOR_AND_KNIFE * averageTimeBetweenContrastTransitions)
            _currentState.value = State.STOP
            _actionJob.value = CoroutineScope(Dispatchers.Main).launch {
                delay(SHORT_DELAY_BEFORE_MOVING_OPPOSITE_DIRECTION)
                _currentState.value = State.FORWARD
                delay((timeToGoDownBeforeCut / 1000).toLong())
                _currentState.value = State.CUT_TOWARDS_END
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
        val collectedValues = (_previousSensorReads.value.takeLast(5)
            .drop(1)
            .windowed(2, 1, false)
            .map { it[0] to it[1] }
            .map { (t1, t2) -> ChronoUnit.MICROS.between(t1.first, t2.first).toDouble() }
            .take(3))

        _averageTimeBetweenContrastStateTransitions.value =
            Triple(collectedValues[0], collectedValues[1], collectedValues[2])
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
        private const val DISTANCE_BETWEEN_CONTRAST_SENSOR_AND_KNIFE = 8
    }
}