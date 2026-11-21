package api

import (
	"encoding/json"
	"log"
	"net/http"
	"net/url"
	"strings"

	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/utils"
)

type SettingsHandler struct {
	settingsStore store.SettingsStore
	logger        *log.Logger
}

func NewSettingsHandler(settingsStore store.SettingsStore, logger *log.Logger) *SettingsHandler {
	return &SettingsHandler{
		settingsStore: settingsStore,
		logger:        logger,
	}
}

type updateSettingsRequest struct {
	BlockAdultContent        *bool   `json:"block_adult_content"`
	BlockShorts              *bool   `json:"block_shorts"`
	BlockWhatsAppChannels    *bool   `json:"block_whatsapp_channels"`
	BlockInstagramReels      *bool   `json:"block_instagram_reels"`
	BlockTikTok              *bool   `json:"block_tiktok"`
	UninstallProtection      *bool   `json:"uninstall_protection"`
	BlockUnsupportedBrowsers *bool   `json:"block_unsupported_browsers"`
	BlockNewlyInstalledApps  *bool   `json:"block_newly_installed_apps"`
	BlockedScreenMessage     *string `json:"blocked_screen_message"`
	BlockedScreenCountdown   *int    `json:"blocked_screen_countdown"`
	RedirectURL              *string `json:"redirect_url"`
}

func (h *SettingsHandler) HandleGetSettings(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	settings, err := h.settingsStore.GetByUserID(user.ID)
	if err != nil {
		h.logger.Printf("ERROR: get user settings: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to fetch settings"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{"settings": settings})
}

func (h *SettingsHandler) HandleUpdateSettings(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req updateSettingsRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		h.logger.Printf("ERROR: decode update settings body: %v", err)
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	current, err := h.settingsStore.GetByUserID(user.ID)
	if err != nil {
		h.logger.Printf("ERROR: fetch current settings for update: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Internal server error"})
		return
	}

	if req.BlockAdultContent != nil {
		current.BlockAdultContent = *req.BlockAdultContent
	}
	if req.BlockShorts != nil {
		current.BlockShorts = *req.BlockShorts
	}
	if req.BlockWhatsAppChannels != nil {
		current.BlockWhatsAppChannels = *req.BlockWhatsAppChannels
	}
	if req.BlockInstagramReels != nil {
		current.BlockInstagramReels = *req.BlockInstagramReels
	}
	if req.BlockTikTok != nil {
		current.BlockTikTok = *req.BlockTikTok
	}
	if req.UninstallProtection != nil {
		current.UninstallProtection = *req.UninstallProtection
	}
	if req.BlockUnsupportedBrowsers != nil {
		current.BlockUnsupportedBrowsers = *req.BlockUnsupportedBrowsers
	}
	if req.BlockNewlyInstalledApps != nil {
		current.BlockNewlyInstalledApps = *req.BlockNewlyInstalledApps
	}
	if req.BlockedScreenMessage != nil {
		msg := strings.TrimSpace(*req.BlockedScreenMessage)
		if len(msg) > 300 {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Blocked screen message must be 300 characters or fewer"})
			return
		}
		current.BlockedScreenMessage = msg
	}
	if req.BlockedScreenCountdown != nil {
		if *req.BlockedScreenCountdown < 0 || *req.BlockedScreenCountdown > 300 {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Countdown must be between 0 and 300 seconds"})
			return
		}
		current.BlockedScreenCountdown = *req.BlockedScreenCountdown
	}
	if req.RedirectURL != nil {
		rawURL := strings.TrimSpace(*req.RedirectURL)
		if rawURL != "" {
			if strings.ContainsAny(rawURL, " \t\r\n") {
				_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Redirect URL contains invalid whitespace"})
				return
			}
			toValidate := rawURL
			if !strings.Contains(toValidate, "://") {
				if strings.Contains(toValidate, ":") {
					_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Redirect URL must use http or https"})
					return
				}
				toValidate = "https://" + toValidate
			}
			parsed, err := url.Parse(toValidate)
			if err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.Host == "" {
				_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Redirect URL must be a valid http or https URL"})
				return
			}
		}
		current.RedirectURL = rawURL
	}

	updated, err := h.settingsStore.Upsert(current)
	if err != nil {
		h.logger.Printf("ERROR: save settings: %v", err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to update settings"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"message":  "Settings updated successfully",
		"settings": updated,
	})
}
