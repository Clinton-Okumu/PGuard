package api

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"Backend/internal/middleware"
	"Backend/internal/store"
	"Backend/internal/utils"

	"github.com/go-chi/chi/v5"
)

type HabitsHandler struct {
	habitsStore store.HabitsStore
	logger      *log.Logger
}

func NewHabitsHandler(habitsStore store.HabitsStore, logger *log.Logger) *HabitsHandler {
	return &HabitsHandler{
		habitsStore: habitsStore,
		logger:      logger,
	}
}

type milestoneInfo struct {
	Days       int    `json:"days"`
	Title      string `json:"title"`
	IsUnlocked bool   `json:"is_unlocked"`
}

var allMilestones = []struct {
	Days  int
	Title string
}{
	{1, "Seed"},
	{3, "Sprout"},
	{7, "Shield"},
	{14, "Bronze"},
	{30, "Silver"},
	{60, "Gold"},
	{90, "Diamond"},
	{180, "Master"},
	{270, "Transcendence"},
	{365, "Legend"},
}

func calculateMilestones(currentDays int) ([]milestoneInfo, *milestoneInfo) {
	var milestones []milestoneInfo
	var nextMilestone *milestoneInfo

	for _, m := range allMilestones {
		unlocked := currentDays >= m.Days
		info := milestoneInfo{
			Days:       m.Days,
			Title:      m.Title,
			IsUnlocked: unlocked,
		}
		milestones = append(milestones, info)

		if nextMilestone == nil && !unlocked {
			next := info
			nextMilestone = &next
		}
	}

	return milestones, nextMilestone
}

type resetStreakRequest struct {
	Trigger string `json:"trigger"`
	Notes   string `json:"notes"`
}

type createHabitRequest struct {
	Title string `json:"title"`
	Icon  string `json:"icon"`
}

func (h *HabitsHandler) HandleGetStreak(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	streak, err := h.habitsStore.GetOrCreateStreak(user.ID)
	if err != nil {
		h.logger.Printf("ERROR: get streak for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to load streak"})
		return
	}

	currentDays := streak.CalculateCurrentDays()
	milestones, next := calculateMilestones(currentDays)

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"streak": utils.Envelope{
			"current_days":         currentDays,
			"longest_streak_days":  streak.LongestStreakDays,
			"total_relapses":      streak.TotalRelapses,
			"current_streak_start": streak.CurrentStreakStart,
		},
		"milestones":     milestones,
		"next_milestone": next,
	})
}

func (h *HabitsHandler) HandleResetStreak(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req resetStreakRequest
	// Optional body
	_ = json.NewDecoder(r.Body).Decode(&req)

	streak, relapseLog, err := h.habitsStore.ResetStreak(user.ID, req.Trigger, req.Notes)
	if err != nil {
		h.logger.Printf("ERROR: reset streak for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to reset streak"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"message":     "Streak has been restarted",
		"streak":      streak,
		"relapse_log": relapseLog,
	})
}

func (h *HabitsHandler) HandleGetRelapseHistory(w http.ResponseWriter, r *http.Request) {
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

	logs, err := h.habitsStore.GetRelapseHistory(user.ID, limit)
	if err != nil {
		h.logger.Printf("ERROR: get relapse history for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to load history"})
		return
	}

	if logs == nil {
		logs = []store.RelapseLog{}
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"relapses": logs,
		"count":    len(logs),
	})
}

func (h *HabitsHandler) HandleGetHabits(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	dateStr := strings.TrimSpace(r.URL.Query().Get("date"))
	if dateStr == "" {
		dateStr = time.Now().UTC().Format("2006-01-02")
	} else if _, err := time.Parse("2006-01-02", dateStr); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid date format. Expected YYYY-MM-DD"})
		return
	}

	habits, err := h.habitsStore.GetHabitsWithStatus(user.ID, dateStr)
	if err != nil {
		h.logger.Printf("ERROR: get habits for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to load habits"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"date":   dateStr,
		"habits": habits,
		"count":  len(habits),
	})
}

func (h *HabitsHandler) HandleCreateHabit(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	var req createHabitRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid request body"})
		return
	}

	if strings.TrimSpace(req.Title) == "" {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Title is required"})
		return
	}

	habit, err := h.habitsStore.CreateHabit(user.ID, req.Title, req.Icon)
	if err != nil {
		h.logger.Printf("ERROR: create habit for user %d: %v", user.ID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to create habit"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusCreated, utils.Envelope{
		"message": "Habit created successfully",
		"habit":   habit,
	})
}

func (h *HabitsHandler) HandleToggleHabit(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	rawID := chi.URLParam(r, "id")
	habitID, err := strconv.ParseUint(rawID, 10, 32)
	if err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid habit ID"})
		return
	}

	dateStr := strings.TrimSpace(r.URL.Query().Get("date"))
	if dateStr == "" {
		dateStr = time.Now().UTC().Format("2006-01-02")
	} else if _, err := time.Parse("2006-01-02", dateStr); err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid date format. Expected YYYY-MM-DD"})
		return
	}

	completed, err := h.habitsStore.ToggleHabitCheckIn(user.ID, uint(habitID), dateStr)
	if err != nil {
		if errors.Is(err, store.ErrHabitNotFound) {
			_ = utils.WriteJSON(w, http.StatusNotFound, utils.Envelope{"error": "Habit not found"})
			return
		}
		h.logger.Printf("ERROR: toggle habit %d: %v", habitID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to toggle habit"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"habit_id":  habitID,
		"date":      dateStr,
		"completed": completed,
	})
}

func (h *HabitsHandler) HandleDeleteHabit(w http.ResponseWriter, r *http.Request) {
	user := middleware.GetUser(r)
	if user == nil || user.IsAnonymous() {
		_ = utils.WriteJSON(w, http.StatusUnauthorized, utils.Envelope{"error": "Unauthorized"})
		return
	}

	rawID := chi.URLParam(r, "id")
	habitID, err := strconv.ParseUint(rawID, 10, 32)
	if err != nil {
		_ = utils.WriteJSON(w, http.StatusBadRequest, utils.Envelope{"error": "Invalid habit ID"})
		return
	}

	err = h.habitsStore.DeleteHabit(user.ID, uint(habitID))
	if err != nil {
		if errors.Is(err, store.ErrHabitNotFound) {
			_ = utils.WriteJSON(w, http.StatusNotFound, utils.Envelope{"error": "Habit not found"})
			return
		}
		h.logger.Printf("ERROR: delete habit %d: %v", habitID, err)
		_ = utils.WriteJSON(w, http.StatusInternalServerError, utils.Envelope{"error": "Failed to delete habit"})
		return
	}

	_ = utils.WriteJSON(w, http.StatusOK, utils.Envelope{
		"status":  "deleted",
		"message": "Habit deleted successfully",
	})
}
