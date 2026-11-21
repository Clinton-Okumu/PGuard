package store

import (
	"errors"
	"time"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

var (
	ErrInvalidRefreshToken = errors.New("invalid or expired refresh token")
	ErrRefreshTokenReused  = errors.New("refresh token reuse detected")
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
	RotateRefreshToken(oldTokenHash, newTokenHash, deviceInfo string, newExpiresAt time.Time) (*RefreshToken, error)
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

func (s *PostgresTokenStore) RotateRefreshToken(oldTokenHash, newTokenHash, deviceInfo string, newExpiresAt time.Time) (*RefreshToken, error) {
	var newToken *RefreshToken
	var domainErr error

	txErr := s.db.Transaction(func(tx *gorm.DB) error {
		var existing RefreshToken
		err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
			Where("token_hash = ?", oldTokenHash).
			First(&existing).Error
		if err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				domainErr = ErrInvalidRefreshToken
				return nil
			}
			return err
		}

		if existing.Revoked {
			// Possible token theft/reuse: revoke all refresh tokens for this user and commit.
			if err := tx.Model(&RefreshToken{}).
				Where("user_id = ?", existing.UserID).
				Update("revoked", true).Error; err != nil {
				return err
			}
			domainErr = ErrRefreshTokenReused
			return nil
		}

		if existing.ExpiresAt.Before(time.Now()) {
			domainErr = ErrInvalidRefreshToken
			return nil
		}

		if err := tx.Model(&RefreshToken{}).
			Where("id = ?", existing.ID).
			Update("revoked", true).Error; err != nil {
			return err
		}

		resolvedDevice := deviceInfo
		if resolvedDevice == "" {
			resolvedDevice = existing.DeviceInfo
		}

		created := &RefreshToken{
			UserID:     existing.UserID,
			TokenHash:  newTokenHash,
			DeviceInfo: resolvedDevice,
			ExpiresAt:  newExpiresAt,
			Revoked:    false,
		}
		if err := tx.Create(created).Error; err != nil {
			return err
		}
		newToken = created
		return nil
	})

	if txErr != nil {
		return nil, txErr
	}
	if domainErr != nil {
		return nil, domainErr
	}
	return newToken, nil
}

func (s *PostgresTokenStore) RevokeRefreshToken(tokenHash string) error {
	return s.db.Model(&RefreshToken{}).Where("token_hash = ?", tokenHash).Update("revoked", true).Error
}

func (s *PostgresTokenStore) RevokeAllUserTokens(userID uint) error {
	return s.db.Model(&RefreshToken{}).Where("user_id = ?", userID).Update("revoked", true).Error
}
