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
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance
import com.pi4j.ktx.console
import com.pi4j.ktx.io.digital.*
import com.pi4j.ktx.pi4jAsync
import input.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import machine.FiniteStateMachine
import output.states.CutterMotorState
import output.states.MainMotorState
import output.states.OutputState
import output.states.State

private const val CONTRAST_SENSOR_BCM_PIN = 17
private const val START_SENSOR_BCM_PIN = 10
private const val END_SENSOR_BCM_PIN = 11

private const val MAIN_MOTOR_PIN_1 = 27
private const val MAIN_MOTOR_PIN_2 = 22
private const val CUTTER_MOTOR_PIN_1 = 23
private const val CUTTER_MOTOR_PIN_2 = 24

private val finiteStateMachine = FiniteStateMachine()

fun main() = application() {
    GlobalScope.launch {
        pi4jAsync {
            console {
                subscribeToContrastSensorInput()
                subscribeToStartSensorInput()
                subscribeToEndSensorInput()

                val mainMotorPin1 = createMotorGpioOutput(MAIN_MOTOR_PIN_1)
                val mainMotorPin2 = createMotorGpioOutput(MAIN_MOTOR_PIN_2)
                val cutterMotorPin1 = createMotorGpioOutput(CUTTER_MOTOR_PIN_1)
                val cutterMotorPin2 = createMotorGpioOutput(CUTTER_MOTOR_PIN_2)
                var previousState: OutputState? = null
                while (true) {
                    val state = finiteStateMachine.currentState.value.outputState
                    if (previousState == state) {
                        continue
                    }
                    previousState = state
                    when (state.mainMotorState) {
                        MainMotorState.NONE -> {
                            mainMotorPin1.low()
                            mainMotorPin2.low()
                        }

                        MainMotorState.FORWARD -> {
                            mainMotorPin1.low()
                            mainMotorPin2.high()
                        }

                        MainMotorState.BACKWARDS -> {
                            mainMotorPin1.high()
                            mainMotorPin2.low()
                        }
                    }
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
        Key.Enter to StartEntered,
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
            val previousSensorReads = finiteStateMachine.previousSensorReads.collectAsState()
            val previousStateSensor = finiteStateMachine.previousStateSensor.collectAsState()
            val calibrationMode = finiteStateMachine.calibrationMode.collectAsState()
            val averageTimeBetweenContrastStateTransitions =
                finiteStateMachine.averageTimeBetweenContrastStateTransitions.collectAsState()
            val accuracy = finiteStateMachine.accuracy.collectAsState()

            Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
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
                Text("Poprzednie odczyty sensora kontrastu: ${previousSensorReads.value.map { "\n $it" }}")
                Text(
                    "Sensor w stanie niskim: ${
                        if (previousStateSensor.value) {
                            "wysoki"
                        } else "niski"
                    }}"
                )
                Text("Tryb kalibracji: ${formatBool(calibrationMode)}")
                Text("Średni czas pomiędzy zmianami stanu [milisekundy]: ${averageTimeBetweenContrastStateTransitions.value}")
                Text("Dokladność: ${accuracy.value}")
            }
        }
    }
}

private fun formatBool(previousStateSensorHigh: androidx.compose.runtime.State<Boolean>) =
    if (previousStateSensorHigh.value) {
        "tak"
    } else {
        "nie"
    }

private fun Context.createMotorGpioOutput(gpioNumber: Int) = digitalOutput(gpioNumber) {
    id(gpioNumber.toString())
    name(gpioNumber.toString())
    shutdown(DigitalState.LOW)
    initial(DigitalState.LOW)
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
    }.onHigh {
        CoroutineScope(Dispatchers.Main).launch {
            finiteStateMachine.transition(input = CutterStartDetected)
        }
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