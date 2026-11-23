package com.example.pguard.presentation.accountability

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.engine.accountability.TimeDelayNotificationScheduler
import com.example.pguard.presentation.dashboard.AccountabilityMode
import com.example.pguard.presentation.dashboard.BlockingUiState
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.dashboard.FriendApprovalDialog
import com.example.pguard.presentation.dashboard.TimeDelayConfirmDialog
import com.example.pguard.presentation.dashboard.TypingChallengeDialog
import com.example.pguard.presentation.dashboard.targetDisplayName

/**
 * Shared state and dialog host for enforcing the user's Accountability Guard across
 * Blocklist, Focus Mode, Settings, and secondary screens without duplicating logic.
 */
@Stable
class AccountabilityGuardController internal constructor(
    private val context: Context,
    private val blockingViewModel: BlockingViewModel?,
    private val uiStateProvider: () -> BlockingUiState?
) {
    var showTypingChallenge by mutableStateOf(false)
        internal set
    var showTimeDelayConfirm by mutableStateOf(false)
        internal set
    var showFriendApproval by mutableStateOf(false)
        internal set
    var targetTitle by mutableStateOf("Protection Feature")
        internal set
    var pendingTimeDelayTargetId by mutableStateOf("")
        internal set
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
        internal set
    var localUnlockError by mutableStateOf<String?>(null)
        internal set

    fun guard(
        targetId: String,
        targetTitle: String,
        action: () -> Unit
    ) {
        val state = uiStateProvider()
        val mode = state?.accountabilityMode
            ?: AccountabilityMode.fromCode(PGuardAccessibilityService.accountabilityMode)

        this.targetTitle = targetTitle

        when (mode) {
            AccountabilityMode.NONE -> {
                action()
            }

            AccountabilityMode.SELF -> {
                pendingAction = action
                showTypingChallenge = true
            }

            AccountabilityMode.TIME_DELAY -> {
                val serverReady = state?.isTimeDelayServerReady ?: false
                if (BlockingPreferences.isTimeDelayElapsed(context, targetId, serverConfirmedReady = serverReady)) {
                    if (blockingViewModel != null) {
                        blockingViewModel.cancelTimeDelay(context)
                    } else {
                        BlockingPreferences.clearTimeDelay(context)
                        TimeDelayNotificationScheduler.cancel(context)
                    }
                    action()
                } else {
                    pendingTimeDelayTargetId = targetId
                    pendingAction = null
                    showTimeDelayConfirm = true
                }
            }

            AccountabilityMode.FRIEND -> {
                // Fail-closed offline using cached partner status so airplane mode / skipped login cannot bypass
                val isLinked = (state?.isPartnerLinked == true) || BlockingPreferences.isPartnerLinkedCached
                if (isLinked) {
                    localUnlockError = null
                    blockingViewModel?.clearUnlockErrors()
                    pendingAction = action
                    showFriendApproval = true
                } else {
                    action()
                }
            }
        }
    }
}

@Composable
fun rememberAccountabilityGuard(
    blockingViewModel: BlockingViewModel?
): AccountabilityGuardController {
    val context = LocalContext.current
    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value
    return remember(context, blockingViewModel) {
        AccountabilityGuardController(
            context = context,
            blockingViewModel = blockingViewModel,
            uiStateProvider = { blockingViewModel?.uiState?.value ?: blockingUiState }
        )
    }
}

@Composable
fun AccountabilityGuardDialogsHost(
    controller: AccountabilityGuardController,
    blockingViewModel: BlockingViewModel?
) {
    val context = LocalContext.current
    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value

    if (controller.showTypingChallenge) {
        TypingChallengeDialog(
            targetFeatureTitle = controller.targetTitle,
            onSuccess = {
                controller.showTypingChallenge = false
                controller.pendingAction?.invoke()
                controller.pendingAction = null
            },
            onDismiss = {
                controller.showTypingChallenge = false
                controller.pendingAction = null
            }
        )
    }

    if (controller.showTimeDelayConfirm) {
        val currentTarget = blockingUiState?.timeDelayTarget?.takeIf {
            it.isNotBlank() && it != controller.pendingTimeDelayTargetId
        }
        val activeTitle = currentTarget?.let { targetDisplayName(it) }

        TimeDelayConfirmDialog(
            targetFeatureTitle = controller.targetTitle,
            activeTimerTitle = activeTitle,
            onConfirm = {
                controller.showTimeDelayConfirm = false
                if (controller.pendingTimeDelayTargetId.isNotBlank()) {
                    if (blockingViewModel != null) {
                        blockingViewModel.startTimeDelayForTarget(
                            targetId = controller.pendingTimeDelayTargetId,
                            targetTitle = controller.targetTitle,
                            context = context
                        )
                    } else {
                        val duration = BlockingPreferences.DEFAULT_TIME_DELAY_MS
                        val unlockAt = System.currentTimeMillis() + duration
                        BlockingPreferences.setTimeDelay(
                            context = context,
                            unlockAtWallMs = unlockAt,
                            target = controller.pendingTimeDelayTargetId,
                            durationMs = duration,
                            serverBacked = false
                        )
                        TimeDelayNotificationScheduler.schedule(context, unlockAt, controller.targetTitle)
                    }
                }
                controller.pendingTimeDelayTargetId = ""
                controller.pendingAction = null
            },
            onDismiss = {
                controller.showTimeDelayConfirm = false
                controller.pendingTimeDelayTargetId = ""
                controller.pendingAction = null
            }
        )
    }

    if (controller.showFriendApproval) {
        val partnerEmail = blockingUiState?.partner?.partnerEmail?.takeIf { it.isNotBlank() }
            ?: BlockingPreferences.cachedPartnerEmail

        FriendApprovalDialog(
            targetFeatureTitle = controller.targetTitle,
            partnerEmail = partnerEmail,
            onRequestCode = {
                controller.localUnlockError = null
                blockingViewModel?.requestPartnerApprovalCode(controller.targetTitle)
            },
            onSubmitCode = { code ->
                if (blockingViewModel != null) {
                    blockingViewModel.verifyExternalUnlockCode(
                        enteredCode = code,
                        featureName = controller.targetTitle,
                        onSuccess = {
                            controller.showFriendApproval = false
                            controller.localUnlockError = null
                            controller.pendingAction?.invoke()
                            controller.pendingAction = null
                        },
                        onError = { err ->
                            controller.localUnlockError = err
                        }
                    )
                } else {
                    controller.localUnlockError = "Accountability verification service is unavailable."
                }
            },
            onDismiss = {
                controller.showFriendApproval = false
                controller.localUnlockError = null
                blockingViewModel?.clearUnlockErrors()
                controller.pendingAction = null
            },
            isVerifying = blockingUiState?.isVerifyingUnlockCode ?: false,
            isSendingRequest = blockingUiState?.isSendingUnlockCode ?: false,
            emailSentSuccess = blockingUiState?.unlockEmailSent ?: false,
            errorMessage = controller.localUnlockError ?: blockingUiState?.unlockCodeError,
            requestError = blockingUiState?.unlockRequestError
        )
    }
}
