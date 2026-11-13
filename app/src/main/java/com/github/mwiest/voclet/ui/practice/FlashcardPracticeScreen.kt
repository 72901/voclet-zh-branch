/*
 * Modified in the non-official Chinese fork "沃莱单词·中文分支" (voclet-zh-branch).
 * Changes: the flashcard front shows the English word (read aloud via TTS) and the back
 * shows the Chinese meaning; a delete button sits to the left of Correct; deleting plays
 * a throw-into-bin exit animation. See the git history for the full diff.
 * Original: Copyright 2024 Marc Wiest, licensed under Apache License 2.0.
 */
package com.github.mwiest.voclet.ui.practice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import com.github.mwiest.voclet.R
import com.github.mwiest.voclet.data.database.WordPair
import com.github.mwiest.voclet.ui.components.TtsErrorDialog
import com.github.mwiest.voclet.ui.components.TtsToggleButton
import com.github.mwiest.voclet.ui.theme.LocalExtendedColors
import com.github.mwiest.voclet.ui.theme.VocletTheme
import com.github.mwiest.voclet.ui.utils.prefersTwoPanes

/** Share of its pane the flashcard fills, the remainder being margin on all four sides. */
private const val CardPaneFraction = 0.85f

@Composable
fun FlashcardPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
    viewModel: FlashcardPracticeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isTtsEnabled by viewModel.ttsDelegate.isTtsEnabled.collectAsState()
    val ttsError by viewModel.ttsDelegate.errorToShow.collectAsState()
    val context = LocalContext.current

    FlashcardPracticeScreen(
        navController = navController,
        windowSizeClass = windowSizeClass,
        uiState = uiState,
        isTtsEnabled = isTtsEnabled,
        onFlip = { viewModel.flipCard() },
        onCorrect = { viewModel.markCorrect() },
        onIncorrect = { viewModel.markIncorrect() },
        onDelete = { viewModel.deleteCurrentWord() },
        onResetPractice = { viewModel.resetPractice() },
        onToggleTts = { viewModel.ttsDelegate.toggle() }
    )

    val error = ttsError
    if (error != null) {
        TtsErrorDialog(
            error = error,
            onDismiss = { viewModel.ttsDelegate.dismissError() },
            onFix = { intent ->
                viewModel.ttsDelegate.onFixStarted()
                context.startActivity(intent)
            }
        )
    }
}

@Composable
fun FlashcardPracticeScreen(
    navController: NavController,
    windowSizeClass: WindowSizeClass,
    uiState: FlashcardPracticeUiState,
    isTtsEnabled: Boolean = true,
    onFlip: () -> Unit,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onDelete: () -> Unit = {},
    onResetPractice: () -> Unit = {},
    onToggleTts: () -> Unit = {}
) {
    if (uiState.practiceComplete) {
        PracticeResultsScreen(
            navController = navController,
            windowSizeClass = windowSizeClass,
            correctCount = uiState.correctCount,
            incorrectCount = uiState.incorrectCount,
            onPracticeAgain = {
                onResetPractice()
            },
            onBackToHome = { navController.navigate("home") { popUpTo("home") } }
        )
    } else {
        FlashcardPracticeContent(
            navController = navController,
            twoPane = windowSizeClass.prefersTwoPanes(),
            uiState = uiState,
            isTtsEnabled = isTtsEnabled,
            onFlip = onFlip,
            onCorrect = onCorrect,
            onIncorrect = onIncorrect,
            onDelete = onDelete,
            onToggleTts = onToggleTts
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlashcardPracticeContent(
    navController: NavController,
    twoPane: Boolean,
    uiState: FlashcardPracticeUiState,
    isTtsEnabled: Boolean,
    onFlip: () -> Unit,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onDelete: () -> Unit,
    onToggleTts: () -> Unit
) {
    val currentPairId = uiState.wordPairs.getOrNull(uiState.currentCardIndex)?.id ?: -1L

    // "Throw the card into the bin" animation. Keyed to the card id, so when the ViewModel
    // swaps in the next word the transform is fresh and the new card never inherits the
    // shrunken / faded state of the discarded one.
    var isDeleting by remember { mutableStateOf(false) }
    val exitProgress = remember(currentPairId) { Animatable(0f) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val density = LocalDensity.current
    val dropDistancePx = remember(density) { with(density) { 160.dp.toPx() } }

    LaunchedEffect(currentPairId) { isDeleting = false }

    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            exitProgress.snapTo(0f)
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 420,
                    easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
                )
            )
            // Remove the word only after it has visibly landed in the bin.
            currentOnDelete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.currentCardIndex + 1} / ${uiState.wordPairs.size} ${
                            stringResource(
                                id = R.string.flashcard_flip
                            )
                        }"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    TtsToggleButton(isEnabled = isTtsEnabled, onToggle = onToggleTts)
                }
            )
        }
    ) { paddingValues ->
        if (uiState.wordPairs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(id = R.string.no_words_to_practice))
            }
        } else {
            val currentPair = uiState.wordPairs[uiState.currentCardIndex]

            // Scaffold measures paddingValues from the system bars but does not consume them, so
            // without the consume call safeContentPadding would apply the very same bars again.
            val body = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .safeContentPadding()

            if (twoPane) {
                // Stacking a card above its buttons needs more height than a landscape window
                // has, and this screen does not scroll, so the buttons were placed past the
                // bottom edge and clipped. Side by side the card gets the whole height.
                Row(
                    modifier = body,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Taking a share of the pane rather than filling it leaves margin on every
                    // side that grows and shrinks with the window, instead of a fixed gap that
                    // looks mean on a tablet and cramped on a phone.
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Front shows the target-language word (word2, e.g. English) so it can be
                        // heard via TTS; flipping reveals the known-language meaning (word1).
                        FlashcardStage(
                            cardId = currentPair.id,
                            isFlipped = uiState.isFlipped,
                            frontText = currentPair.word2,
                            backText = currentPair.word1,
                            cardModifier = Modifier.fillMaxSize(CardPaneFraction),
                            isDeleting = isDeleting,
                            progress = { exitProgress.value },
                            dropDistancePx = dropDistancePx
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        ButtonArea(
                            isFlipped = uiState.isFlipped,
                            onFlip = { if (!isDeleting) onFlip() },
                            onCorrect = { if (!isDeleting) onCorrect() },
                            onIncorrect = { if (!isDeleting) onIncorrect() },
                            onDelete = { if (!isDeleting) isDeleting = true }
                        )
                    }
                }
            } else {
                Column(
                    modifier = body,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(modifier = Modifier.height(1.dp))

                    // Front shows the target-language word (word2, e.g. English) so it can be
                    // heard via TTS; flipping reveals the known-language meaning (word1).
                    FlashcardStage(
                        cardId = currentPair.id,
                        isFlipped = uiState.isFlipped,
                        frontText = currentPair.word2,
                        backText = currentPair.word1,
                        cardModifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(300.dp),
                        isDeleting = isDeleting,
                        progress = { exitProgress.value },
                        dropDistancePx = dropDistancePx
                    )

                    ButtonArea(
                        isFlipped = uiState.isFlipped,
                        onFlip = { if (!isDeleting) onFlip() },
                        onCorrect = { if (!isDeleting) onCorrect() },
                        onIncorrect = { if (!isDeleting) onIncorrect() },
                        onDelete = { if (!isDeleting) isDeleting = true },
                        modifier = Modifier.padding(vertical = 32.dp)
                    )
                }
            }
        }
    }
}

