package api

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"Backend/internal/middleware"
	"Backend/internal/store"

	"github.com/go-chi/chi/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type MockHabitsStore struct {
	streaks     map[uint]*store.Streak
	relapses    map[uint][]store.RelapseLog
	habits      map[uint]*store.Habit
	habitLogs   map[string]bool // key: "habitID:date"
	nextHabitID uint
}

func NewMockHabitsStore() *MockHabitsStore {
	return &MockHabitsStore{
		streaks:     make(map[uint]*store.Streak),
		relapses:    make(map[uint][]store.RelapseLog),
		habits:      make(map[uint]*store.Habit),
		habitLogs:   make(map[string]bool),
		nextHabitID: 1,
	}
}

func (m *MockHabitsStore) GetOrCreateStreak(userID uint) (*store.Streak, error) {
	if s, ok := m.streaks[userID]; ok {
		return s, nil
	}
	s := &store.Streak{
		ID:                 userID,
		UserID:             userID,
		CurrentStreakStart: time.Now().Add(-72 * time.Hour), // 3 days ago for test
		LongestStreakDays:  5,
		TotalRelapses:      1,
	}
	m.streaks[userID] = s
	return s, nil
}

func (m *MockHabitsStore) ResetStreak(userID uint, trigger, notes string) (*store.Streak, *store.RelapseLog, error) {
	s, _ := m.GetOrCreateStreak(userID)
	days := s.CalculateCurrentDays()
	if days > s.LongestStreakDays {
		s.LongestStreakDays = days
	}
	s.TotalRelapses++
	s.CurrentStreakStart = time.Now()

	relapse := store.RelapseLog{
		ID:                 uint(len(m.relapses[userID]) + 1),
		UserID:             userID,
		StreakDaysAchieved: days,
		Trigger:            trigger,
		Notes:              notes,
		RelapsedAt:         time.Now(),
	}
	m.relapses[userID] = append([]store.RelapseLog{relapse}, m.relapses[userID]...)
	return s, &relapse, nil
}

func (m *MockHabitsStore) GetRelapseHistory(userID uint, limit int) ([]store.RelapseLog, error) {
	return m.relapses[userID], nil
}

func (m *MockHabitsStore) GetHabitsWithStatus(userID uint, dateStr string) ([]store.HabitWithStatus, error) {
	var result []store.HabitWithStatus
	for _, h := range m.habits {
		if h.UserID == userID && !h.IsArchived {
			key := fmt.Sprintf("%d:%s", h.ID, dateStr)
			result = append(result, store.HabitWithStatus{
				ID:        h.ID,
				Title:     h.Title,
				Icon:      h.Icon,
				Completed: m.habitLogs[key],
				CreatedAt: h.CreatedAt,
			})
		}
	}
	return result, nil
}

func (m *MockHabitsStore) CreateHabit(userID uint, title, icon string) (*store.Habit, error) {
	if strings.TrimSpace(title) == "" {
		return nil, store.ErrEmptyHabitTitle
	}
	if icon == "" {
		icon = "book"
	}
	h := &store.Habit{
		ID:        m.nextHabitID,
		UserID:    userID,
		Title:     title,
		Icon:      icon,
		CreatedAt: time.Now(),
	}
	m.habits[h.ID] = h
	m.nextHabitID++
	return h, nil
}

func (m *MockHabitsStore) ToggleHabitCheckIn(userID uint, habitID uint, dateStr string) (bool, error) {
	h, ok := m.habits[habitID]
	if !ok || h.UserID != userID {
		return false, store.ErrHabitNotFound
	}
	key := fmt.Sprintf("%d:%s", habitID, dateStr)
	curr := m.habitLogs[key]
	m.habitLogs[key] = !curr
	return !curr, nil
}

func (m *MockHabitsStore) DeleteHabit(userID uint, habitID uint) error {
	h, ok := m.habits[habitID]
	if !ok || h.UserID != userID {
		return store.ErrHabitNotFound
	}
	delete(m.habits, habitID)
	return nil
}

func TestHabitsHandler_StreakEndpoints(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockHabitsStore()
	handler := NewHabitsHandler(mockStore, logger)

	user := &store.User{ID: 1, Email: "user@pguard.local"}

	t.Run("GET /api/v1/streak returns streak and milestone calculations", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/streak", nil)
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleGetStreak(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Streak struct {
				CurrentDays       int `json:"current_days"`
				LongestStreakDays int `json:"longest_streak_days"`
			} `json:"streak"`
			Milestones []milestoneInfo `json:"milestones"`
			Next       *milestoneInfo  `json:"next_milestone"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 3, resp.Streak.CurrentDays)
		assert.Equal(t, 5, resp.Streak.LongestStreakDays)
		assert.NotEmpty(t, resp.Milestones)
		// 3 days: Seed(1) and Sprout(3) should be unlocked
		assert.True(t, resp.Milestones[0].IsUnlocked)
		assert.True(t, resp.Milestones[1].IsUnlocked)
		// Next milestone should be Shield (7 days)
		require.NotNil(t, resp.Next)
		assert.Equal(t, 7, resp.Next.Days)
	})

	t.Run("POST /api/v1/streak/reset restarts streak and creates log", func(t *testing.T) {
		body := `{"trigger": "Boredom", "notes": "Late night scrolling"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/streak/reset", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleResetStreak(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Message    string           `json:"message"`
			RelapseLog store.RelapseLog `json:"relapse_log"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "Streak has been restarted", resp.Message)
		assert.Equal(t, "Boredom", resp.RelapseLog.Trigger)
		assert.Equal(t, 3, resp.RelapseLog.StreakDaysAchieved)
	})

	t.Run("GET /api/v1/streak/history returns logged relapses", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/streak/history", nil)
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()

		handler.HandleGetRelapseHistory(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Relapses []store.RelapseLog `json:"relapses"`
			Count    int                `json:"count"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 1, resp.Count)
		assert.Equal(t, "Boredom", resp.Relapses[0].Trigger)
	})

	t.Run("POST /api/v1/streak/reset rejects overlong trigger and notes", func(t *testing.T) {
		longTrigger := strings.Repeat("a", 101)
		body := fmt.Sprintf(`{"trigger": "%s", "notes": "ok"}`, longTrigger)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/streak/reset", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr := httptest.NewRecorder()
		handler.HandleResetStreak(rr, req)
		assert.Equal(t, http.StatusBadRequest, rr.Code)

		longNotes := strings.Repeat("b", 501)
		body = fmt.Sprintf(`{"trigger": "Stress", "notes": "%s"}`, longNotes)
		req = httptest.NewRequest(http.MethodPost, "/api/v1/streak/reset", strings.NewReader(body))
		req = middleware.SetUser(req, user)
		rr = httptest.NewRecorder()
		handler.HandleResetStreak(rr, req)
		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})
}

