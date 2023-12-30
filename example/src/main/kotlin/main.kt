import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance
import com.pi4j.ktx.console
import com.pi4j.ktx.io.digital.digitalInput
import com.pi4j.ktx.io.digital.digitalOutput
import com.pi4j.ktx.io.digital.onLow
import com.pi4j.ktx.io.digital.piGpioProvider
import com.pi4j.ktx.pi4jAsync
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PIN_BUTTON = 17
private const val PIN_LED = 22 // PIN 15 = BCM 22

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Compose for Desktop",
        state = rememberWindowState(width = 300.dp, height = 300.dp)
    ) {
        val count = remember { mutableStateOf(0) }
        val scope = rememberCoroutineScope()
        println("start")
        MaterialTheme {
            GlobalScope.launch {
                pi4jAsync {
                    console {
                        digitalInput(PIN_BUTTON) {
                            id("button")
                            name("Press button")
                            pull(PullResistance.PULL_DOWN)
                            debounce(3000L)
                            piGpioProvider()
                        }.onLow {
                            count.value = count.value + 1
                            +"Button was pressed for the ${count.value}th time"
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
            Column(Modifier.fillMaxSize(), Arrangement.spacedBy(5.dp)) {
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        count.value++
                    }) {
                    Text(if (count.value == 0) "Hello World" else "Clicked ${count.value}!")
                }
                Button(modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = {
                        count.value = 0
                    }) {
                    Text("Reset")
                }
            }
        }
    }
}