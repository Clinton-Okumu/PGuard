package com.example.pguard.presentation.dashboard

enum class AccountabilityMode(
    val code: String,
    val title: String,
    val subtitle: String
) {
    NONE(
        code = "none",
        title = "No Restriction",
        subtitle = "Toggles can be turned off anytime without friction."
    ),
    FRIEND(
        code = "friend",
        title = "Accountability Partner",
        subtitle = "A trusted friend receives an email and must approve disabling protection."
    ),
    SELF(
        code = "self",
        title = "Myself (Typing Challenge)",
        subtitle = "Type a focus commitment within a 60-second timer to turn off features."
    ),
    TIME_DELAY(
        code = "time_delay",
        title = "24-Hour Time Delay",
        subtitle = "A 24-hour waiting period is enforced before any protection can be disabled."
    );

    companion object {
        fun fromCode(code: String?): AccountabilityMode {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NONE
        }
    }
}

sealed interface ProtectedAction {
    val title: String
    val identifier: String

    data class ToggleFeature(val toggle: BlockingToggle) : ProtectedAction {
        override val title: String get() = featureDisplayName(toggle.name)
        override val identifier: String get() = toggle.name
    }

    data object OpenChangeMode : ProtectedAction {
        override val title: String get() = "Change Accountability Mode"
        override val identifier: String get() = "OPEN_CHANGE_MODE"
    }

    data class ChangeMode(val targetMode: AccountabilityMode) : ProtectedAction {
        override val title: String get() = "Change Accountability Mode"
        override val identifier: String get() = "MODE_${targetMode.code}"
    }

    data object DisconnectPartner : ProtectedAction {
        override val title: String get() = "Remove Accountability Partner"
        override val identifier: String get() = "DISCONNECT_PARTNER"
    }
}

fun targetDisplayName(target: String?): String {
    if (target == null) return "Protection Feature"
    if (target == "DISCONNECT_PARTNER") return "Remove Accountability Partner"
    if (target == "OPEN_CHANGE_MODE") return "Change Accountability Mode"
    if (target.startsWith("BLOCKLIST_APP:")) return "Blocklist App"
    if (target.startsWith("BLOCKLIST_KW:")) return "Blocklist Keyword (${target.removePrefix("BLOCKLIST_KW:")})"
    if (target.startsWith("FOCUS_SCHEDULE:")) return "Focus Schedule"
    if (target.startsWith("MODE_")) {
        val modeCode = target.removePrefix("MODE_")
        val mode = AccountabilityMode.fromCode(modeCode)
        return "Change Mode to ${mode.title}"
    }
    return featureDisplayName(target)
}

fun featureDisplayName(toggleName: String?): String = when (toggleName) {
    BlockingToggle.ADULT_CONTENT.name -> "Adult Content"
    BlockingToggle.SHORTS.name -> "YouTube Shorts"
    BlockingToggle.WHATSAPP_CHANNELS.name -> "WhatsApp Channels"
    BlockingToggle.INSTAGRAM_REELS.name -> "Instagram Reels"
    BlockingToggle.TIKTOK.name -> "TikTok"
    BlockingToggle.UNINSTALL_PROTECTION.name -> "Uninstall Protection"
    BlockingToggle.UNSUPPORTED_BROWSERS.name -> "Unsupported Browsers"
    BlockingToggle.NEW_INSTALLED_APPS.name -> "Newly Installed Apps"
    else -> toggleName ?: "Protection Feature"
}
