package store

import (
	"errors"
	"time"

	"gorm.io/gorm"
)

type RefreshToken struct {
	ID         uint      `gorm:"primaryKey" json:"id"`
	UserID     uint      `gorm:"not null;index" json:"user_id"`
	TokenHash  string    `gorm:"uniqueIndex;not null" json:"-"`
	DeviceInfo string    `json:"device_info"`
	ExpiresAt  time.Time `gorm:"not null" json:"expires_at"`
	Revoked    bool      `gorm:"default:false;not null" json:"revoked"`
	CreatedAt  time.Time `gorm:"autoCreateTime" json:"created_at"`
	User       User      `gorm:"foreignKey:UserID" json:"-"`
}

type TokenStore interface {
	CreateRefreshToken(userID uint, tokenHash string, deviceInfo string, expiresAt time.Time) (*RefreshToken, error)
	GetValidRefreshToken(tokenHash string) (*RefreshToken, error)
	RevokeRefreshToken(tokenHash string) error
	RevokeAllUserTokens(userID uint) error
}

type PostgresTokenStore struct {
	db *gorm.DB
}

func NewPostgresTokenStore(db *gorm.DB) *PostgresTokenStore {
	return &PostgresTokenStore{db: db}
}

func (s *PostgresTokenStore) CreateRefreshToken(userID uint, tokenHash string, deviceInfo string, expiresAt time.Time) (*RefreshToken, error) {
	token := &RefreshToken{
		UserID:     userID,
		TokenHash:  tokenHash,
		DeviceInfo: deviceInfo,
		ExpiresAt:  expiresAt,
		Revoked:    false,
	}

	if err := s.db.Create(token).Error; err != nil {
		return nil, err
	}
	return token, nil
}

func (s *PostgresTokenStore) GetValidRefreshToken(tokenHash string) (*RefreshToken, error) {
	var token RefreshToken
	err := s.db.Preload("User").
		Where("token_hash = ? AND revoked = false AND expires_at > ?", tokenHash, time.Now()).
		First(&token).Error
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return nil, nil
		}
		return nil, err
	}
	return &token, nil
}

func (s *PostgresTokenStore) RevokeRefreshToken(tokenHash string) error {
	return s.db.Model(&RefreshToken{}).Where("token_hash = ?", tokenHash).Update("revoked", true).Error
}

func (s *PostgresTokenStore) RevokeAllUserTokens(userID uint) error {
	return s.db.Model(&RefreshToken{}).Where("user_id = ?", userID).Update("revoked", true).Error
}
