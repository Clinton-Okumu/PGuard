package api

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"strconv"
	"strings"

	"Backend/internal/mailer"
	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/utils"
)

type AccountabilityHandler struct {
	accountabilityStore store.AccountabilityStore
	mailer              mailer.Mailer
	logger              *log.Logger
}

func NewAccountabilityHandler(accountabilityStore store.AccountabilityStore, mailer mailer.Mailer, logger *log.Logger) *AccountabilityHandler {
	return &AccountabilityHandler{
		accountabilityStore: accountabilityStore,
		mailer:              mailer,
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

type requestUnlockCodeRequest struct {
	FeatureName string `json:"feature_name"`
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

	if store.AreEmailsEquivalent(req.PartnerEmail, user.Email) {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "You cannot invite yourself as an accountability partner"})
		return
	}

	partner, err := h.accountabilityStore.CreateInvite(user.ID, user.Email, req.PartnerEmail, req.PartnerName)
	if err != nil {
		if errors.Is(err, store.ErrInvalidEmail) {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid email address"})
			return
		}
		if errors.Is(err, store.ErrSelfInvite) {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "You cannot invite yourself as an accountability partner"})
			return
		}
		h.logger.Printf("ERROR: create partner invite for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to generate invite"})
		return
	}

	if h.mailer != nil {
		go func(pEmail, uEmail, code string) {
			if err := h.mailer.SendPartnerInvite(context.Background(), pEmail, uEmail, code); err != nil {
				h.logger.Printf("ERROR: sending partner invite email: %v", err)
			}
		}(req.PartnerEmail, user.Email, partner.InviteCode)
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
		if errors.Is(err, store.ErrSelfInvite) {
			_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "You cannot accept your own invite code"})
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

func (h *AccountabilityHandler) HandleAcceptInviteWeb(w http.ResponseWriter, r *http.Request) {
	code := strings.TrimSpace(r.URL.Query().Get("code"))
	w.Header().Set("Content-Type", "text/html; charset=utf-8")

	if code == "" {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte(`<!DOCTYPE html><html><body style="background:#121212;color:#FFF;font-family:sans-serif;padding:32px;text-align:center;"><h2>⚠️ Missing Code</h2><p>No invite code provided.</p></body></html>`))
		return
	}

	partner, err := h.accountabilityStore.AcceptInvite(code, nil)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		_, _ = w.Write([]byte(`<!DOCTYPE html><html><body style="background:#121212;color:#FFF;font-family:sans-serif;padding:32px;text-align:center;"><h2>⚠️ Invite Expired or Invalid</h2><p>This invite code is invalid or has already been accepted.</p></body></html>`))
		return
	}

	htmlResp := fmt.Sprintf(`<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Partner Confirmed - PGuard</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <style>
    body { background-color: #121212; color: #FFFFFF; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; display: flex; align-items: center; justify-content: center; min-height: 80vh; margin: 0; padding: 20px; }
    .card { background-color: #1E1E22; border-radius: 20px; padding: 36px; max-width: 480px; text-align: center; border: 1px solid #333333; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
    .icon { font-size: 54px; margin-bottom: 16px; }
    h2 { color: #4CAF50; margin: 0 0 12px 0; font-size: 24px; }
    p { color: #CCCCCC; font-size: 15px; line-height: 1.6; }
    .badge { background: #28282D; border: 1px solid #4CAF50; color: #4CAF50; border-radius: 8px; padding: 8px 16px; font-weight: bold; display: inline-block; margin: 16px 0; }
  </style>
</head>
<body>
  <div class="card">
    <div class="icon">🛡️</div>
    <h2>Accountability Partner Confirmed!</h2>
    <div class="badge">Active Partner Linked</div>
    <p>You have successfully accepted the partnership for <strong>%s</strong>.</p>
    <p>Thank you for supporting their digital wellbeing. You don't need to install anything. Whenever they request to disable a protection shield, you will receive an approval email.</p>
  </div>
</body>
</html>`, partner.PartnerEmail)

	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(htmlResp))
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

	if h.mailer != nil {
		go func(uid uint, uEmail, eType, msg string) {
			if partner, err := h.accountabilityStore.GetPartnerByUserID(uid); err == nil && partner != nil && partner.Status == store.PartnerStatusAccepted {
				_ = h.mailer.SendSecurityAlert(context.Background(), partner.PartnerEmail, uEmail, eType, msg)
			}
		}(user.ID, user.Email, req.EventType, req.Message)
	}

	_ = utils.WriteJSON(w, http.StatusCreated, utils.Envelope{
		"message": "Security event recorded",
		"alert":   alert,
	})
}

func (h *AccountabilityHandler) HandleRequestUnlockCode(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req requestUnlockCodeRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	partner, err := h.accountabilityStore.GetPartnerByUserID(user.ID)
	if err != nil || partner == nil || (partner.Status != store.PartnerStatusAccepted && partner.Status != store.PartnerStatusPending) {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{
			"error": "No active or pending accountability partner found",
		})
		return
	}

	// Generate a 6-digit random code
	num, err := rand.Int(rand.Reader, big.NewInt(900000))
	if err != nil {
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to generate approval code"})
		return
	}
	code := fmt.Sprintf("%06d", num.Int64()+100000)

	featureName := req.FeatureName
	if strings.TrimSpace(featureName) == "" {
		featureName = "Protected Content"
	}

	if h.mailer != nil {
		go func(pEmail, uEmail, fName, c string) {
			if err := h.mailer.SendUnlockRequest(context.Background(), pEmail, uEmail, fName, c); err != nil {
				h.logger.Printf("ERROR: sending unlock approval email: %v", err)
			}
		}(partner.PartnerEmail, user.Email, featureName, code)
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"message":      "Approval code sent to accountability partner",
		"partner_email": partner.PartnerEmail,
		"feature_name": featureName,
		// Return code in dev for easy debugging/testing
		"code": code,
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
