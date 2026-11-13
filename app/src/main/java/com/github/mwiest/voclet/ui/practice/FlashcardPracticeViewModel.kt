/*
 * Modified in the non-official Chinese fork "沃莱单词·中文分支" (voclet-zh-branch).
 * Changes: TTS speaks the English front face on first load, flip-back, next card and reset;
 * deleteCurrentWord() permanently removes the current pair from the word list.
 * Original: Copyright 2024 Marc Wiest, licensed under Apache License 2.0.
 */
package com.github.mwiest.voclet.ui.practice

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mwiest.voclet.data.VocletRepository
import com.github.mwiest.voclet.data.database.PracticeType
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.data.tts.TtsManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FlashcardPracticeUiState(
    val currentCardIndex: Int = 0,
    val wordPairs: List<WordPair> = emptyList(),
    val isFlipped: Boolean = false,
    val isLoading: Boolean = true,
    val practiceComplete: Boolean = false,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    val languageMap: Map<Long, String> = emptyMap() // wordListId -> language2 code
)

@HiltViewModel
class FlashcardPracticeViewModel @Inject constructor(
    private val repository: VocletRepository,
    private val ttsManager: TtsManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val ttsDelegate = TtsDelegate(ttsManager, viewModelScope)

    private val _uiState = MutableStateFlow(FlashcardPracticeUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Extract route params
            val selectedListIds = savedStateHandle.get<String>("selectedListIds")
                ?.split(",")
                ?.mapNotNull { it.toLongOrNull() }
                ?: emptyList()

            val focusFilter = savedStateHandle.get<String>("focusFilter") ?: "all"

            val settings = repository.getSettings().filterNotNull().first()

            // Load word lists to get language codes, applying any language variant overrides
            val wordLists = repository.getWordListsByIds(selectedListIds)
            val overrides = settings.ttsLanguageOverrides
            val languageMap = wordLists.associate {
                val base = it.language2 ?: "en"
                it.id to (overrides[base] ?: base)
            }

            ttsDelegate.initialize(languageMap.values.toSet())
            if (!settings.ttsEnabledByDefault) ttsDelegate.toggle()

            // Load word pairs based on selected lists and filter
            val wordPairs = when (focusFilter) {
                "starred" -> repository.getWordPairsForListsStarredOnly(selectedListIds)
                "hard" -> repository.getWordPairsForListsHardOnly(selectedListIds)
                else -> repository.getWordPairsForLists(selectedListIds)
            }

            // Shuffle word pairs
            val shuffledPairs = wordPairs.shuffled()

            _uiState.update { state ->
                state.copy(
                    wordPairs = shuffledPairs,
                    languageMap = languageMap,
                    isLoading = false
                )
            }

            // Read the first card's target-language word (word2) aloud when it appears.
            speakFrontWord(_uiState.value)
        }
    }

    fun flipCard() {
        val currentState = _uiState.value
        val willBeFlipped = !currentState.isFlipped

        _uiState.update { state ->
            state.copy(isFlipped = willBeFlipped)
        }

        // The front face shows the target-language word (word2, e.g. English) and is read
        // aloud. When flipping back to the front, speak that word again.
        if (!willBeFlipped) {
            speakFrontWord(currentState)
        }
    }

    /**
     * Read the current card's target-language word (word2) aloud. The front face of the
     * flashcard now displays this word, so speaking it teaches recognition of the word.
     */
    private fun speakFrontWord(state: FlashcardPracticeUiState) {
        val currentPair = state.wordPairs.getOrNull(state.currentCardIndex) ?: return
        val languageCode = state.languageMap[currentPair.wordListId] ?: "en"
        ttsDelegate.speak(currentPair.word2, languageCode)
    }

    fun markCorrect() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.currentCardIndex < currentState.wordPairs.size) {
                val wordPair = currentState.wordPairs[currentState.currentCardIndex]
                repository.recordPracticeResult(wordPair.id, true, PracticeType.FLASHCARD)
            }

            _uiState.update { state ->
                state.copy(
                    correctCount = state.correctCount + 1,
                    isFlipped = false
                )
            }

            moveToNext()
        }
    }

    fun deleteCurrentWord() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val currentPair = currentState.wordPairs.getOrNull(currentState.currentCardIndex)

            if (currentPair != null) {
                repository.deleteWordPair(currentPair)
            }

            _uiState.update { state ->
                val remaining = state.wordPairs.filterIndexed { index, _ ->
                    index != state.currentCardIndex
                }
                state.copy(
                    wordPairs = remaining,
                    isFlipped = false,
                    // The next word slides into the current slot; with none left, finish.
                    practiceComplete = remaining.isEmpty(),
                    currentCardIndex = if (state.currentCardIndex >= remaining.size) {
                        (remaining.size - 1).coerceAtLeast(0)
                    } else {
                        state.currentCardIndex
                    }
                )
            }

            // Read the next card's target-language word aloud.
            val state = _uiState.value
            if (!state.practiceComplete) speakFrontWord(state)
        }
    }

    fun markIncorrect() {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState.currentCardIndex < currentState.wordPairs.size) {
                val wordPair = currentState.wordPairs[currentState.currentCardIndex]
                repository.recordPracticeResult(wordPair.id, false, PracticeType.FLASHCARD)
            }

            _uiState.update { state ->
                state.copy(
                    incorrectCount = state.incorrectCount + 1,
                    isFlipped = false
                )
            }

            moveToNext()
        }
    }

    private suspend fun moveToNext() {
        // Wait 300ms for result feedback perception
        delay(300)

        _uiState.update { state ->
            val nextIndex = state.currentCardIndex + 1
            if (nextIndex >= state.wordPairs.size) {
                state.copy(practiceComplete = true)
            } else {
                state.copy(currentCardIndex = nextIndex)
            }
        }

        // Read the next card's target-language word aloud if practice continues.
        val state = _uiState.value
        if (!state.practiceComplete) speakFrontWord(state)
    }

    fun resetPractice() {
        _uiState.update { state ->
            state.copy(
                currentCardIndex = 0,
                isFlipped = false,
                practiceComplete = false,
                correctCount = 0,
                incorrectCount = 0,
                wordPairs = state.wordPairs.shuffled()
            )
        }
        // Read the first card's target-language word aloud again.
        speakFrontWord(_uiState.value)
    }
}
