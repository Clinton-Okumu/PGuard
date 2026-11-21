package store

import (
	"errors"
	"math"
	"strings"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var (
	ErrHabitNotFound = errors.New("habit not found")
	ErrEmptyHabitTitle = errors.New("habit title cannot be empty")
)

type Streak struct {
	ID                 uint      `gorm:"primaryKey" json:"id"`
	UserID             uint      `gorm:"uniqueIndex;not null" json:"user_id"`
	CurrentStreakStart time.Time `gorm:"not null" json:"current_streak_start"`
	LongestStreakDays  int       `gorm:"default:0;not null" json:"longest_streak_days"`
	TotalRelapses      int       `gorm:"default:0;not null" json:"total_relapses"`
	CreatedAt          time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt          time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	User               User      `gorm:"foreignKey:UserID" json:"-"`
}

func (s *Streak) CalculateCurrentDays() int {
	diff := time.Since(s.CurrentStreakStart)
	if diff < 0 {
		return 0
	}
	return int(math.Floor(diff.Hours() / 24))
}

type RelapseLog struct {
	ID                 uint      `gorm:"primaryKey" json:"id"`
	UserID             uint      `gorm:"index;not null" json:"user_id"`
	StreakDaysAchieved int       `gorm:"not null" json:"streak_days_achieved"`
	Trigger            string    `gorm:"size:100" json:"trigger"`
	Notes              string    `gorm:"size:500" json:"notes"`
	RelapsedAt         time.Time `gorm:"not null" json:"relapsed_at"`
	CreatedAt          time.Time `gorm:"autoCreateTime" json:"created_at"`
	User               User      `gorm:"foreignKey:UserID" json:"-"`
}

type Habit struct {
	ID         uint      `gorm:"primaryKey" json:"id"`
	UserID     uint      `gorm:"index;not null" json:"user_id"`
	Title      string    `gorm:"size:150;not null" json:"title"`
	Icon       string    `gorm:"size:50;default:'book';not null" json:"icon"`
	IsArchived bool      `gorm:"default:false;not null" json:"is_archived"`
	CreatedAt  time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt  time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	User       User      `gorm:"foreignKey:UserID" json:"-"`
}

type HabitLog struct {
	ID        uint      `gorm:"primaryKey" json:"id"`
	HabitID   uint      `gorm:"index:idx_habit_date,unique;not null" json:"habit_id"`
	UserID    uint      `gorm:"index;not null" json:"user_id"`
	Date      string    `gorm:"size:10;index:idx_habit_date,unique;not null" json:"date"` // YYYY-MM-DD
	Completed bool      `gorm:"default:true;not null" json:"completed"`
	CreatedAt time.Time `gorm:"autoCreateTime" json:"created_at"`
	Habit     Habit     `gorm:"foreignKey:HabitID;constraint:OnDelete:CASCADE" json:"-"`
	User      User      `gorm:"foreignKey:UserID" json:"-"`
}

type HabitWithStatus struct {
	ID          uint      `json:"id"`
	Title       string    `json:"title"`
	Icon        string    `json:"icon"`
	Completed   bool      `json:"completed"`
	StreakDays  int       `json:"streak_days"`
	CreatedAt   time.Time `json:"created_at"`
}

type HabitsStore interface {
	GetOrCreateStreak(userID uint) (*Streak, error)
	ResetStreak(userID uint, trigger, notes string) (*Streak, *RelapseLog, error)
	GetRelapseHistory(userID uint, limit int) ([]RelapseLog, error)

	GetHabitsWithStatus(userID uint, dateStr string) ([]HabitWithStatus, error)
	CreateHabit(userID uint, title, icon string) (*Habit, error)
	ToggleHabitCheckIn(userID uint, habitID uint, dateStr string) (bool, error)
	DeleteHabit(userID uint, habitID uint) error
}

type PostgresHabitsStore struct {
	db *gorm.DB
}

func NewPostgresHabitsStore(db *gorm.DB) *PostgresHabitsStore {
	return &PostgresHabitsStore{db: db}
}

func (s *PostgresHabitsStore) GetOrCreateStreak(userID uint) (*Streak, error) {
	var streak Streak
	err := s.db.Where("user_id = ?", userID).First(&streak).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			newStreak := &Streak{
				UserID:             userID,
				CurrentStreakStart: time.Now(),
				LongestStreakDays:  0,
				TotalRelapses:      0,
			}
			if err := s.db.Create(newStreak).Error; err != nil {
				return nil, err
			}
			return newStreak, nil
		}
		return nil, err
	}
	return &streak, nil
}

