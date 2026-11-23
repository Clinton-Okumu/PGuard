package api

import (
	"context"
	"crypto/subtle"
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"testing"
	"time"

	"Backend/internal/middleware"
	"Backend/internal/store"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type captureMailer struct {
	mu          sync.Mutex
	unlockCodes []string
	inviteCodes []string
	failNextErr error
}

func newCaptureMailer() *captureMailer {
	return &captureMailer{}
}

func (m *captureMailer) SendEmail(ctx context.Context, to []string, subject, htmlBody, textBody string) (string, error) {
	return "mock_id", nil
}
func (m *captureMailer) SendPartnerInvite(ctx context.Context, partnerEmail, inviterEmail, inviteCode string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.failNextErr != nil {
		err := m.failNextErr
		m.failNextErr = nil
		return err
	}
	m.inviteCodes = append(m.inviteCodes, inviteCode)
	return nil
}
func (m *captureMailer) SendUnlockRequest(ctx context.Context, partnerEmail, userEmail, featureName, approvalCode string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.failNextErr != nil {
		err := m.failNextErr
		m.failNextErr = nil
		return err
	}
	m.unlockCodes = append(m.unlockCodes, approvalCode)
	return nil
}
func (m *captureMailer) SendSecurityAlert(ctx context.Context, partnerEmail, userEmail, eventType, details string) error {
	return nil
}

type MockAccountabilityStore struct {
	partners    map[uint]*store.AccountabilityPartner
	events      map[uint][]store.SecurityAlert
	unlockCodes map[uint][]*store.UnlockCode
	timeDelays  map[uint]*store.TimeDelayUnlock
	nextID      uint
}

func NewMockAccountabilityStore() *MockAccountabilityStore {
	return &MockAccountabilityStore{
		partners:    make(map[uint]*store.AccountabilityPartner),
		events:      make(map[uint][]store.SecurityAlert),
		unlockCodes: make(map[uint][]*store.UnlockCode),
		timeDelays:  make(map[uint]*store.TimeDelayUnlock),
		nextID:      1,
	}
}

func (m *MockAccountabilityStore) CreateInvite(userID uint, userEmail, partnerEmail, partnerName string) (*store.AccountabilityPartner, error) {
	if !store.IsValidEmail(partnerEmail) {
		return nil, store.ErrInvalidEmail
	}
	if store.AreEmailsEquivalent(partnerEmail, userEmail) {
		return nil, store.ErrSelfInvite
	}

	p := &store.AccountabilityPartner{
		ID:           m.nextID,
		UserID:       userID,
		PartnerEmail: strings.ToLower(strings.TrimSpace(partnerEmail)),
		PartnerName:  strings.TrimSpace(partnerName),
		InviteCode:   "PG-TEST01",
		Status:       store.PartnerStatusPending,
		CreatedAt:    time.Now(),
		UpdatedAt:    time.Now(),
		User:         store.User{ID: userID, Email: userEmail},
	}
	m.partners[userID] = p
	m.nextID++
	return p, nil
}

func (m *MockAccountabilityStore) GetPartnerByUserID(userID uint) (*store.AccountabilityPartner, error) {
	p, ok := m.partners[userID]
	if !ok || p.Status == store.PartnerStatusDisconnected {
		return nil, store.ErrPartnerNotFound
	}
	return p, nil
}

func (m *MockAccountabilityStore) GetInviteByCode(code string) (*store.AccountabilityPartner, error) {
	for _, p := range m.partners {
		if strings.EqualFold(p.InviteCode, code) {
			return p, nil
		}
	}
	return nil, store.ErrInvalidInviteCode
}

func (m *MockAccountabilityStore) AcceptInvite(code string, partnerUserID *uint) (*store.AccountabilityPartner, error) {
	p, err := m.GetInviteByCode(code)
	if err != nil {
		return nil, err
	}
	if p.Status != store.PartnerStatusPending {
		return nil, store.ErrInviteNotPending
	}
	if time.Since(p.UpdatedAt) > 7*24*time.Hour {
		return nil, store.ErrInviteExpired
	}
	if partnerUserID != nil && *partnerUserID == p.UserID {
		return nil, store.ErrSelfInvite
	}
	p.Status = store.PartnerStatusAccepted
	p.PartnerUserID = partnerUserID
	p.UpdatedAt = time.Now()
	return p, nil
}

func (m *MockAccountabilityStore) DisconnectPartner(userID uint) error {
	p, ok := m.partners[userID]
	if !ok || p.Status == store.PartnerStatusDisconnected {
		return store.ErrPartnerNotFound
	}
	p.Status = store.PartnerStatusDisconnected
	return nil
}

func (m *MockAccountabilityStore) CreateUnlockCode(userID uint, featureName, codeHash string, expiresAt time.Time) (*store.UnlockCode, error) {
	for _, existing := range m.unlockCodes[userID] {
		existing.Used = true
	}
	rec := &store.UnlockCode{
		ID:          uint(len(m.unlockCodes[userID]) + 1),
		UserID:      userID,
		FeatureName: strings.TrimSpace(featureName),
		CodeHash:    codeHash,
		Attempts:    0,
		MaxAttempts: 5,
		Used:        false,
		ExpiresAt:   expiresAt,
		CreatedAt:   time.Now(),
	}
	m.unlockCodes[userID] = append(m.unlockCodes[userID], rec)
	return rec, nil
}

func (m *MockAccountabilityStore) VerifyAndConsumeUnlockCode(userID uint, codeHash, expectedFeature string) (*store.UnlockCode, error) {
	list := m.unlockCodes[userID]
	var latest *store.UnlockCode
	for i := len(list) - 1; i >= 0; i-- {
		if !list[i].Used {
			latest = list[i]
			break
		}
	}
	if latest == nil {
		return nil, store.ErrInvalidUnlockCode
	}
	if latest.ExpiresAt.Before(time.Now()) {
		latest.Used = true
		return nil, store.ErrInvalidUnlockCode
	}
	if latest.Attempts >= latest.MaxAttempts {
		latest.Used = true
		return nil, store.ErrUnlockCodeAttemptsExceeded
	}
	expected := strings.TrimSpace(expectedFeature)
	if expected != "" && !strings.EqualFold(strings.TrimSpace(latest.FeatureName), expected) {
		latest.Attempts++
		if latest.Attempts >= latest.MaxAttempts {
			latest.Used = true
		}
		return nil, store.ErrUnlockCodeFeatureMismatch
	}
	if subtle.ConstantTimeCompare([]byte(latest.CodeHash), []byte(codeHash)) != 1 {
		latest.Attempts++
		if latest.Attempts >= latest.MaxAttempts {
			latest.Used = true
			return nil, store.ErrUnlockCodeAttemptsExceeded
		}
		return nil, store.ErrInvalidUnlockCode
	}
	latest.Used = true
	return latest, nil
}

func (m *MockAccountabilityStore) UpsertTimeDelay(userID uint, targetKey, targetTitle string, unlockAt time.Time) (*store.TimeDelayUnlock, error) {
	rec := &store.TimeDelayUnlock{
		ID:          userID,
		UserID:      userID,
		TargetKey:   targetKey,
		TargetTitle: targetTitle,
		UnlockAt:    unlockAt,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
	}
	m.timeDelays[userID] = rec
	return rec, nil
}

func (m *MockAccountabilityStore) GetTimeDelay(userID uint) (*store.TimeDelayUnlock, error) {
	rec, ok := m.timeDelays[userID]
	if !ok {
		return nil, store.ErrTimeDelayNotFound
	}
	return rec, nil
}

func (m *MockAccountabilityStore) ClearTimeDelay(userID uint) error {
	if _, ok := m.timeDelays[userID]; !ok {
		return store.ErrTimeDelayNotFound
	}
	delete(m.timeDelays, userID)
	return nil
}

func (m *MockAccountabilityStore) RecordSecurityEvent(userID uint, eventType, message string) (*store.SecurityAlert, error) {
	if !store.IsValidEventType(eventType) {
		return nil, store.ErrInvalidEventType
	}
	a := store.SecurityAlert{
		ID:        uint(len(m.events[userID]) + 1),
		UserID:    userID,
		EventType: eventType,
		Message:   message,
		CreatedAt: time.Now(),
	}
	m.events[userID] = append([]store.SecurityAlert{a}, m.events[userID]...)
	return &a, nil
}

func (m *MockAccountabilityStore) GetRecentEvents(userID uint, limit int) ([]store.SecurityAlert, error) {
	return m.events[userID], nil
}

func TestAccountabilityHandler_PartnerFlow(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockAccountabilityStore()
	handler := NewAccountabilityHandler(mockStore, nil, logger)

	user1 := &store.User{ID: 1, Email: "user1@pguard.local"}
	user2 := &store.User{ID: 2, Email: "partner@example.com"}

	t.Run("GET /api/v1/accountability/partner returns unlinked status when no partner", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/accountability/partner", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleGetPartner(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			IsLinked bool                         `json:"is_linked"`
			Partner  *store.AccountabilityPartner `json:"partner"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.False(t, resp.IsLinked)
		assert.Nil(t, resp.Partner)
	})

	t.Run("POST /api/v1/accountability/invite rejects invalid email format", func(t *testing.T) {
		for _, bad := range []string{`{"partner_email": "a@b"}`, `{"partner_email": "not-an-email"}`} {
			req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(bad))
			req = middleware.SetUser(req, user1)
			rr := httptest.NewRecorder()

			handler.HandleCreateInvite(rr, req)

			assert.Equal(t, http.StatusBadRequest, rr.Code)
		}
	})

	t.Run("POST /api/v1/accountability/invite prevents self-invite", func(t *testing.T) {
		body := `{"partner_email": "user1@pguard.local"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateInvite(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
		assert.Contains(t, rr.Body.String(), "You cannot invite yourself as an accountability partner")
	})

	t.Run("POST /api/v1/accountability/invite prevents self-invite with email alias", func(t *testing.T) {
		body := `{"partner_email": "user1+partner@pguard.local"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateInvite(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
		assert.Contains(t, rr.Body.String(), "You cannot invite yourself as an accountability partner")
	})

	t.Run("POST /api/v1/accountability/invite creates pending invite without leaking invite_code in JSON", func(t *testing.T) {
		body := `{"partner_email": "partner@example.com", "partner_name": "Best Friend"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateInvite(rr, req)

		assert.Equal(t, http.StatusCreated, rr.Code)
		assert.NotContains(t, rr.Body.String(), "invite_code")

		var resp struct {
			Partner store.AccountabilityPartner `json:"partner"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "partner@example.com", resp.Partner.PartnerEmail)
		assert.Equal(t, "pending", resp.Partner.Status)
		assert.Empty(t, resp.Partner.InviteCode)
		assert.Equal(t, "PG-TEST01", mockStore.partners[user1.ID].InviteCode)
	})

	t.Run("POST /api/v1/accountability/accept prevents accepting own invite code", func(t *testing.T) {
		body := `{"invite_code": "PG-TEST01"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/accept", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleAcceptInvite(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
		assert.Contains(t, rr.Body.String(), "You cannot accept your own invite code")
	})

	t.Run("POST /api/v1/accountability/accept links partner", func(t *testing.T) {
		body := `{"invite_code": "PG-TEST01"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/accept", strings.NewReader(body))
		req = middleware.SetUser(req, user2)
		rr := httptest.NewRecorder()

		handler.HandleAcceptInvite(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Message string                      `json:"message"`
			Partner store.AccountabilityPartner `json:"partner"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "accepted", resp.Partner.Status)
	})

	t.Run("DELETE /api/v1/accountability/partner disconnects partner", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodDelete, "/api/v1/accountability/partner", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleDisconnectPartner(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		p, err := mockStore.GetPartnerByUserID(user1.ID)
		assert.ErrorIs(t, err, store.ErrPartnerNotFound)
		assert.Nil(t, p)
	})
}

func TestAccountabilityHandler_SecurityEvents(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockAccountabilityStore()
	handler := NewAccountabilityHandler(mockStore, nil, logger)

	user := &store.User{ID: 1, Email: "user@pguard.local"}

	t.Run("POST /api/v1/accountability/event records valid event", func(t *testing.T) {
		body := `{"event_type": "sos_triggered", "message": "Emergency urge surfing opened"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/event", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleRecordEvent(rr, req)

		assert.Equal(t, http.StatusCreated, rr.Code)

		var resp struct {
			Alert store.SecurityAlert `json:"alert"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "sos_triggered", resp.Alert.EventType)
		assert.Equal(t, "Emergency urge surfing opened", resp.Alert.Message)
	})

	t.Run("POST /api/v1/accountability/event rejects invalid event type", func(t *testing.T) {
		body := `{"event_type": "unknown_event", "message": "test"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/event", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleRecordEvent(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("GET /api/v1/accountability/events returns recent alerts", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/accountability/events", nil)
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleGetEvents(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Events []store.SecurityAlert `json:"events"`
			Count  int                   `json:"count"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 1, resp.Count)
		assert.Equal(t, "sos_triggered", resp.Events[0].EventType)
	})
}

func TestAccountabilityHandler_WebAcceptAndUnlockVerification(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockAccountabilityStore()
	mailer := newCaptureMailer()
	handler := NewAccountabilityHandler(mockStore, mailer, logger)

	user := &store.User{ID: 1, Email: "inviter@pguard.local", DisplayName: "Inviter"}
	partner, err := mockStore.CreateInvite(user.ID, user.Email, "friend@example.com", "Friend")
	require.NoError(t, err)

	t.Run("GET accept-web renders confirmation form without mutating state", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/accountability/accept-web?code="+partner.InviteCode, nil)
		rr := httptest.NewRecorder()

		handler.HandleAcceptInviteWebPage(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)
		body := rr.Body.String()
		assert.Contains(t, body, `<form method="POST" action="/api/v1/accountability/accept-web">`)
		assert.Contains(t, body, "inviter@pguard.local")
		assert.Equal(t, store.PartnerStatusPending, mockStore.partners[user.ID].Status)
	})

	t.Run("POST request-unlock rejects pending partner", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/request-unlock", strings.NewReader(`{"feature_name":"TikTok"}`))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleRequestUnlockCode(rr, req)
		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("synchronous mailer failure returns 502 on invite", func(t *testing.T) {
		mailer.failNextErr = errors.New("resend unavailable")
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(`{"partner_email":"other@example.com"}`))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleCreateInvite(rr, req)
		assert.Equal(t, http.StatusBadGateway, rr.Code)
	})

	// Re-create pending invite and accept it via accept-web
	partner, err = mockStore.CreateInvite(user.ID, user.Email, "friend@example.com", "Friend")
	require.NoError(t, err)

	t.Run("POST accept-web accepts invite and references inviter", func(t *testing.T) {
		form := url.Values{}
		form.Set("code", partner.InviteCode)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/accept-web", strings.NewReader(form.Encode()))
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
		rr := httptest.NewRecorder()

		handler.HandleAcceptInviteWeb(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)
		assert.Contains(t, rr.Body.String(), "inviter@pguard.local")
		assert.NotContains(t, rr.Body.String(), "friend@example.com")
		assert.Equal(t, store.PartnerStatusAccepted, mockStore.partners[user.ID].Status)
	})

	t.Run("synchronous mailer failure returns 502 on request-unlock", func(t *testing.T) {
		mailer.failNextErr = errors.New("smtp error")
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/request-unlock", strings.NewReader(`{"feature_name":"TikTok"}`))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleRequestUnlockCode(rr, req)
		assert.Equal(t, http.StatusBadGateway, rr.Code)
	})

	t.Run("request-unlock binds feature_name and enforces single-use + attempt lockout", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/request-unlock", strings.NewReader(`{"feature_name":"TikTok"}`))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleRequestUnlockCode(rr, req)
		assert.Equal(t, http.StatusOK, rr.Code)

		var resp map[string]any
		require.NoError(t, json.NewDecoder(rr.Body).Decode(&resp))
		_, hasCode := resp["code"]
		assert.False(t, hasCode, "unlock code must never be returned in API response")

		mailer.mu.Lock()
		sentCode := mailer.unlockCodes[0]
		mailer.mu.Unlock()
		require.Len(t, sentCode, 6)

		// Wrong feature name fails with 403 and does not consume for TikTok
		mismatchBody := `{"code":"` + sentCode + `","feature_name":"Uninstall Protection"}`
		mismatchReq := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/verify-unlock", strings.NewReader(mismatchBody))
		mismatchReq = middleware.SetUser(mismatchReq, user)
		mismatchRR := httptest.NewRecorder()
		handler.HandleVerifyUnlockCode(mismatchRR, mismatchReq)
		assert.Equal(t, http.StatusForbidden, mismatchRR.Code)

		// Wrong code fails with 401
		wrongReq := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/verify-unlock", strings.NewReader(`{"code":"000000","feature_name":"TikTok"}`))
		wrongReq = middleware.SetUser(wrongReq, user)
		wrongRR := httptest.NewRecorder()
		handler.HandleVerifyUnlockCode(wrongRR, wrongReq)
		assert.Equal(t, http.StatusUnauthorized, wrongRR.Code)

		// Correct code + matching feature succeeds
		okBody := `{"code":"` + sentCode + `","feature_name":"TikTok"}`
		okReq := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/verify-unlock", strings.NewReader(okBody))
		okReq = middleware.SetUser(okReq, user)
		okRR := httptest.NewRecorder()
		handler.HandleVerifyUnlockCode(okRR, okReq)
		assert.Equal(t, http.StatusOK, okRR.Code)
		assert.Contains(t, okRR.Body.String(), `"verified": true`)

		// Replay is rejected
		replayReq := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/verify-unlock", strings.NewReader(okBody))
		replayReq = middleware.SetUser(replayReq, user)
		replayRR := httptest.NewRecorder()
		handler.HandleVerifyUnlockCode(replayRR, replayReq)
		assert.Equal(t, http.StatusUnauthorized, replayRR.Code)

		// Lockout after 5 failed attempts
		req2 := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/request-unlock", strings.NewReader(`{"feature_name":"Adult Content"}`))
		req2 = middleware.SetUser(req2, user)
		rr2 := httptest.NewRecorder()
		handler.HandleRequestUnlockCode(rr2, req2)
		require.Equal(t, http.StatusOK, rr2.Code)

		for i := 0; i < 4; i++ {
			r := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/verify-unlock", strings.NewReader(`{"code":"999999","feature_name":"Adult Content"}`))
			r = middleware.SetUser(r, user)
			w := httptest.NewRecorder()
			handler.HandleVerifyUnlockCode(w, r)
			assert.Equal(t, http.StatusUnauthorized, w.Code)
		}

		lockReq := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/verify-unlock", strings.NewReader(`{"code":"999999","feature_name":"Adult Content"}`))
		lockReq = middleware.SetUser(lockReq, user)
		lockRR := httptest.NewRecorder()
		handler.HandleVerifyUnlockCode(lockRR, lockReq)
		assert.Equal(t, http.StatusTooManyRequests, lockRR.Code)
	})
}

