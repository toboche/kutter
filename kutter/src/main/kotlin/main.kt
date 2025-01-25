import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance
import com.pi4j.ktx.console
import com.pi4j.ktx.io.digital.*
import com.pi4j.ktx.pi4jAsync
import input.*
import kotlinx.coroutines.*
import machine.FiniteStateMachine
import output.states.*

//these are all GPIO PINS (not "just" pin numbers)
private const val CONTRAST_SENSOR_BCM_PIN = 17
private const val START_SENSOR_BCM_PIN = 10
private const val END_SENSOR_BCM_PIN = 11

private const val MAIN_MOTOR_PIN_1 = 27
private const val MAIN_MOTOR_PIN_2 = 22
private const val MAIN_MOTOR_PIN_3 = 21
private const val CUTTER_MOTOR_PIN_1 = 23
private const val CUTTER_MOTOR_PIN_2 = 24

//private const val ACTUATOR_PIN_1 = 9 // do nt use 9, it's fried
private const val ELECTROMAGNET_ACTUATOR_PIN_1 = 8
private const val ELECTROMAGNET_ACTUATOR_PIN_2 = 7

private val finiteStateMachine = FiniteStateMachine()

fun main() = application() {
    GlobalScope.launch {
        pi4jAsync {
            console {
                subscribeToContrastSensorInput()
                subscribeToStartSensorInput()
                subscribeToEndSensorInput()

                val mainMotorPin1 = createMotorGpioOutput(MAIN_MOTOR_PIN_1, DigitalState.HIGH)
                val mainMotorPin2 = createMotorGpioOutput(MAIN_MOTOR_PIN_2, DigitalState.HIGH)
                val mainMotorEnablingPin = createMotorGpioOutput(MAIN_MOTOR_PIN_3, DigitalState.LOW)
                val cutterMotorPin1 = createMotorGpioOutput(CUTTER_MOTOR_PIN_1, DigitalState.LOW)
                val cutterMotorPin2 = createMotorGpioOutput(CUTTER_MOTOR_PIN_2, DigitalState.LOW)
                val electromagnetActuatorPin1 = createMotorGpioOutput(ELECTROMAGNET_ACTUATOR_PIN_1, DigitalState.LOW)
                val electromagnetActuatorPin2 = createMotorGpioOutput(ELECTROMAGNET_ACTUATOR_PIN_2, DigitalState.LOW)
                var previousState: OutputState? = null
                while (true) {
                    val state = finiteStateMachine.currentState.value.outputState
                    when (state.cutterMotorState) {
                        CutterMotorState.LEFT -> {
                            cutterMotorPin1.high()
                            cutterMotorPin2.low()
                        }

                        CutterMotorState.RIGHT -> {
                            cutterMotorPin1.low()
                            cutterMotorPin2.high()
                        }

                        CutterMotorState.NONE -> {
                            cutterMotorPin1.low()
                            cutterMotorPin2.low()
                        }
                    }
                    when (state.electromagnetSate) {
                        HoldingMotorState.UP -> {
                            electromagnetActuatorPin1.low()
                            electromagnetActuatorPin2.high()
                        }

                        HoldingMotorState.DOWN -> {
                            electromagnetActuatorPin1.high()
                            electromagnetActuatorPin2.low()
                        }

                        HoldingMotorState.NONE -> {
                            electromagnetActuatorPin1.low()
                            electromagnetActuatorPin2.low()
                        }
                    }
                    if (previousState == state) {
                        continue
                    }
                    previousState = state
                    when (state.mainMotorState) {
                        MainMotorState.NONE -> {
                            stopMainMotorOutputAndDelay100ms(mainMotorEnablingPin, mainMotorPin1, mainMotorPin2)
                        }

                        MainMotorState.FORWARD -> {
                            stopMainMotorOutputAndDelay100ms(mainMotorEnablingPin, mainMotorPin1, mainMotorPin2)
                            if (mainMotorEnablingPin.isLow) {
                                println("skipping enabling main motor sth went wrong")
                            }
                            mainMotorPin1.high()
                            mainMotorPin2.low()
                            mainMotorEnablingPin.low()
                        }

                        MainMotorState.BACKWARDS -> {
                            stopMainMotorOutputAndDelay100ms(mainMotorEnablingPin, mainMotorPin1, mainMotorPin2)
                            if (mainMotorEnablingPin.isLow) {
                                println("skipping enabling main motor sth went wrong")
                            }
                            mainMotorPin1.low()
                            mainMotorPin2.high()
                            mainMotorEnablingPin.low()
                        }
                    }
                }
            }
        }
    }

    val keyboardKeyMappings = mapOf(
        Key.DirectionLeft to MoveTowardsEndEntered,
        Key.DirectionRight to MoveTowardsStartEntered,
        Key.DirectionDown to MoveForwardEntered,
        Key.DirectionUp to MoveBackwardsEntered,
        Key.Spacebar to StopEntered,
        Key.S to StartEntered,
        Key.K to CalibrationEntered,
        Key.D to HoldingMotorEnteredDown,
        Key.G to HoldingMotorEnteredUp,
        Key.F to ForceStartCuttingEntered,
    )

    Window(
        onCloseRequest = ::exitApplication, title = "Kutter", state = rememberWindowState(height = 1000.dp),
        onKeyEvent = {
            if (
                keyboardKeyMappings.containsKey(it.key) &&
                it.type == KeyEventType.KeyDown
            ) {
                finiteStateMachine.transition(keyboardKeyMappings[it.key]!!)
                true
            } else {
                false
            }
        }
    ) {
        MaterialTheme {
            val currentState = finiteStateMachine.currentState.collectAsState()
//            val previousSensorReads = finiteStateMachine.previousSensorReads.collectAsState()
            val previousStateSensor = finiteStateMachine.previousStateSensor.collectAsState()
            val calibrationMode = finiteStateMachine.calibrationMode.collectAsState()
            val averageTimeBetweenContrastStateTransitions =
                finiteStateMachine.averageTimeBetweenContrastStateTransitions.collectAsState()
            val accuracy = finiteStateMachine.accuracy.collectAsState()

            Row(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                Column {
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            when (currentState.value) {
                                State.STOP -> finiteStateMachine.transition(input = StartEntered)
                                else -> finiteStateMachine.transition(input = StopEntered)
                            }
                        }
                    }) {
                        Text(
                            when (currentState.value) {
                                State.STOP -> "Start"
                                else -> "Stop (spacja)"
                            }
                        )
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = ContrastSensorHigh)
                        }
                    }) {
                        Text("Sensor kontrastu HIGH")
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = ContrastSensorLow)
                        }
                    }) {
                        Text("Sensor kontrastu LOW")
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = CutterEndDetected)
                        }
                    }) {
                        Text("Koniec wykryty")
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = CutterStartDetected)
                        }
                    }) {
                        Text("Poczatek wykryty")
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = CalibrationEntered)
                        }
                    }) {
                        Text("Rozpocznij kalibrację")
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = HoldingMotorEnteredDown)
                        }
                    }) {
                        Text("Silniki trzymajace w dol")
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = HoldingMotorEnteredUp)
                        }
                    }) {
                        Text("Silniki trzymajace w gore")
                    }
                }
                Column {
                    Spacer(modifier = Modifier.size(30.dp))
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = MoveBackwardsEntered)
                        }
                    }) {
                        Text("Odwijanie do tylu⬆️")
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Button(onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                finiteStateMachine.transition(input = MoveTowardsEndEntered)
                            }
                        }) {
                            Text("Karetka do do konca⬅️")
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Button(onClick = {
                            CoroutineScope(Dispatchers.Main).launch {
                                finiteStateMachine.transition(input = MoveTowardsStartEntered)
                            }
                        }) {
                            Text("➡️Karetka do poczatku")
                        }
                    }
                    Button(modifier = Modifier.align(Alignment.CenterHorizontally), onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = MoveForwardEntered)
                        }
                    }) {
                        Text("Odwijanie do przodu⬇️")
                    }
                    Text("Stan: ${currentState.value}")
