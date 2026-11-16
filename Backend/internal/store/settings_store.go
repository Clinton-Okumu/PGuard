package store

import (
	"errors"
	"time"

	"gorm.io/gorm"
)

type UserSettings struct {
	ID                       uint      `gorm:"primaryKey" json:"id"`
	UserID                   uint      `gorm:"uniqueIndex;not null" json:"user_id"`
	BlockAdultContent        bool      `gorm:"default:true" json:"block_adult_content"`
	BlockShorts              bool      `gorm:"default:false" json:"block_shorts"`
	BlockWhatsAppChannels    bool      `gorm:"default:false" json:"block_whatsapp_channels"`
	BlockInstagramReels      bool      `gorm:"default:false" json:"block_instagram_reels"`
	BlockTikTok              bool      `gorm:"default:false" json:"block_tiktok"`
	UninstallProtection      bool      `gorm:"default:false" json:"uninstall_protection"`
	BlockUnsupportedBrowsers bool      `gorm:"default:false" json:"block_unsupported_browsers"`
	BlockNewlyInstalledApps  bool      `gorm:"default:false" json:"block_newly_installed_apps"`
	BlockedScreenMessage     string    `gorm:"default:'This page is blocked.'" json:"blocked_screen_message"`
	BlockedScreenCountdown   int       `gorm:"default:3" json:"blocked_screen_countdown"`
	RedirectURL              string    `gorm:"default:''" json:"redirect_url"`
	CreatedAt                time.Time `gorm:"autoCreateTime" json:"created_at"`
	UpdatedAt                time.Time `gorm:"autoUpdateTime" json:"updated_at"`
	User                     User      `gorm:"foreignKey:UserID" json:"-"`
}

func DefaultUserSettings(userID uint) *UserSettings {
	return &UserSettings{
		UserID:                   userID,
		BlockAdultContent:        true,
		BlockShorts:              false,
		BlockWhatsAppChannels:    false,
		BlockInstagramReels:      false,
		BlockTikTok:              false,
		UninstallProtection:      false,
		BlockUnsupportedBrowsers: false,
		BlockNewlyInstalledApps:  false,
		BlockedScreenMessage:     "This page is blocked.",
		BlockedScreenCountdown:   3,
		RedirectURL:              "",
	}
}

type SettingsStore interface {
	GetByUserID(userID uint) (*UserSettings, error)
	Upsert(settings *UserSettings) (*UserSettings, error)
}

type PostgresSettingsStore struct {
	db *gorm.DB
}

func NewPostgresSettingsStore(db *gorm.DB) *PostgresSettingsStore {
	return &PostgresSettingsStore{db: db}
}

func (s *PostgresSettingsStore) GetByUserID(userID uint) (*UserSettings, error) {
	var settings UserSettings
	err := s.db.Where("user_id = ?", userID).First(&settings).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			defaultSettings := DefaultUserSettings(userID)
			if err := s.db.Create(defaultSettings).Error; err != nil {
				return nil, err
			}
			return defaultSettings, nil
		}
		return nil, err
	}
	return &settings, nil
}

func (s *PostgresSettingsStore) Upsert(settings *UserSettings) (*UserSettings, error) {
	var existing UserSettings
	err := s.db.Where("user_id = ?", settings.UserID).First(&existing).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			if err := s.db.Create(settings).Error; err != nil {
				return nil, err
			}
			return settings, nil
		}
		return nil, err
	}

	existing.BlockAdultContent = settings.BlockAdultContent
	existing.BlockShorts = settings.BlockShorts
	existing.BlockWhatsAppChannels = settings.BlockWhatsAppChannels
	existing.BlockInstagramReels = settings.BlockInstagramReels
	existing.BlockTikTok = settings.BlockTikTok
	existing.UninstallProtection = settings.UninstallProtection
	existing.BlockUnsupportedBrowsers = settings.BlockUnsupportedBrowsers
	existing.BlockNewlyInstalledApps = settings.BlockNewlyInstalledApps
	existing.BlockedScreenMessage = settings.BlockedScreenMessage
	existing.BlockedScreenCountdown = settings.BlockedScreenCountdown
	existing.RedirectURL = settings.RedirectURL

	if err := s.db.Save(&existing).Error; err != nil {
		return nil, err
	}
	return &existing, nil
}