/**
 * Wraps the flashcard with a small "throw into the bin" exit animation used when a word is
 * deleted: the card shrinks, tilts and drops toward a trash icon, then fades out, while the
 * icon gives a little bounce as it catches the word.
 */
@Composable
private fun FlashcardStage(
    cardId: Long,
    isFlipped: Boolean,
    frontText: String,
    backText: String,
    cardModifier: Modifier,
    isDeleting: Boolean,
    progress: () -> Float,
    dropDistancePx: Float
) {
    Box(contentAlignment = Alignment.Center) {
        AnimatedFlashcard(
            cardId = cardId,
            isFlipped = isFlipped,
            word1 = frontText,
            word2 = backText,
            modifier = cardModifier.graphicsLayer {
                val p = progress()
                if (p > 0f) {
                    val scale = 1f - 0.85f * p
                    scaleX = scale
                    scaleY = scale
                    rotationZ = 20f * p
                    translationY = dropDistancePx * p
                    alpha = (1f - p * p * 1.1f).coerceIn(0f, 1f)
                }
            }
        )

        if (isDeleting) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(56.dp)
                    .graphicsLayer {
                        val p = progress()
                        val fadeIn = (p / 0.15f).coerceIn(0f, 1f)
                        val fadeOut = ((1f - p) / 0.15f).coerceIn(0f, 1f)
                        alpha = fadeIn * fadeOut
                        val bounce = Math.sin(p.toDouble() * Math.PI).toFloat()
                        val s = 1f + 0.3f * bounce
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}

@Composable
private fun AnimatedFlashcard(
    cardId: Long,
    isFlipped: Boolean,
    word1: String,
    word2: String,
    modifier: Modifier = Modifier
) {
    // Key the animation to the card id so every newly shown word starts face-up. Otherwise
    // deleting a card while it is flipped keeps the 180-degree rotation and animates the
    // *next* word back to 0 degrees, briefly revealing its translation (word2 / Chinese).
    val flipRotation = remember(cardId) { Animatable(0f) }

    LaunchedEffect(isFlipped) {
        flipRotation.animateTo(
            targetValue = if (isFlipped) 180f else 0f,
            animationSpec = tween(
                durationMillis = 500,
                easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
            )
        )
    }

    // Card scale animation (subtle elevation effect). Reset for each new card too, so a flip
    // interrupted by a delete does not leave the card stuck at a slightly enlarged scale.
    val scaleValue = remember(cardId) { Animatable(1f) }

    LaunchedEffect(isFlipped) {
        if (isFlipped) {
            scaleValue.animateTo(1.05f, animationSpec = tween(250))
            scaleValue.animateTo(1f, animationSpec = tween(250))
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer(
                rotationY = flipRotation.value,
                scaleX = scaleValue.value,
                scaleY = scaleValue.value,
                cameraDistance = 12f * 10
            ),
        contentAlignment = Alignment.Center
    ) {
        // At 90 degrees the card is edge-on, so both the text and the tint switch there:
        // the word1 face is tertiary-shaded, the word2 face primary-shaded.
        val isShowingBack = flipRotation.value > 90

        Card(
            modifier = Modifier.fillMaxSize(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isShowingBack) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                contentColor = if (isShowingBack) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                }
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val displayText = if (isShowingBack) word2 else word1

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(24.dp)
                        .graphicsLayer(
                            scaleX = if (isShowingBack) -1f else 1f
                        )
                )
            }
        }
    }
}

