package machine

import input.Input
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import output.states.State
import transitions.Transition

class FiniteStateMachine {
    private val _currentState = MutableStateFlow(State.STOP)
    val currentState = _currentState.asStateFlow()

    fun transition(input: Input) {
        val transition = Transition.entries.firstOrNull {
            it.startState == _currentState.value && it.input == input
        } ?: Transition.entries.firstOrNull { it.input == input }
        if (transition != null) {
            _currentState.value = transition.destinationState
        }
    }
}