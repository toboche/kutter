package machine

import input.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import output.states.State
import telemetry.Logger
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

class FiniteStateMachine {
    private val _currentState = MutableStateFlow(State.STOP)
    val currentState = _currentState.asStateFlow()
    private var _actionJob = MutableStateFlow<Job?>(null)
    val actionJob = _actionJob.asStateFlow()
    private var _previousSensorReads = listOf<Pair<Instant, Boolean>>() //time, sensor state

    //    val previousSensorReads = _previousSensorReads.asStateFlow()
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
    private var _accuracy = MutableStateFlow(Triple(.8, .8, .8))
    val accuracy = _accuracy.asStateFlow()
    private val _manualOverride = MutableStateFlow(false)
    val manualOverride = _manualOverride.asStateFlow()
    var _cuttingState = MutableStateFlow(CuttingState.None)

    enum class CuttingState {
        None, Cutting,
    }

    init {
        CoroutineScope(Dispatchers.Main).launch {

            readAverageTimeBetweenContrastStateTransitionsFromFile()
            _manualOverride.collect {
                if (it) {
                    _cuttingState.value = CuttingState.None
                    _previousSensorReads = emptyList()
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            _currentState.collectLatest {
                Logger.logStateChange(it)
            }
        }
    }

    private fun readAverageTimeBetweenContrastStateTransitionsFromFile() {
        Logger.onStartReadingSettingFromFile()
        try {
            val rawValues = File("cutter_settings.blob").readText().split(";").map { it.toDouble() }

            _averageTimeBetweenContrastStateTransitions.value = Triple(
                rawValues[0],
                rawValues[1],
                rawValues[2],
            )
            Logger.onSettingFromFileRead(_averageTimeBetweenContrastStateTransitions.value)
        } catch (e: Exception) {
        Logger.onErrorReadingSettingFromFile()
            e.printStackTrace()
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
                _actionJob.value?.cancel()
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
                _previousSensorReads = _previousSensorReads + (Instant.now() to true)
            }

            ContrastSensorLow -> {
                _previousSensorReads = _previousSensorReads + (Instant.now() to false)
                onLowDetectedInCalibrationMode()
            }

            CutterEndDetected -> {}
            CutterStartDetected -> {}
            StartEntered -> {}
            StopEntered -> {
                _actionJob.value?.cancel()
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
        Logger.handleInputForNonCalibrationMode(input)
        when (input) {
            ContrastSensorHigh -> {
                if (_cuttingState.value == CuttingState.Cutting) {
                    return
                }
                _previousSensorReads = _previousSensorReads + (Instant.now() to true)
            }

            ContrastSensorLow -> {
                if (_cuttingState.value == CuttingState.Cutting) {
                    return
                }
                _previousSensorReads = _previousSensorReads + (Instant.now() to false)
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
                    _currentState.value = State.PAPER_MOVING_BACKWARD
                    delay(TIME_TO_ROLL_PAPER_BACK_BEORE_CUTTING)
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
                _previousSensorReads = emptyList()
                moveForwardReadingCurrentSensorState()
            }

            StopEntered -> {
                _actionJob.value?.cancel()
                _currentState.value = State.STOP
                _previousSensorReads = emptyList()
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
                setHoldingDownMotorMovementWithTimeOut(State.STOP_WITH_ELECTROMAGNET_GOING_DOWN)
            }

            HoldingMotorEnteredUp -> {
                _manualOverride.value = true
                setHoldingDownMotorMovementWithTimeOut(State.STOP_WITH_ELECTROMAGNET_GOING_UP)
            }

            ForceStartCuttingEntered -> {
                forceStartCutting()
            }
        }
    }

    private fun setHoldingDownMotorMovementWithTimeOut(state: State) {
        scheduleActionCancelledWhenOtherStarts {
            _currentState.value = State.STOP
            delay(SHORT_DELAY_BEFORE_CHANGING_MOTOR_MOVEMENT_DIRECTION)
            _currentState.value = if (_currentState.value == state) {
                State.STOP
            } else {
                state
            }
            delay(MAX_TIME_FOR_FORCED_HOLDING_MOTOR_MOVEMENT)
            if (_currentState.value == state) {
                _currentState.value = State.STOP
            }
        }
    }

    private fun startCalibrationMode() {
        _calibrationMode.value = true
        _previousSensorReads = listOf(Instant.now() to _currentStateSensor.value)
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
        _previousSensorReads = listOf(Instant.now() to _currentStateSensor.value)
        _actionJob.value?.cancel()
    }

    private fun onLowDetected() {

        //h         ---------        --------
        //
        //l   *-----         --------        ---------
        //    t0    t1       t2     t3      t4     t5

        if (_cuttingState.value == CuttingState.Cutting) {
            Logger.onLowDetectedWhileWorking("skipping because cutting")
            return
        }
        if (_previousSensorReads.count() < 5) {
            Logger.onLowDetectedWhileWorking("${_previousSensorReads.count()} less than 5")
            return
        }
        if (_currentState.value == State.CUT_TOWARDS_END || _currentState.value == State.CUT_TOWARDS_START || _currentState.value == State.PAUSE_BEFORE_CUTS) {
            Logger.onLowDetectedWhileWorking("state incorrect")
            return
        }
        if (_previousSensorReads.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            Logger.onLowDetectedWhileWorking("incorrect last 5")
            return
        }
        val collectedValues = (_previousSensorReads.takeLast(5).drop(1).windowed(2, 1, false).map { it[0] to it[1] }
            .map { (t1, t2) -> ChronoUnit.MICROS.between(t1.first, t2.first).toDouble() }.take(3))
        val collectedValuesToCheck = Triple(collectedValues[0], collectedValues[1], collectedValues[2])
        val shouldStartCutting = collectedValuesToCheck.toList().withIndex().all { (index, measurement) ->
            val lowerBoundForTimeBetweenSwitches =
                _averageTimeBetweenContrastStateTransitions.value.toList()[index] * (1 - _accuracy.value.toList()[index])
            val upperBoundForTimeBetweenSwitches =
                _averageTimeBetweenContrastStateTransitions.value.toList()[index] * (1 + _accuracy.value.toList()[index])
            measurement in lowerBoundForTimeBetweenSwitches..upperBoundForTimeBetweenSwitches
        }

        if (!shouldStartCutting) {
//            _previousSensorReads = _previousSensorReads.drop(2)
            println("----------------rejecting-----------------")
            Logger.onLowDetectedWhileWorking("rejecting:\n" + collectedValuesToCheck.toList().map { "\n $it" })
            Logger.onLowDetectedWhileWorking("expected values:\n" + _averageTimeBetweenContrastStateTransitions.value.toList().map { "\n $it" })
            return
        } else {
            Logger.onLowDetectedWhileWorking("accepting:\n" + collectedValuesToCheck.toList().map { "\n $it" })
            Logger.onLowDetectedWhileWorking("expected values:\n" + _averageTimeBetweenContrastStateTransitions.value.toList().map { "\n $it" })
            println(collectedValuesToCheck.toList().map { "\n $it" })
        }

        _previousSensorReads = emptyList()
        _actionJob.value?.cancel()

        Logger.onLowDetectedWhileWorking("checking state before cutting: " + _cuttingState.value)
        if (_cuttingState.value == CuttingState.None) {
        Logger.onLowDetectedWhileWorking("forceStartCutting")
            forceStartCutting()
        }
    }

    private fun forceStartCutting() {
        _cuttingState.value = CuttingState.Cutting
        val averageTimeBetweenContrastTransitions = _averageTimeBetweenContrastStateTransitions.value.toList().average()
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
            _currentState.value = State.PAPER_MOVING_BACKWARD
            delay(TIME_TO_ROLL_PAPER_BACK_BEORE_CUTTING)
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
        if (_previousSensorReads.count() < 5) {
            return
        }
        if (_previousSensorReads.takeLast(5).map { it.second } != listOf(false, true, false, true, false)) {
            return
        }
        val collectedValues = (_previousSensorReads.takeLast(5).drop(1).windowed(2, 1, false).map { it[0] to it[1] }
            .map { (t1, t2) -> ChronoUnit.MICROS.between(t1.first, t2.first).toDouble() }.take(3))

        _averageTimeBetweenContrastStateTransitions.value =
            Triple(collectedValues[0], collectedValues[1], collectedValues[2])
        _currentState.value = State.STOP
        _calibrationMode.value = false
        println("write to cutter_settings.blob")
        try {
            File("cutter_settings.blob").createNewFile()
            val file = File("cutter_settings.blob")
            file.writeText(_averageTimeBetweenContrastStateTransitions.value.toList().map { it.toString() }
                .joinToString(";"))
        } catch (e: Exception) {
            println("couldn't write cutter_settings.blob")
            e.printStackTrace()
        }
        readAverageTimeBetweenContrastStateTransitionsFromFile()
        _actionJob.value?.cancel()
    }

    companion object {
        private const val LENGTH_BETWEEN_CUTTING_LEFT_AND_RIGHT = 22
        private const val SHORT_DELAY_BEFORE_CHANGING_MOTOR_MOVEMENT_DIRECTION = 100L
        private const val TIME_TO_ROLL_PAPER_BACK_BEORE_CUTTING = 150L
        private const val DELAY_WHEN_HOLDING_MOTOR_IS_GOING_DOWN = 2000L
        private const val DELAY_WHEN_HOLDING_MOTOR_IS_GOING_UP = 1800L
        private const val MAX_TIME_FOR_FORCED_HOLDING_MOTOR_MOVEMENT = 3000L

        //when cutting in any of the directions, this time is set as timeout for the caret movement
        private const val CUTTING_TIMEOUT = 10000.toDouble()
        private const val CALIBRATION_DELAY = 10000.toDouble()
        private const val DISTANCE_BETWEEN_CONTRAST_SENSOR_AND_KNIFE = 7
    }
}