package org.elektronnet.mobile.wallet

enum class FirstRunStep {
    Welcome, HowItWorks, Network, Notifications,
    PhraseIntro, PhraseDice, PhraseTouch, PhraseShow, PhraseQuiz, PassphraseNew, Creating,
    RestoreEnter, PassphraseRestore, Restoring, Done,
}

data class FirstRunState(
    val step: FirstRunStep = FirstRunStep.Welcome,
    val restore: Boolean = false,
    val phrase: List<String> = emptyList(),
    val failure: BootstrapFailure? = null,
)

object FirstRunFlow {
    fun next(state: FirstRunState): FirstRunState = when (state.step) {
        FirstRunStep.Welcome -> state.copy(step = FirstRunStep.Network)
        FirstRunStep.HowItWorks -> state.copy(step = FirstRunStep.Welcome)
        FirstRunStep.Network -> state.copy(step = FirstRunStep.Notifications)
        FirstRunStep.Notifications -> state.copy(step = if (state.restore) FirstRunStep.RestoreEnter else FirstRunStep.PhraseIntro)
        FirstRunStep.PhraseIntro, FirstRunStep.PhraseDice, FirstRunStep.PhraseTouch -> state.copy(step = FirstRunStep.PhraseShow)
        FirstRunStep.PhraseShow -> state.copy(step = FirstRunStep.PhraseQuiz)
        FirstRunStep.PhraseQuiz -> state.copy(step = FirstRunStep.PassphraseNew)
        FirstRunStep.PassphraseNew -> state.copy(step = FirstRunStep.Creating, failure = null)
        FirstRunStep.Creating -> state.copy(step = FirstRunStep.Done, phrase = emptyList())
        FirstRunStep.RestoreEnter -> state.copy(step = FirstRunStep.PassphraseRestore)
        FirstRunStep.PassphraseRestore -> state.copy(step = FirstRunStep.Restoring, failure = null)
        FirstRunStep.Restoring -> state.copy(step = FirstRunStep.Done, phrase = emptyList())
        FirstRunStep.Done -> state
    }

    fun back(state: FirstRunState): FirstRunState? = when (state.step) {
        FirstRunStep.Welcome, FirstRunStep.Creating, FirstRunStep.Restoring, FirstRunStep.Done -> null
        FirstRunStep.HowItWorks -> state.copy(step = FirstRunStep.Welcome)
        FirstRunStep.Network -> state.copy(step = FirstRunStep.Welcome, restore = false)
        FirstRunStep.Notifications -> state.copy(step = FirstRunStep.Network)
        FirstRunStep.PhraseIntro -> state.copy(step = FirstRunStep.Notifications)
        FirstRunStep.PhraseDice, FirstRunStep.PhraseTouch -> state.copy(step = FirstRunStep.PhraseIntro)
        FirstRunStep.PhraseShow -> state.copy(step = FirstRunStep.PhraseIntro, phrase = emptyList())
        FirstRunStep.PhraseQuiz -> state.copy(step = FirstRunStep.PhraseShow)
        FirstRunStep.PassphraseNew -> state.copy(step = FirstRunStep.PhraseQuiz)
        FirstRunStep.RestoreEnter -> state.copy(step = FirstRunStep.Notifications)
        FirstRunStep.PassphraseRestore -> state.copy(step = FirstRunStep.RestoreEnter, phrase = emptyList())
    }

    fun failed(state: FirstRunState, failure: BootstrapFailure): FirstRunState = state.copy(
        step = if (state.restore) FirstRunStep.PassphraseRestore else FirstRunStep.PassphraseNew,
        failure = failure,
    )
}