func TestAccountabilityHandler_TimeDelayLifecycle(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockAccountabilityStore()
	handler := NewAccountabilityHandler(mockStore, nil, logger)

	user := &store.User{ID: 7, Email: "delay@pguard.local"}

	// Initially empty
	getReq := httptest.NewRequest(http.MethodGet, "/api/v1/accountability/time-delay", nil)
	getReq = middleware.SetUser(getReq, user)
	getRR := httptest.NewRecorder()
	handler.HandleGetTimeDelay(getRR, getReq)
	assert.Equal(t, http.StatusOK, getRR.Code)
	assert.Contains(t, getRR.Body.String(), `"time_delay": null`)

	// Trying to start a shorter 60s timer is clamped up to 24 hours on server
	startReq := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/time-delay", strings.NewReader(`{"target_key":"TIKTOK","target_title":"TikTok","duration_seconds":60}`))
	startReq = middleware.SetUser(startReq, user)
	startRR := httptest.NewRecorder()
	handler.HandleStartTimeDelay(startRR, startReq)
	assert.Equal(t, http.StatusCreated, startRR.Code)

	var startResp struct {
		TimeDelay   store.TimeDelayUnlock `json:"time_delay"`
		RemainingMs int64                 `json:"remaining_ms"`
		IsReady     bool                  `json:"is_ready"`
	}
	require.NoError(t, json.NewDecoder(startRR.Body).Decode(&startResp))
	assert.Equal(t, "TIKTOK", startResp.TimeDelay.TargetKey)
	assert.False(t, startResp.IsReady)
	assert.GreaterOrEqual(t, startResp.RemainingMs, int64(24*60*60*1000-1000))

	// Clear active timer
	delReq := httptest.NewRequest(http.MethodDelete, "/api/v1/accountability/time-delay", nil)
	delReq = middleware.SetUser(delReq, user)
	delRR := httptest.NewRecorder()
	handler.HandleClearTimeDelay(delRR, delReq)
	assert.Equal(t, http.StatusOK, delRR.Code)

	getReq2 := httptest.NewRequest(http.MethodGet, "/api/v1/accountability/time-delay", nil)
	getReq2 = middleware.SetUser(getReq2, user)
	getRR2 := httptest.NewRecorder()
	handler.HandleGetTimeDelay(getRR2, getReq2)
	assert.Equal(t, http.StatusOK, getRR2.Code)
	assert.Contains(t, getRR2.Body.String(), `"time_delay": null`)
}