func TestHabitsHandler_HabitEndpoints(t *testing.T) {
	logger := log.New(io.Discard, "", 0)
	mockStore := NewMockHabitsStore()
	handler := NewHabitsHandler(mockStore, logger)

	user1 := &store.User{ID: 1, Email: "user1@pguard.local"}
	user2 := &store.User{ID: 2, Email: "user2@pguard.local"}

	var createdHabitID uint

	t.Run("POST /api/v1/habits creates a new habit", func(t *testing.T) {
		body := `{"title": "Read 20 mins", "icon": "book"}`
		req := httptest.NewRequest(http.MethodPost, "/api/v1/habits", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateHabit(rr, req)

		assert.Equal(t, http.StatusCreated, rr.Code)

		var resp struct {
			Habit store.Habit `json:"habit"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, "Read 20 mins", resp.Habit.Title)
		assert.Equal(t, "book", resp.Habit.Icon)
		createdHabitID = resp.Habit.ID
	})

	t.Run("POST /api/v1/habits rejects overlong title", func(t *testing.T) {
		longTitle := strings.Repeat("x", 101)
		body := fmt.Sprintf(`{"title": "%s", "icon": "book"}`, longTitle)
		req := httptest.NewRequest(http.MethodPost, "/api/v1/habits", strings.NewReader(body))
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleCreateHabit(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("GET /api/v1/habits returns habit with uncompleted status initially", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/habits?date=2026-11-15", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleGetHabits(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Date   string                  `json:"date"`
			Habits []store.HabitWithStatus `json:"habits"`
			Count  int                     `json:"count"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.Equal(t, 1, resp.Count)
		assert.False(t, resp.Habits[0].Completed)
	})

	t.Run("POST /api/v1/habits/{id}/toggle marks habit completed", func(t *testing.T) {
		url := fmt.Sprintf("/api/v1/habits/%d/toggle?date=2026-11-15", createdHabitID)
		req := httptest.NewRequest(http.MethodPost, url, nil)
		req = middleware.SetUser(req, user1)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", createdHabitID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleToggleHabit(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)

		var resp struct {
			Completed bool `json:"completed"`
		}
		err := json.NewDecoder(rr.Body).Decode(&resp)
		require.NoError(t, err)
		assert.True(t, resp.Completed)
	})

	t.Run("prevents user2 from toggling user1 habit", func(t *testing.T) {
		url := fmt.Sprintf("/api/v1/habits/%d/toggle?date=2026-11-15", createdHabitID)
		req := httptest.NewRequest(http.MethodPost, url, nil)
		req = middleware.SetUser(req, user2)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", createdHabitID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleToggleHabit(rr, req)

		assert.Equal(t, http.StatusNotFound, rr.Code)
	})

	t.Run("GET /api/v1/habits rejects invalid date format", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/api/v1/habits?date=15-11-2026", nil)
		req = middleware.SetUser(req, user1)
		rr := httptest.NewRecorder()

		handler.HandleGetHabits(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("POST /api/v1/habits/{id}/toggle rejects invalid date format", func(t *testing.T) {
		url := fmt.Sprintf("/api/v1/habits/%d/toggle?date=not-a-date", createdHabitID)
		req := httptest.NewRequest(http.MethodPost, url, nil)
		req = middleware.SetUser(req, user1)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", createdHabitID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleToggleHabit(rr, req)

		assert.Equal(t, http.StatusBadRequest, rr.Code)
	})

	t.Run("DELETE /api/v1/habits/{id} removes habit", func(t *testing.T) {
		url := fmt.Sprintf("/api/v1/habits/%d", createdHabitID)
		req := httptest.NewRequest(http.MethodDelete, url, nil)
		req = middleware.SetUser(req, user1)

		rctx := chi.NewRouteContext()
		rctx.URLParams.Add("id", fmt.Sprintf("%d", createdHabitID))
		req = req.WithContext(context.WithValue(req.Context(), chi.RouteCtxKey, rctx))

		rr := httptest.NewRecorder()
		handler.HandleDeleteHabit(rr, req)

		assert.Equal(t, http.StatusOK, rr.Code)
	})
}