@Composable
private fun ButtonArea(
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onCorrect: () -> Unit,
    onIncorrect: () -> Unit,
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isFlipped) {
            // Flip button
            Button(
                onClick = onFlip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    Icons.Default.Flip,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(id = R.string.flip),
                )
            }
        } else {
            // Correct and Incorrect buttons, with a delete button to the left of Correct
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Button(
                    onClick = onCorrect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalExtendedColors.current.success.color,
                        contentColor = LocalExtendedColors.current.success.onColor
                    )
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(id = R.string.correct),
                    )
                }
                Button(
                    onClick = onIncorrect,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        // buttonColors() only overrides what it is given, so without this the
                        // label keeps the default onPrimary - near-black under this palette.
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(id = R.string.incorrect),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun FlashcardPracticeScreenPreview() {
    VocletTheme {
        FlashcardPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = FlashcardPracticeUiState(
                currentCardIndex = 0,
                wordPairs = listOf(
                    WordPair(
                        id = 1,
                        wordListId = 1,
                        word1 = "I'm best when I'm awake",
                        word2 = "Hola"
                    ),
                    WordPair(id = 2, wordListId = 1, word1 = "Goodbye", word2 = "Adiós"),
                    WordPair(id = 3, wordListId = 1, word1 = "Thank you", word2 = "Gracias")
                ),
                isFlipped = false,
                isLoading = false,
                practiceComplete = false,
                correctCount = 0,
                incorrectCount = 0
            ),
            onFlip = {},
            onCorrect = {},
            onIncorrect = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 450, heightDp = 800)
@Composable
fun FlashcardPracticeScreenFlippedPreview() {
    VocletTheme {
        FlashcardPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = FlashcardPracticeUiState(
                currentCardIndex = 0,
                wordPairs = listOf(
                    WordPair(
                        id = 1,
                        wordListId = 1,
                        word1 = "I'm best when I'm awake",
                        word2 = "Hola"
                    ),
                    WordPair(id = 2, wordListId = 1, word1 = "Goodbye", word2 = "Adiós"),
                    WordPair(id = 3, wordListId = 1, word1 = "Thank you", word2 = "Gracias")
                ),
                isFlipped = true,
                isLoading = false,
                practiceComplete = false,
                correctCount = 0,
                incorrectCount = 0
            ),
            onFlip = {},
            onCorrect = {},
            onIncorrect = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 600)
@Composable
fun FlashcardPracticeScreenDarkPreview() {
    VocletTheme(darkTheme = true) {
        FlashcardPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = FlashcardPracticeUiState(
                currentCardIndex = 1,
                wordPairs = listOf(
                    WordPair(id = 1, wordListId = 1, word1 = "Hello", word2 = "Hola"),
                    WordPair(id = 2, wordListId = 1, word1 = "Goodbye", word2 = "Adiós"),
                    WordPair(id = 3, wordListId = 1, word1 = "Thank you", word2 = "Gracias")
                ),
                isFlipped = false,
                isLoading = false,
                practiceComplete = false,
                correctCount = 1,
                incorrectCount = 0
            ),
            onFlip = {},
            onCorrect = {},
            onIncorrect = {}
        )
    }
}

/** Phone landscape, flipped: two panes, with both answer buttons sharing the controls pane. */
@Preview(showBackground = true, widthDp = 800, heightDp = 400)
@Composable
fun FlashcardPracticeScreenLandscapePreview() {
    VocletTheme {
        FlashcardPracticeScreen(
            navController = rememberNavController(),
            windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass,
            uiState = FlashcardPracticeUiState(
                currentCardIndex = 1,
                wordPairs = listOf(
                    WordPair(id = 1, wordListId = 1, word1 = "Hello", word2 = "Hola"),
                    WordPair(id = 2, wordListId = 1, word1 = "Goodbye", word2 = "Adiós"),
                    WordPair(id = 3, wordListId = 1, word1 = "Thank you", word2 = "Gracias")
                ),
                isFlipped = true,
                isLoading = false,
                practiceComplete = false,
                correctCount = 1,
                incorrectCount = 0
            ),
            onFlip = {},
            onCorrect = {},
            onIncorrect = {}
        )
    }
}
