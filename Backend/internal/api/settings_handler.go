package api

import (
	"encoding/json"
	"log"
	"net/http"

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
	BlockMediaSearch         *bool   `json:"block_media_search"`
	BlockShorts              *bool   `json:"block_shorts"`
	BlockWhatsAppChannels    *bool   `json:"block_whatsapp_channels"`
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
	if req.BlockMediaSearch != nil {
		current.BlockMediaSearch = *req.BlockMediaSearch
	}
	if req.BlockShorts != nil {
		current.BlockShorts = *req.BlockShorts
	}
	if req.BlockWhatsAppChannels != nil {
		current.BlockWhatsAppChannels = *req.BlockWhatsAppChannels
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
		current.BlockedScreenMessage = *req.BlockedScreenMessage
	}
	if req.BlockedScreenCountdown != nil {
		if *req.BlockedScreenCountdown < 0 {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Countdown must be non-negative"})
			return
		}
		current.BlockedScreenCountdown = *req.BlockedScreenCountdown
	}
	if req.RedirectURL != nil {
		current.RedirectURL = *req.RedirectURL
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