//                    Text("Poprzednie odczyty sensora kontrastu: ${previousSensorReads.value.map { "\n $it" }}")
                    Text(
                        "Sensor w stanie niskim: ${
                            if (previousStateSensor.value) {
                                "wysoki"
                            } else "niski"
                        }}"
                    )
                    Text("Tryb kalibracji: ${formatBool(calibrationMode)}")
                    Text("Średni czas pomiędzy zmianami stanu [milisekundy]:")
                    Text("${averageTimeBetweenContrastStateTransitions.value}")
                    Text("Dokladność: ${accuracy.value}")
//                    println("Średni czas pomiędzy zmianami stanu [milisekundy]: ${averageTimeBetweenContrastStateTransitions.value}")
//                    println("Dokladność: ${accuracy.value}")

                }
            }
        }
    }
}

private suspend fun stopMainMotorOutputAndDelay100ms(
    mainMotorEnablingPin: DigitalOutput,
    mainMotorPin1: DigitalOutput,
    mainMotorPin2: DigitalOutput,
) {
    mainMotorEnablingPin.high()
    delay(50)
    mainMotorPin1.high()
    mainMotorPin2.high()
    delay(50)
}

private fun formatBool(previousStateSensorHigh: androidx.compose.runtime.State<Boolean>) =
    if (previousStateSensorHigh.value) {
        "tak"
    } else {
        "nie"
    }

private fun Context.createMotorGpioOutput(gpioNumber: Int, defaultState: DigitalState) = digitalOutput(gpioNumber) {
    id(gpioNumber.toString())
    name(gpioNumber.toString())
    shutdown(defaultState)
    initial(defaultState)
    piGpioProvider()
}

private fun Context.subscribeToContrastSensorInput() {
    digitalInput(CONTRAST_SENSOR_BCM_PIN) {
        id("CONTRAST_SENSOR_BCM_PIN")
        name("CONTRAST_SENSOR_BCM_PIN")
        pull(PullResistance.PULL_DOWN)
//        debounce(3L)
        piGpioProvider()
    }.onLow {
        finiteStateMachine.transition(input = ContrastSensorLow)
    }.onHigh {
        finiteStateMachine.transition(input = ContrastSensorHigh)
    }
}

private fun Context.subscribeToStartSensorInput() {
    digitalInput(START_SENSOR_BCM_PIN) {
        id("START_SENSOR_BCM_PIN")
        name("START_SENSOR_BCM_PIN")
        pull(PullResistance.PULL_UP)
        debounce(3000L)
        piGpioProvider()
    }.onLow {
        CoroutineScope(Dispatchers.Main).launch {
            finiteStateMachine.transition(input = CutterStartDetected)
        }
    }.onHigh {
    }
}

private fun Context.subscribeToEndSensorInput() {
    digitalInput(END_SENSOR_BCM_PIN) {
        id("END_SENSOR_BCM_PIN")
        name("END_SENSOR_BCM_PIN")
        pull(PullResistance.PULL_UP)
        debounce(3000L)
        piGpioProvider()
    }.onLow {
        CoroutineScope(Dispatchers.Main).launch {
            finiteStateMachine.transition(input = CutterEndDetected)
        }
    }.onHigh {
    }
}