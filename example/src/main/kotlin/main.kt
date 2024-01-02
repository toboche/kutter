import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance
import com.pi4j.ktx.console
import com.pi4j.ktx.io.digital.*
import com.pi4j.ktx.pi4jAsync
import input.*
import kotlinx.coroutines.*
import machine.FiniteStateMachine
import output.states.State

private const val CONRTAST_SENSOR_BCM_PIN = 17
private const val PIN_LED = 22 // PIN 15 = BCM 22

private val finiteStateMachine = FiniteStateMachine()

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kutter",
        state = rememberWindowState(width = 300.dp, height = 300.dp)
    ) {
        val count = remember { mutableStateOf(0) }
        println("start")
        MaterialTheme {
            GlobalScope.launch {
                pi4jAsync {
                    console {
                        digitalInput(CONRTAST_SENSOR_BCM_PIN) {
                            id("button")
                            name("Press button")
                            pull(PullResistance.PULL_DOWN)
                            debounce(3000L)
                            piGpioProvider()
                        }.onLow {
                            count.value = count.value + 1
                            +"marker was detected for the ${count.value}th time"
                        }.onHigh {
                            +"marker end was detected for the ${count.value}th time"
                        }

                        digitalOutput(PIN_LED) {
                            id("led")
                            name("LED Flasher")
                            shutdown(DigitalState.LOW)
                            initial(DigitalState.LOW)
                            piGpioProvider()
                        }.run {
                            while (true) {
                                +"LED ${state()}"
                                toggle()
                                delay(500L)
                            }
                        }
                    }
                }
            }
            val currentState = finiteStateMachine.currentState.collectAsState()
            Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            when (currentState.value) {
                                State.STOP ->
                                    finiteStateMachine.transition(input = StartEntered)

                                else -> finiteStateMachine.transition(input = StopEntered)
                            }
                        }
                    }) {
                    Text(
                        when (currentState.value) {
                            State.STOP -> "Start"
                            else -> "Stop"
                        }
                    )
                }
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = ContrastSensorHigh)
                        }
                    }) {
                    Text("Sensor kontrastu HIGH")
                }
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = ContrastSensorLow)
                        }
                    }) {
                    Text("Sensor kontrastu LOW")
                }
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = CutterEndDetected)
                        }
                    }) {
                    Text("Koniec wykryty")
                }
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        CoroutineScope(Dispatchers.Main).launch {
                            finiteStateMachine.transition(input = CutterStartDetected)
                        }
                    }) {
                    Text("Poczatek wykryty")
                }
                Text("Stan: ${currentState.value}")
            }
        }
    }
}