func (s *PostgresHabitsStore) ResetStreak(userID uint, trigger, notes string) (*Streak, *RelapseLog, error) {
	var resultStreak *Streak
	var relapse *RelapseLog

	err := s.db.Transaction(func(tx *gorm.DB) error {
		var streak Streak
		err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
			Where("user_id = ?", userID).
			First(&streak).Error
		if err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				streak = Streak{
					UserID:             userID,
					CurrentStreakStart: time.Now(),
					LongestStreakDays:  0,
					TotalRelapses:      0,
				}
				if err := tx.Create(&streak).Error; err != nil {
					return err
				}
			} else {
				return err
			}
		}

		daysAchieved := streak.CalculateCurrentDays()
		if daysAchieved > streak.LongestStreakDays {
			streak.LongestStreakDays = daysAchieved
		}
		streak.TotalRelapses++
		streak.CurrentStreakStart = time.Now()

		if err := tx.Save(&streak).Error; err != nil {
			return err
		}

		relapse = &RelapseLog{
			UserID:             userID,
			StreakDaysAchieved: daysAchieved,
			Trigger:            strings.TrimSpace(trigger),
			Notes:              strings.TrimSpace(notes),
			RelapsedAt:         time.Now(),
		}
		if err := tx.Create(relapse).Error; err != nil {
			return err
		}

		resultStreak = &streak
		return nil
	})

	if err != nil {
		return nil, nil, err
	}

	return resultStreak, relapse, nil
}

func (s *PostgresHabitsStore) GetRelapseHistory(userID uint, limit int) ([]RelapseLog, error) {
	if limit <= 0 || limit > 100 {
		limit = 20
	}
	var logs []RelapseLog
	err := s.db.Where("user_id = ?", userID).
		Order("relapsed_at DESC").
		Limit(limit).
		Find(&logs).Error
	if err != nil {
		return nil, err
	}
	return logs, nil
}

func (s *PostgresHabitsStore) GetHabitsWithStatus(userID uint, dateStr string) ([]HabitWithStatus, error) {
	var habits []Habit
	err := s.db.Where("user_id = ? AND is_archived = false", userID).
		Order("created_at ASC").
		Find(&habits).Error
	if err != nil {
		return nil, err
	}

	if len(habits) == 0 {
		return []HabitWithStatus{}, nil
	}

	var habitIDs []uint
	for _, h := range habits {
		habitIDs = append(habitIDs, h.ID)
	}

	// Fetch completion logs up to target date to determine status and consecutive streak days
	var logs []HabitLog
	if err := s.db.Where("habit_id IN ? AND date <= ? AND completed = true", habitIDs, dateStr).
		Find(&logs).Error; err != nil {
		return nil, err
	}

	completedDates := make(map[uint]map[string]bool)
	for _, l := range logs {
		if completedDates[l.HabitID] == nil {
			completedDates[l.HabitID] = make(map[string]bool)
		}
		completedDates[l.HabitID][l.Date] = true
	}

	targetDate, parseErr := time.Parse("2006-01-02", dateStr)

	result := make([]HabitWithStatus, len(habits))
	for i, h := range habits {
		dates := completedDates[h.ID]
		completedToday := dates[dateStr]

		streakDays := 0
		if parseErr == nil && len(dates) > 0 {
			cursor := targetDate
			if !completedToday {
				cursor = targetDate.AddDate(0, 0, -1)
			}
			for dates[cursor.Format("2006-01-02")] {
				streakDays++
				cursor = cursor.AddDate(0, 0, -1)
			}
		}

		result[i] = HabitWithStatus{
			ID:         h.ID,
			Title:      h.Title,
			Icon:       h.Icon,
			Completed:  completedToday,
			StreakDays: streakDays,
			CreatedAt:  h.CreatedAt,
		}
	}

	return result, nil
}

func (s *PostgresHabitsStore) CreateHabit(userID uint, title, icon string) (*Habit, error) {
	t := strings.TrimSpace(title)
	if t == "" {
		return nil, ErrEmptyHabitTitle
	}

	ic := strings.ToLower(strings.TrimSpace(icon))
	if ic == "" {
		ic = "book"
	}

	habit := &Habit{
		UserID:     userID,
		Title:      t,
		Icon:       ic,
		IsArchived: false,
	}

	if err := s.db.Create(habit).Error; err != nil {
		return nil, err
	}
	return habit, nil
}

func (s *PostgresHabitsStore) ToggleHabitCheckIn(userID uint, habitID uint, dateStr string) (bool, error) {
	// Verify habit belongs to user
	var habit Habit
	if err := s.db.Where("id = ? AND user_id = ?", habitID, userID).First(&habit).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return false, ErrHabitNotFound
		}
		return false, err
	}

	var log HabitLog
	err := s.db.Where("habit_id = ? AND date = ?", habitID, dateStr).First(&log).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			// Create log as completed
			newLog := &HabitLog{
				HabitID:   habitID,
				UserID:    userID,
				Date:      dateStr,
				Completed: true,
			}
			if err := s.db.Create(newLog).Error; err != nil {
				return false, err
			}
			return true, nil
		}
		return false, err
	}

	// Toggle existing
	log.Completed = !log.Completed
	if err := s.db.Save(&log).Error; err != nil {
		return false, err
	}
	return log.Completed, nil
}

func (s *PostgresHabitsStore) DeleteHabit(userID uint, habitID uint) error {
	return s.db.Transaction(func(tx *gorm.DB) error {
		result := tx.Where("id = ? AND user_id = ?", habitID, userID).Delete(&Habit{})
		if result.Error != nil {
			return result.Error
		}
		if result.RowsAffected == 0 {
			return ErrHabitNotFound
		}
		if err := tx.Where("habit_id = ?", habitID).Delete(&HabitLog{}).Error; err != nil {
			return err
		}
		return nil
	})
}
