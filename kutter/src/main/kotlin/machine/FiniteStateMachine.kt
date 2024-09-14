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
            59048.0, 66774.0, 56829.0
        )
    )
    val averageTimeBetweenContrastStateTransitions = _averageTimeBetweenContrastStateTransitions.asStateFlow()
    private var _accuracy = MutableStateFlow(Triple(.5, .5, .5))
    val accuracy = _accuracy.asStateFlow()
    private val _manualOverride = MutableStateFlow(false)
    val manualOverride = _manualOverride.asStateFlow()
    var _cuttingState = MutableStateFlow(CuttingState.None)

    enum class CuttingState {
        None,
        Cutting,
    }

    init {
        CoroutineScope(Dispatchers.Main).launch {
            _manualOverride.collect {
                if (it) {
                    _cuttingState.value = CuttingState.None
                    _previousSensorReads.value = emptyList()
                }
            }
        }
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
                updateStateWithDelay(State.PAPER_MOVING_BACKWARD)
            }

            MoveForwardEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.PAPER_MOVING_FORWARD)
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
                _cuttingState.value = CuttingState.None
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
                updateStateWithDelay(State.PAPER_MOVING_BACKWARD)
            }

            MoveForwardEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.PAPER_MOVING_FORWARD)
            }

            MoveTowardsStartEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_START)
            }

            MoveTowardsEndEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_END)
            }

            HoldingMotorEnteredDown -> {
                _manualOverride.value = true
                updateStateWithDelay(
                    if (_currentState.value == State.STOP_WITH_ELECTROMAGNET_GOING_DOWN) {
                        State.STOP
                    } else {
                        State.STOP_WITH_ELECTROMAGNET_GOING_DOWN
                    }
                )
            }

            HoldingMotorEnteredUp -> {
                _manualOverride.value = true
                updateStateWithDelay(
                    if (_currentState.value == State.STOP_WITH_ELECTROMAGNET_GOING_UP) {
                        State.STOP
                    } else {
                        State.STOP_WITH_ELECTROMAGNET_GOING_UP
                    }
                )
            }

            ForceStartCuttingEntered -> {
                forceStartCutting()
            }
        }
    }

    private fun updateStateWithDelay(state: State) {
        _currentState.value = State.STOP
        scheduleActionCancelledWhenOtherStarts {
            delay(SHORT_DELAY_BEFORE_CHANGING_MOTOR_MOVEMENT_DIRECTION)
            _currentState.value = state
        }
    }

    private fun scheduleActionCancelledWhenOtherStarts(block: suspend kotlinx.coroutines.CoroutineScope.() -> kotlin.Unit) {
        _actionJob.value?.cancel()
        _actionJob.value = CoroutineScope(Dispatchers.Main).launch {
            block()
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
                val timeToGoDownBeforeCut =
                    averageTimeBetweenContrastTransitions * LENGTH_BETWEEN_CUTTING_LEFT_AND_RIGHT
                _currentState.value = State.STOP
                scheduleActionCancelledWhenOtherStarts {
                    delay(SHORT_DELAY_BEFORE_CHANGING_MOTOR_MOVEMENT_DIRECTION)
                    _currentState.value = State.STOP_WITH_ELECTROMAGNET_GOING_UP
                    delay(DELAY_WHEN_HOLDING_MOTOR_IS_GOING_UP)
                    _currentState.value = State.PAPER_MOVING_FORWARD
                    delay((timeToGoDownBeforeCut / 1000).toLong())
                    _currentState.value = State.STOP_WITH_ELECTROMAGNET_GOING_DOWN
                    delay(DELAY_WHEN_HOLDING_MOTOR_IS_GOING_DOWN)
                    _currentState.value = State.CUT_TOWARDS_START
                    _cuttingState.value = CuttingState.None
                    delay(CUTTING_TIMEOUT.toLong())
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
                scheduleActionCancelledWhenOtherStarts {
                    _currentState.value = State.STOP_WITH_ELECTROMAGNET_GOING_UP
                    delay(DELAY_WHEN_HOLDING_MOTOR_IS_GOING_UP)
                    _cuttingState.value = CuttingState.None
                    moveForwardReadingCurrentSensorState()
                }
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
                updateStateWithDelay(State.PAPER_MOVING_BACKWARD)
            }

            MoveForwardEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.PAPER_MOVING_FORWARD)
            }

            MoveTowardsStartEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_START)
            }

            MoveTowardsEndEntered -> {
                _manualOverride.value = true
                updateStateWithDelay(State.CUT_TOWARDS_END)
            }

            HoldingMotorEnteredDown -> {
                _manualOverride.value = true
                updateStateWithDelay(
                    if (_currentState.value == State.STOP_WITH_ELECTROMAGNET_GOING_DOWN) {
                        State.STOP
                    } else {
                        State.STOP_WITH_ELECTROMAGNET_GOING_DOWN
                    }
                )
            }

            HoldingMotorEnteredUp -> {
                _manualOverride.value = true
                updateStateWithDelay(
                    if (_currentState.value == State.STOP_WITH_ELECTROMAGNET_GOING_UP) {
                        State.STOP
                    } else {
                        State.STOP_WITH_ELECTROMAGNET_GOING_UP
                    }
                )
            }

            ForceStartCuttingEntered -> {
                forceStartCutting()
            }
        }
    }

    private fun startCalibrationMode() {
        _calibrationMode.value = true
        _previousSensorReads.value = listOf(Instant.now() to _currentStateSensor.value)
        moveForwardReadingCurrentSensorState()
        scheduleActionCancelledWhenOtherStarts {
            delay(CALIBRATION_DELAY.toLong())
            if (_currentState.value == State.PAPER_MOVING_FORWARD && _calibrationMode.value) {
                _currentState.value = State.STOP
            }
        }
    }

    private fun moveForwardReadingCurrentSensorState() {
        _currentState.value = State.PAPER_MOVING_FORWARD
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

            forceStartCutting()
        }
    }

    private fun forceStartCutting() {
        _cuttingState.value = CuttingState.Cutting
        val averageTimeBetweenContrastTransitions =
            _averageTimeBetweenContrastStateTransitions.value.toList().average()
        val timeToGoDownBeforeCut =
//            -(averageTimeBetweenContrastTransitions * 7) +
            (DISTANCE_BETWEEN_CONTRAST_SENSOR_AND_KNIFE * averageTimeBetweenContrastTransitions)
        _currentState.value = State.STOP
        scheduleActionCancelledWhenOtherStarts {
            delay(SHORT_DELAY_BEFORE_CHANGING_MOTOR_MOVEMENT_DIRECTION)
            _currentState.value = State.PAPER_MOVING_FORWARD
            delay((timeToGoDownBeforeCut / 1000).toLong())
            _currentState.value = State.STOP_WITH_ELECTROMAGNET_GOING_DOWN
            delay(DELAY_WHEN_HOLDING_MOTOR_IS_GOING_DOWN)
            _currentState.value = State.CUT_TOWARDS_END
            delay(CUTTING_TIMEOUT.toLong())
            if (_currentState.value == State.CUT_TOWARDS_END) {
                _currentState.value = State.STOP
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
        private const val LENGTH_BETWEEN_CUTTING_LEFT_AND_RIGHT = 12
        private const val SHORT_DELAY_BEFORE_CHANGING_MOTOR_MOVEMENT_DIRECTION = 100L
        private const val DELAY_WHEN_HOLDING_MOTOR_IS_GOING_DOWN = 3000L
        private const val DELAY_WHEN_HOLDING_MOTOR_IS_GOING_UP = 2500L

        //when cutting in any of the directions, this time is set as timeout for the caret movement
        private const val CUTTING_TIMEOUT = 10000.toDouble()
        private const val CALIBRATION_DELAY = 10000.toDouble()
        private const val DISTANCE_BETWEEN_CONTRAST_SENSOR_AND_KNIFE = 5
    }
}