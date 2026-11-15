package api

import (
	"encoding/json"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"Backend/internal/middleware"
	"Backend/internal/store"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type MockAccountabilityStore struct {
	partners map[uint]*store.AccountabilityPartner
	events   map[uint][]store.SecurityAlert
	nextID   uint
}

func NewMockAccountabilityStore() *MockAccountabilityStore {
	return &MockAccountabilityStore{
		partners: make(map[uint]*store.AccountabilityPartner),
		events:   make(map[uint][]store.SecurityAlert),
		nextID:   1,
	}
}

func (m *MockAccountabilityStore) CreateInvite(userID uint, userEmail, partnerEmail, partnerName string) (*store.AccountabilityPartner, error) {
	if partnerEmail == "" || !strings.Contains(partnerEmail, "@") {
		return nil, store.ErrInvalidEmail
	}
	if strings.EqualFold(partnerEmail, userEmail) {
		return nil, store.ErrSelfInvite
	}

	p := &store.AccountabilityPartner{
		ID:           m.nextID,
		UserID:       userID,
		PartnerEmail: partnerEmail,
		PartnerName:  partnerName,
		InviteCode:   "PG-TEST01",
		Status:       store.PartnerStatusPending,
		CreatedAt:    time.Now(),
		UpdatedAt:    time.Now(),
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
	handler := NewAccountabilityHandler(mockStore, logger)

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

	t.Run("POST /api/v1/accountability/invite prevents self-invite", func(t *testing.T) {
		body := `{"partner_email": "user1@pguard.local"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateInvite(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("POST /api/v1/accountability/invite creates pending invite", func(t *testing.T) {
		body := `{"partner_email": "partner@example.com", "partner_name": "Best Friend"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/accountability/invite", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateInvite(rr, req)

		assert.Equal(t, http.StatusCreated, rr.Code)

		var resp struct {
			Partner store.AccountabilityPartner `json:"partner"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "partner@example.com", resp.Partner.PartnerEmail)
		assert.Equal(t, "pending", resp.Partner.Status)
		assert.NotEmpty(t, resp.Partner.InviteCode)
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

		// Verify unlinked
		p, err := mockStore.GetPartnerByUserID(user1.ID)
		assert.ErrorIs(t, err, store.ErrPartnerNotFound)
		assert.Nil(t, p)
	})
}

func TestAccountabilityHandler_SecurityEvents(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockAccountabilityStore()
	handler := NewAccountabilityHandler(mockStore, logger)

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
