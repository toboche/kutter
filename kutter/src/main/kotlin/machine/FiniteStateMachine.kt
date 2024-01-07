package machine

import input.Input
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import output.states.State
import transitions.Transition

class FiniteStateMachine {
    private val _currentState = MutableStateFlow(State.STOP)
    val currentState = _currentState.asStateFlow()
    var actionJob: Job? = null

    suspend fun transition(input: Input) {
        val transition = Transition.entries.firstOrNull {
            it.startState == _currentState.value && it.input == input
        } ?: Transition.entries.firstOrNull { it.startState == null && it.input == input }
        if (transition != null) {
            val previousState = _currentState.value
            _currentState.value = transition.destinationState
            // cancel previous job
            actionJob?.cancel()
            // start new job
            actionJob = CoroutineScope(Dispatchers.Main).launch {
                transition.onTransitionAction?.invoke(this@FiniteStateMachine)
            }
        }
    }
}