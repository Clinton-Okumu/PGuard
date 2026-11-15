package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"

	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/utils"
)

type AccountabilityHandler struct {
	accountabilityStore store.AccountabilityStore
	logger              *log.Logger
}

func NewAccountabilityHandler(accountabilityStore store.AccountabilityStore, logger *log.Logger) *AccountabilityHandler {
	return &AccountabilityHandler{
		accountabilityStore: accountabilityStore,
		logger:              logger,
	}
}

type invitePartnerRequest struct {
	PartnerEmail string `json:"partner_email"`
	PartnerName  string `json:"partner_name"`
}

type acceptInviteRequest struct {
	InviteCode string `json:"invite_code"`
}

type recordEventRequest struct {
	EventType string `json:"event_type"`
	Message   string `json:"message"`
}

func (h *AccountabilityHandler) HandleGetPartner(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	partner, err := h.accountabilityStore.GetPartnerByUserID(user.ID)
	if err != nil {
		if errors.Is(err, store.ErrPartnerNotFound) {
			_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
				"partner":   nil,
				"is_linked": false,
			})
			return
		}
		h.logger.Printf("ERROR: get accountability partner for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to load partner"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"partner":   partner,
		"is_linked": partner.Status == store.PartnerStatusAccepted,
	})
}

func (h *AccountabilityHandler) HandleCreateInvite(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req invitePartnerRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	if strings.TrimSpace(req.PartnerEmail) == "" {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "partner_email is required"})
		return
	}

	partner, err := h.accountabilityStore.CreateInvite(user.ID, user.Email, req.PartnerEmail, req.PartnerName)
	if err != nil {
		if errors.Is(err, store.ErrInvalidEmail) {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid email address"})
			return
		}
		if errors.Is(err, store.ErrSelfInvite) {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "You cannot invite yourself"})
			return
		}
		h.logger.Printf("ERROR: create partner invite for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to generate invite"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusCreated, utils.Envelope{
		"message": "Accountability partner invite created",
		"partner": partner,
	})
}

func (h *AccountabilityHandler) HandleAcceptInvite(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req acceptInviteRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	code := strings.TrimSpace(req.InviteCode)
	if code == "" {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "invite_code is required"})
		return
	}

	partner, err := h.accountabilityStore.AcceptInvite(code, &user.ID)
	if err != nil {
		if errors.Is(err, store.ErrInvalidInviteCode) {
			_ = utils.WriteJSON(w, http.StatusNotFound, utils.Envelope{"error": "Invalid or expired invite code"})
			return
		}
		h.logger.Printf("ERROR: accept partner invite %s: %v", code, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to accept invite"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"message": "Accountability partnership activated",
		"partner": partner,
	})
}

func (h *AccountabilityHandler) HandleDisconnectPartner(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	err := h.accountabilityStore.DisconnectPartner(user.ID)
	if err != nil {
		if errors.Is(err, store.ErrPartnerNotFound) {
			_ = utils.WriteJSON(w, http.StatusNotFound, utils.Envelope{"error": "No active partner found"})
			return
		}
		h.logger.Printf("ERROR: disconnect partner for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to disconnect partner"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"status":  "disconnected",
		"message": "Accountability partner disconnected successfully",
	})
}

func (h *AccountabilityHandler) HandleRecordEvent(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req recordEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	if !store.IsValidEventType(req.EventType) {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{
			"error": "Invalid event_type. Must be 'sos_triggered', 'protection_disabled', 'permission_revoked', or 'uninstall_attempt'",
		})
		return
	}

	alert, err := h.accountabilityStore.RecordSecurityEvent(user.ID, req.EventType, req.Message)
	if err != nil {
		h.logger.Printf("ERROR: record security alert for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to record alert"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusCreated, utils.Envelope{
		"message": "Security event recorded",
		"alert":   alert,
	})
}

func (h *AccountabilityHandler) HandleGetEvents(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	limit := 20
	if rawLimit := r.URL.Query().Get("limit"); rawLimit != "" {
		if parsed, err := strconv.Atoi(rawLimit); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	events, err := h.accountabilityStore.GetRecentEvents(user.ID, limit)
	if err != nil {
		h.logger.Printf("ERROR: get security events for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to load events"})
		return
	}

	if events == nil {
		events = []store.SecurityAlert{}
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"events": events,
		"count":  len(events),
	})
}
